package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.BunkerMod;
import com.shardsofvoid.bunker.block.BunkerPartBlock;
import com.shardsofvoid.bunker.block.BunkerPartBlockEntity;
import com.shardsofvoid.bunker.bunker.Bunker.State;
import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.energy.BunkerMachineBlockEntity;
import com.shardsofvoid.bunker.energy.EnergySystem;
import com.shardsofvoid.bunker.item.BunkerCoreItem;
import com.shardsofvoid.bunker.network.HighlightPayload;
import com.shardsofvoid.bunker.registry.ModAttachments;
import com.shardsofvoid.bunker.registry.ModBlocks;
import com.shardsofvoid.bunker.registry.ModComponents;
import com.shardsofvoid.bunker.registry.ModComponents.CoreOwner;
import com.shardsofvoid.bunker.registry.ModComponents.LinkedCore;
import com.shardsofvoid.bunker.registry.ModMisc;

import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Бункеры одного измерения: данные, индексы (якоря, наблюдаемая оболочка, чанки, зоны, энергоблоки),
 * очередь проверок герметичности, размещение и размеры областей. Всё выполняется на серверном потоке.
 */
public final class BunkerManager {
    private static final Map<ResourceKey<Level>, BunkerManager> MANAGERS = new HashMap<>();

    private final ServerLevel level;
    private final BunkerSavedData data;

    private final Long2ObjectOpenHashMap<Bunker> byCore = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Bunker> byRoom = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Bunker> byPending = new Long2ObjectOpenHashMap<>();
    /** Ожидаемая оболочка и проходы → бункер. */
    private final Long2ObjectOpenHashMap<Bunker> watched = new Long2ObjectOpenHashMap<>();
    /** Чанки ограничивающего объёма → бункеры. */
    private final Long2ObjectOpenHashMap<List<Bunker>> byChunk = new Long2ObjectOpenHashMap<>();
    /** Ячейки заливки → бункер. */
    private final Long2ObjectOpenHashMap<Bunker> zoneIndex = new Long2ObjectOpenHashMap<>();
    private final Map<UUID, LongOpenHashSet> watchedKeys = new HashMap<>();
    private final Map<UUID, LongOpenHashSet> chunkKeys = new HashMap<>();
    private final Object2LongLinkedOpenHashMap<UUID> queue = new Object2LongLinkedOpenHashMap<>();

    /** Энергоблоки мода в загруженных чанках (v3 3.1). */
    private final Long2ObjectOpenHashMap<BunkerMachineBlockEntity> machines = new Long2ObjectOpenHashMap<>();
    /** Кабельная сеть изменилась — пересчитать граф при следующем расчёте энергии. */
    public boolean cablesDirty = true;
    /** Кэш графа кабелей: бункер → энергоблоки, подключённые кабелем через энерговвод (v3 4.3). */
    public final Map<UUID, LongOpenHashSet> cableMembers = new HashMap<>();
    /** Бункеры, у которых сеть кабелей больше bunker.energy.maxCableNetwork. */
    public final java.util.Set<UUID> cableTooBig = new java.util.HashSet<>();

    private BunkerManager(ServerLevel level) {
        this.level = level;
        this.data = level.getDataStorage().computeIfAbsent(BunkerSavedData.FACTORY, BunkerSavedData.NAME);
        for (Bunker b : data.bunkers.values()) {
            index(b);
            schedule(b, 1);
        }
    }

    // ================================================================ доступ

    public static BunkerManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level.dimension(), k -> new BunkerManager(level));
    }

    @Nullable
    public static BunkerManager getIfLoaded(ServerLevel level) {
        return MANAGERS.get(level.dimension());
    }

    public static void unload(ServerLevel level) {
        BunkerManager m = MANAGERS.get(level.dimension());
        if (m != null && m.level == level) MANAGERS.remove(level.dimension());
    }

    public static void clearAll() {
        MANAGERS.clear();
    }

    public Collection<Bunker> all() {
        return data.bunkers.values();
    }

    public ServerLevel level() {
        return level;
    }

    public void markDirty() {
        data.setDirty();
    }

    @Nullable
    public Bunker byId(UUID id) {
        return data.bunkers.get(id);
    }

    @Nullable
    public Bunker byCore(BlockPos pos) {
        return byCore.get(pos.asLong());
    }

    @Nullable
    public Bunker byRoom(BlockPos pos) {
        return byRoom.get(pos.asLong());
    }

    @Nullable
    public Bunker byPending(BlockPos pos) {
        return byPending.get(pos.asLong());
    }

    @Nullable
    public Bunker watchedAt(BlockPos pos) {
        return watched.get(pos.asLong());
    }

    /** Бункер, в область (включая оболочку) которого попадает позиция. */
    @Nullable
    public Bunker bunkerAt(BlockPos pos) {
        List<Bunker> list = byChunk.get(ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())));
        if (list == null) return null;
        for (Bunker b : list) {
            for (Area a : b.areas()) if (a.contains(pos)) return b;
        }
        return null;
    }

    public boolean inZone(Bunker b, BlockPos pos) {
        return zoneIndex.get(pos.asLong()) == b;
    }

    /**
     * Установка обслуживает бункер: стоит в защитном объёме или встроена в оболочку одной из его областей
     * (станции можно ставить вместо стены — герметичность не ломается, блоки входят в тег shell_blocks).
     */
    public boolean servesZone(Bunker b, BlockPos pos) {
        if (inZone(b, pos)) return true;
        if (b.zone == null || !b.isActive()) return false;
        for (Area a : b.areas()) {
            if (a.isShell(pos.getX(), pos.getY(), pos.getZ())) return true;
        }
        return false;
    }

    public record Located(ServerLevel level, Bunker bunker) {}

    @Nullable
    public static Located find(MinecraftServer server, UUID bunkerId) {
        for (ServerLevel lvl : server.getAllLevels()) {
            Bunker b = get(lvl).byId(bunkerId);
            if (b != null) return new Located(lvl, b);
        }
        return null;
    }

    public static List<Located> bunkersOf(MinecraftServer server, UUID owner) {
        List<Located> out = new ArrayList<>();
        for (ServerLevel lvl : server.getAllLevels()) {
            for (Bunker b : get(lvl).all()) if (b.owner.equals(owner)) out.add(new Located(lvl, b));
        }
        out.sort(Comparator.<Located>comparingLong(l -> l.bunker().createdAt).thenComparing(l -> l.bunker().id));
        return out;
    }

    public static int ownershipRank(MinecraftServer server, Bunker bunker) {
        List<Located> list = bunkersOf(server, bunker.owner);
        for (int i = 0; i < list.size(); i++) if (list.get(i).bunker() == bunker) return i + 1;
        return list.size() + 1;
    }

    // ================================================================ зона

    /**
     * Сведения о ячейке защитного объёма. protectedHere = false — комната отключена или обесточена
     * (v3 3.5): игрок в ней набирает дозу, HUD показывает «Комната обесточена».
     */
    public record ZoneInfo(Bunker bunker, Area area, boolean protectedHere, double doseDecay, List<BunkerUpgrades.EffectDef> effectDefs) {
        public List<MobEffectInstance> effects(int duration) {
            if (effectDefs.isEmpty() || !protectedHere) return List.of();
            List<MobEffectInstance> out = new ArrayList<>(effectDefs.size());
            for (BunkerUpgrades.EffectDef d : effectDefs) out.add(d.create(duration));
            return out;
        }
    }

    @Nullable
    public static ZoneInfo zoneAt(ServerLevel level, BlockPos pos) {
        BunkerManager m = MANAGERS.get(level.dimension());
        if (m == null) return null;
        Bunker b = m.zoneIndex.get(pos.asLong());
        if (b == null || !b.isActive()) return null;
        Area area = b.areaAt(pos.getX(), pos.getY(), pos.getZ());
        if (area == null) area = b.coreArea;
        BunkerUpgrades.LevelDef def = area == b.coreArea ? BunkerUpgrades.core(b.level) : BunkerUpgrades.room(area.level);
        boolean on = b.isAreaOn(area);
        return new ZoneInfo(b, area, on, on ? def.doseDecay() : 0, def.effects());
    }

    // ================================================================ индексы

    private void index(Bunker b) {
        unindex(b);
        byCore.put(b.core.asLong(), b);
        for (BlockPos room : b.rooms.keySet()) byRoom.put(room.asLong(), b);
        for (BlockPos p : b.pendingRooms) byPending.put(p.asLong(), b);

        List<Area> areas = b.areas();
        LongOpenHashSet keys = new LongOpenHashSet(BunkerGeometry.expectedShell(areas));
        keys.addAll(BunkerGeometry.passages(areas));
        for (LongIterator it = keys.iterator(); it.hasNext(); ) watched.put(it.nextLong(), b);
        watchedKeys.put(b.id, keys);

        int[] bb = BunkerGeometry.bounds(areas, 1);
        LongOpenHashSet chunks = new LongOpenHashSet();
        for (int cx = SectionPos.blockToSectionCoord(bb[0]); cx <= SectionPos.blockToSectionCoord(bb[3]); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(bb[2]); cz <= SectionPos.blockToSectionCoord(bb[5]); cz++) {
                long key = ChunkPos.asLong(cx, cz);
                chunks.add(key);
                byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
            }
        }
        chunkKeys.put(b.id, chunks);
        cablesDirty = true;
    }

    private void unindex(Bunker b) {
        byCore.remove(b.core.asLong(), b);
        byRoom.long2ObjectEntrySet().removeIf(e -> e.getValue() == b);
        byPending.long2ObjectEntrySet().removeIf(e -> e.getValue() == b);
        LongOpenHashSet keys = watchedKeys.remove(b.id);
        if (keys != null) {
            for (LongIterator it = keys.iterator(); it.hasNext(); ) watched.remove(it.nextLong(), b);
        }
        LongOpenHashSet chunks = chunkKeys.remove(b.id);
        if (chunks != null) {
            for (LongIterator it = chunks.iterator(); it.hasNext(); ) {
                long key = it.nextLong();
                List<Bunker> list = byChunk.get(key);
                if (list != null) {
                    list.remove(b);
                    if (list.isEmpty()) byChunk.remove(key);
                }
            }
        }
    }

    private void setZone(Bunker b, @Nullable LongOpenHashSet zone) {
        if (b.zone != null) {
            for (LongIterator it = b.zone.iterator(); it.hasNext(); ) zoneIndex.remove(it.nextLong(), b);
        }
        b.zone = zone;
        if (zone != null) {
            for (LongIterator it = zone.iterator(); it.hasNext(); ) zoneIndex.put(it.nextLong(), b);
        }
    }

    /** Структура бункера изменилась: пересобрать индексы, сохранить, перепроверить. */
    public void structureChanged(Bunker b) {
        index(b);
        data.setDirty();
        validateNow(b);
    }

    // ================================================================ энергоблоки

    public void registerMachine(BunkerMachineBlockEntity be) {
        machines.put(be.getBlockPos().asLong(), be);
        cablesDirty = true;
    }

    public void unregisterMachine(BunkerMachineBlockEntity be) {
        machines.remove(be.getBlockPos().asLong(), be);
        cablesDirty = true;
    }

    public Collection<BunkerMachineBlockEntity> machines() {
        return machines.values();
    }

    @Nullable
    public BunkerMachineBlockEntity machineAt(BlockPos pos) {
        return machines.get(pos.asLong());
    }

    // ================================================================ очередь проверок

    public void schedule(Bunker b, int delay) {
        long now = level.getGameTime();
        long due = Math.max(now + Math.max(1, delay), b.lastValidated + 5);
        if (queue.containsKey(b.id)) {
            if (queue.getLong(b.id) > due) queue.put(b.id, due);
        } else {
            queue.put(b.id, due);
        }
    }

    public void schedule(Bunker b) {
        schedule(b, 1);
    }

    /** Тик измерения: проверки, страховочный таймер, двери, частицы, энергия и жизнеобеспечение. */
    public void tick() {
        long now = level.getGameTime();
        int interval = BunkerConfig.BUNKER_VALIDATION_INTERVAL.get();
        if (now % interval == 0) {
            for (Bunker b : data.bunkers.values()) schedule(b, 1 + (int) Math.floorMod(b.id.getLeastSignificantBits(), 20L));
        }
        if (now % 10 == 0) pollOpenables();
        if (now % 20 == 0) breachParticles();

        int budget = BunkerConfig.BUNKER_VALIDATIONS_PER_TICK.get();
        var it = queue.object2LongEntrySet().fastIterator();
        List<UUID> ready = new ArrayList<>(budget);
        while (it.hasNext() && ready.size() < budget) {
            var e = it.next();
            if (e.getLongValue() <= now) ready.add(e.getKey());
        }
        for (UUID id : ready) {
            queue.removeLong(id);
            Bunker b = data.bunkers.get(id);
            if (b != null) validateNow(b);
        }

        // v3 3.1: энергия раз в bunker.energy.interval, бункеры разнесены по тикам
        int energyInterval = BunkerConfig.ENERGY_INTERVAL.get();
        if (cablesDirty && now % 5 == 0) EnergySystem.rebuildCables(this);
        for (Bunker b : List.copyOf(data.bunkers.values())) {
            if (Math.floorMod(now + b.id.getMostSignificantBits(), energyInterval) == 0 && level.isLoaded(b.core)) {
                EnergySystem.tick(this, b, energyInterval);
            }
        }
    }

    /** Гермодвери: отметка времени открытия для шлюзового режима (v3 2.2). */
    private void updateDoorTimers(Bunker b) {
        long now = level.getGameTime();
        for (Long2BooleanMap.Entry e : b.openables.long2BooleanEntrySet()) {
            BlockPos pos = BlockPos.of(e.getLongKey());
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            boolean open = state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
            if (open && state.is(ModMisc.AIRLOCKS)) b.doorOpenSince.putIfAbsent(e.getLongKey(), now);
            else b.doorOpenSince.remove(e.getLongKey());
        }
    }

    /** Гермодверь переключилась (вызывает сам блок): засечь время сразу, чтобы шлюз не «мигал». */
    public void onAirlockToggled(BlockPos pos, boolean open) {
        Bunker b = watchedAt(pos);
        if (b == null) return;
        if (open) b.doorOpenSince.putIfAbsent(pos.asLong(), level.getGameTime());
        else b.doorOpenSince.remove(pos.asLong());
        schedule(b, 2);
    }

    private void pollOpenables() {
        long now = level.getGameTime();
        long grace = BunkerConfig.DOOR_GRACE_SECONDS.get() * 20L;
        for (Bunker b : data.bunkers.values()) {
            if (b.openables.isEmpty() || queue.containsKey(b.id)) continue;
            boolean changed = false;
            for (Long2BooleanMap.Entry e : b.openables.long2BooleanEntrySet()) {
                BlockPos pos = BlockPos.of(e.getLongKey());
                if (!level.isLoaded(pos)) continue;
                BlockState state = level.getBlockState(pos);
                boolean open = state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
                if (!state.hasProperty(BlockStateProperties.OPEN) || open != e.getBooleanValue()) {
                    changed = true;
                    break;
                }
            }
            updateDoorTimers(b);
            if (!changed && b.state == State.ACTIVE) {
                for (long since : b.doorOpenSince.values()) {
                    if (now - since >= grace) {
                        changed = true;
                        break;
                    }
                }
            }
            if (changed) schedule(b);
        }
    }

    private void breachParticles() {
        for (Bunker b : data.bunkers.values()) {
            if (!b.breached || !level.isLoaded(b.core)) continue;
            level.sendParticles(ParticleTypes.SMOKE, b.core.getX() + 0.5, b.core.getY() + 1.1, b.core.getZ() + 0.5,
                    6, 0.25, 0.15, 0.25, 0.01);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, b.core.getX() + 0.5, b.core.getY() + 1.0, b.core.getZ() + 0.5,
                    3, 0.3, 0.2, 0.3, 0.05);
        }
    }

    public void onBlockChanged(BlockPos pos) {
        Bunker b = watched.get(pos.asLong());
        if (b != null) schedule(b);
    }

    public void onChunkLoad(ChunkPos pos) {
        List<Bunker> list = byChunk.get(pos.toLong());
        if (list != null) for (Bunker b : list) schedule(b, 2);
        cablesDirty = true;
    }

    /** Немедленная проверка: условия → оболочка → заливка → смена состояния. */
    public void validateNow(Bunker b) {
        b.lastValidated = level.getGameTime();
        queue.removeLong(b.id);

        if (level.isLoaded(b.core) && !level.getBlockState(b.core).is(ModBlocks.BUNKER_CORE.get())) {
            BunkerMod.LOGGER.warn("Ядро бункера {} на {} отсутствует — бункер удалён", b.id, b.core);
            removeBunker(b);
            return;
        }
        boolean changed = b.rooms.keySet().removeIf(p -> level.isLoaded(p) && !level.getBlockState(p).is(ModBlocks.BUNKER_ROOM.get()));
        changed |= b.pendingRooms.removeIf(p -> level.isLoaded(p) && !level.getBlockState(p).is(ModBlocks.BUNKER_ROOM.get()));
        if (changed) {
            index(b);
            data.setDirty();
        }

        updateDoorTimers(b);
        List<ActivationConditions.Result> conditions = ActivationConditions.evaluate(level, b);
        b.lastConditions = conditions;
        BunkerValidator.Result r = BunkerValidator.validate(level, b);
        b.shellPresent = r.shellPresent();
        b.shellExpected = r.shellExpected();
        b.missing.clear();
        b.missing.addAll(r.missing());
        b.openables.clear();
        b.openables.putAll(r.openables());
        updateDoorTimers(b);
        if (r.outcome() == BunkerValidator.Outcome.UNLOADED) return;
        b.lastOutcome = r.outcome();
        b.sealed = r.outcome() == BunkerValidator.Outcome.SEALED;

        State old = b.state;
        State next;
        LongOpenHashSet zone = null;
        if (!ActivationConditions.allOk(conditions)) {
            next = State.NOT_READY;
        } else if (b.sealed && r.complete()) {
            next = State.ACTIVE;
            zone = r.zone();
        } else {
            next = State.BUILDING;
        }
        setZone(b, zone);
        b.state = next;

        boolean wasActive = old == State.ACTIVE || b.awaitingRestore;
        if (next == State.ACTIVE) {
            b.breached = false;
            b.doorBreach = false;
            if (old != State.ACTIVE && !b.awaitingRestore) notifyOwner(b, Component.translatable("message.bunker.activated")
                    .withStyle(ChatFormatting.GREEN), false);
        } else if (wasActive) {
            b.breached = true;
            long grace = BunkerConfig.DOOR_GRACE_SECONDS.get() * 20L;
            boolean door = false;
            for (long since : b.doorOpenSince.values()) if (level.getGameTime() - since >= grace) door = true;
            b.doorBreach = door;
            if (!b.awaitingRestore) {
                notifyOwner(b, door
                        ? Component.translatable("message.bunker.door_open", b.core.getX(), b.core.getY(), b.core.getZ()).withStyle(ChatFormatting.RED)
                        : Component.translatable("message.bunker.breached", b.core.getX(), b.core.getY(), b.core.getZ()).withStyle(ChatFormatting.RED), true);
            }
        }
        if (old == State.NOT_READY && next != State.NOT_READY) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(b.owner);
            if (owner != null && owner.level() == level && owner.distanceToSqr(b.core.getX(), b.core.getY(), b.core.getZ()) < 64 * 64) {
                sendHighlight(owner, b, false);
            }
        }
        b.awaitingRestore = false;
        if (old != next) data.setDirty();
    }

    public void notifyOwner(Bunker b, Component message, boolean alarm) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(b.owner);
        if (owner == null) return;
        owner.sendSystemMessage(message);
        if (alarm) owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), ModMisc.BUNKER_BREACH.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    // ================================================================ пересечения

    /** Чужой бункер, с областями которого пересекается area. */
    @Nullable
    public Bunker foreignOverlap(Bunker self, Area area) {
        for (Bunker other : data.bunkers.values()) {
            if (other == self) continue;
            for (Area a : other.areas()) if (a.intersects(area)) return other;
        }
        return null;
    }

    // ================================================================ ядро

    public void onCorePlaced(BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (byCore(pos) != null) return;
        CoreOwner owner = stack.get(ModComponents.CORE_OWNER.get());
        UUID ownerId;
        String ownerName;
        if (owner != null) {
            ownerId = owner.id();
            ownerName = owner.name();
        } else if (placer instanceof Player p) {
            ownerId = p.getUUID();
            ownerName = p.getGameProfile().getName();
        } else {
            return;
        }
        Direction facing = state.hasProperty(BunkerPartBlock.FACING) ? state.getValue(BunkerPartBlock.FACING) : Direction.UP;
        Bunker b = new Bunker(UUID.randomUUID(), ownerId, ownerName, pos, Area.inWall(pos, facing));
        b.createdAt = level.getGameTime();
        b.doorsLocked = BunkerConfig.DOOR_LOCKED_BY_DEFAULT.get();
        data.bunkers.put(b.id, b);
        data.setDirty();
        index(b);
        if (level.getBlockEntity(pos) instanceof BunkerPartBlockEntity be) be.bind(b.id, ownerId, ownerName);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerId);
        if (player != null) {
            player.setData(ModAttachments.BUNKER_ID, b.id.toString());
            player.sendSystemMessage(Component.translatable("message.bunker.core.placed").withStyle(ChatFormatting.YELLOW));
        }
        validateNow(b);
    }

    public void onCoreRemoved(BlockPos pos) {
        Bunker b = byCore(pos);
        if (b != null) removeBunker(b);
    }

    public void removeBunker(Bunker b) {
        setZone(b, null);
        unindex(b);
        queue.removeLong(b.id);
        List<BlockPos> rooms = new ArrayList<>(b.rooms.keySet());
        rooms.addAll(b.pendingRooms);
        for (BlockPos room : rooms) {
            if (level.isLoaded(room) && level.getBlockEntity(room) instanceof BunkerPartBlockEntity be) be.unbind();
        }
        data.bunkers.remove(b.id);
        data.setDirty();
        cablesDirty = true;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(b.owner);
        if (owner != null && b.id.toString().equals(owner.getData(ModAttachments.BUNKER_ID))) {
            owner.setData(ModAttachments.BUNKER_ID, "");
        }
    }

    public void returnCoreItem(Bunker b) {
        ItemStack stack = BunkerCoreItem.createFor(b.owner, b.ownerName);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(b.owner);
        if (owner != null) {
            if (!owner.getInventory().add(stack)) dropForOwner(owner.serverLevel(), owner.blockPosition(), stack, b.owner);
        } else {
            dropForOwner(level, b.core, stack, b.owner);
        }
    }

    public static void dropForOwner(ServerLevel level, BlockPos pos, ItemStack stack, UUID owner) {
        ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        entity.setTarget(owner);
        entity.setNoPickUpDelay();
        level.addFreshEntity(entity);
    }

    // ================================================================ размещение комнат (v3 1.4)

    /** Результат проверки установки: ошибка или список вариантов области. */
    public record Placement(@Nullable Component error, List<Area> variants) {}

    public static Placement checkRoomPlacement(ServerPlayer player, ServerLevel level, BlockPos pos, LinkedCore link) {
        if (!level.dimension().equals(link.dimension())) {
            return new Placement(Component.translatable("message.bunker.room.other_dimension").withStyle(ChatFormatting.RED), List.of());
        }
        BunkerManager m = get(level);
        Bunker b = m.byId(link.bunkerId());
        if (b == null || !b.core.equals(link.pos())) {
            return new Placement(Component.translatable("message.bunker.room.core_missing").withStyle(ChatFormatting.RED), List.of());
        }
        if (!b.owner.equals(player.getUUID()) && !player.hasPermissions(2)) {
            return new Placement(Component.translatable("message.bunker.room.not_owner").withStyle(ChatFormatting.RED), List.of());
        }
        int max = BunkerUpgrades.maxRooms(b.level);
        if (b.rooms.size() >= max) {
            return new Placement(Component.translatable("message.bunker.room.limit", b.rooms.size(), max).withStyle(ChatFormatting.RED), List.of());
        }
        List<Area> variants = m.roomVariants(b, pos);
        if (variants.isEmpty()) {
            return new Placement(Component.translatable("message.bunker.room.no_variant").withStyle(ChatFormatting.RED), List.of());
        }
        return new Placement(null, variants);
    }

    /** Варианты области 3×3×3 для блока в pos: примыкают к области бункера и ни с чем не пересекаются, кроме общей стены. */
    public List<Area> roomVariants(Bunker b, BlockPos pos) {
        List<Area> own = b.areas();
        List<Area> out = new ArrayList<>();
        for (Area cand : BunkerGeometry.baseCandidates(pos)) {
            boolean adjacent = false;
            for (Area a : own) {
                if (BunkerGeometry.adjacent(a, cand)) {
                    adjacent = true;
                    break;
                }
            }
            if (!adjacent || BunkerGeometry.overlapsOwn(cand, own) || foreignOverlap(b, cand) != null) continue;
            boolean anchorConflict = false;
            for (Area a : own) if (cand.isInterior(a.anchor.getX(), a.anchor.getY(), a.anchor.getZ())) anchorConflict = true;
            if (anchorConflict) continue;
            out.add(cand);
        }
        return out;
    }

    /** Блок комнаты встал: один вариант — комната подключается, несколько — «Размещение». */
    public void onRoomPlaced(BlockPos pos, @Nullable LivingEntity placer, ItemStack stack) {
        LinkedCore link = stack.get(ModComponents.LINKED_CORE.get());
        if (link == null || byRoom(pos) != null || byPending(pos) != null) return;
        Bunker b = byId(link.bunkerId());
        if (b == null) return;
        Integer lvl = stack.get(ModComponents.ROOM_LEVEL.get());
        int roomLevel = lvl == null ? 1 : lvl;
        List<Area> variants = roomVariants(b, pos);
        if (level.getBlockEntity(pos) instanceof BunkerPartBlockEntity be) {
            be.bind(b.id, b.owner, b.ownerName);
            be.setLevel(roomLevel);
        }
        if (variants.size() == 1) {
            attachRoom(b, variants.get(0), roomLevel, placer instanceof ServerPlayer sp ? sp : null);
        } else {
            b.pendingRooms.add(pos.immutable());
            if (level.getBlockEntity(pos) instanceof BunkerPartBlockEntity be) be.setPlacing(true);
            index(b);
            data.setDirty();
            if (placer instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.bunker.room.choose", variants.size()).withStyle(ChatFormatting.YELLOW), true);
                BunkerActions.openScreen(sp, pos);
            }
        }
    }

    private void attachRoom(Bunker b, Area area, int roomLevel, @Nullable ServerPlayer player) {
        area.level = roomLevel;
        b.rooms.put(area.anchor, area);
        b.pendingRooms.remove(area.anchor);
        if (level.getBlockEntity(area.anchor) instanceof BunkerPartBlockEntity be) be.setPlacing(false);
        BlockState state = level.getBlockState(area.anchor);
        if (state.hasProperty(BunkerPartBlock.LEVEL)) level.setBlock(area.anchor, state.setValue(BunkerPartBlock.LEVEL, roomLevel), 3);
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.bunker.room.placed", b.rooms.size(),
                    BunkerUpgrades.maxRooms(b.level)).withStyle(ChatFormatting.GREEN), true);
        }
        structureChanged(b);
    }

    /** Комната, построенная пультом проектирования: блок комнаты уже стоит в anchor. */
    public void attachBuiltRoom(Bunker b, Area area, int roomLevel, @Nullable ServerPlayer player) {
        attachRoom(b, area, roomLevel, player);
    }

    /** Подтвердить вариант размещения. */
    @Nullable
    public Component confirmPlacement(Bunker b, BlockPos pos, int variant, ServerPlayer player) {
        if (!b.pendingRooms.contains(pos)) return Component.translatable("message.bunker.room.not_pending");
        int max = BunkerUpgrades.maxRooms(b.level);
        if (b.rooms.size() >= max) return Component.translatable("message.bunker.room.limit", b.rooms.size(), max);
        List<Area> variants = roomVariants(b, pos);
        if (variant < 0 || variant >= variants.size()) return Component.translatable("message.bunker.room.no_variant");
        int lvl = level.getBlockEntity(pos) instanceof BunkerPartBlockEntity be ? be.level() : 1;
        attachRoom(b, variants.get(variant), lvl, player);
        return null;
    }

    public void onRoomRemoved(BlockPos pos) {
        Bunker b = byRoom(pos);
        if (b != null) {
            unlinkRoom(b, pos);
            return;
        }
        b = byPending(pos);
        if (b != null) {
            b.pendingRooms.remove(pos);
            index(b);
            data.setDirty();
        }
    }

    public void unlinkRoom(Bunker b, BlockPos pos) {
        if (b.rooms.remove(pos) == null) return;
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BunkerPartBlockEntity be) be.unbind();
        structureChanged(b);
    }

    // ================================================================ размер области (v3 1.3, 1.6)

    public static int expansionBudget(Bunker b, Area a) {
        int base = a == b.coreArea ? BunkerUpgrades.core(b.level).expansion() : BunkerUpgrades.room(a.level).expansion();
        return base + activeTier(b) * BunkerConfig.EXPANSION_TIER_BUDGET.get();
    }

    /** Бюджет новой комнаты заданного уровня (пульт проектирования). */
    public static int roomBudget(Bunker b, int roomLevel) {
        return BunkerUpgrades.room(roomLevel).expansion() + activeTier(b) * BunkerConfig.EXPANSION_TIER_BUDGET.get();
    }

    /** Улучшения расширений действуют, пока ядро на III уровне. */
    public static int activeTier(Bunker b) {
        return b.level >= 3 ? Math.min(b.expansionTier, maxTier()) : 0;
    }

    public static int maxTier() {
        return BunkerConfig.EXPANSION_TIER_COST.get().size();
    }

    public static int maxHorizontal(Bunker b) {
        return BunkerConfig.AREA_MAX_HORIZONTAL.get() + activeTier(b) * BunkerConfig.EXPANSION_TIER_HORIZONTAL.get();
    }

    public static int maxVertical(Bunker b) {
        return BunkerConfig.AREA_MAX_VERTICAL.get();
    }

    /**
     * Применить новые границы области. Проверки v3 1.6: бюджет и предел по оси, нет пересечений,
     * общие стены на месте, комнаты связаны с ядром, якорь в допустимой позиции; сдвиг ядра — только без комнат.
     * Возвращает причину отказа или null.
     */
    @Nullable
    public Component checkBounds(Bunker b, Area area, int[] nb) {
        Area test = area.copy();
        test.setBounds(nb);
        int maxH = maxHorizontal(b);
        int maxV = maxVertical(b);
        if (test.innerX() < Area.BASE_INNER || test.innerY() < Area.BASE_INNER || test.innerZ() < Area.BASE_INNER) {
            return Component.translatable("message.bunker.size.too_small");
        }
        if (test.innerX() > maxH || test.innerZ() > maxH || test.innerY() > maxV) {
            return Component.translatable("message.bunker.size.axis_limit", maxH, maxV);
        }
        int budget = expansionBudget(b, area);
        if (test.expansionUsed() > budget) {
            return Component.translatable("message.bunker.size.budget", test.expansionUsed(), budget);
        }
        if (!test.isValidAnchor(area.anchor)) return Component.translatable("message.bunker.size.anchor");
        boolean moved = !test.sameBounds(area) && test.sameSize(area);
        if (area == b.coreArea && moved && !b.rooms.isEmpty()) return Component.translatable("message.bunker.size.shift_rooms");

        List<Area> others = new ArrayList<>(b.areas());
        others.remove(area);
        for (Direction d : Direction.values()) {
            if (test.wall(d) != area.wall(d) && BunkerGeometry.isSharedWall(area, d, others)) {
                return Component.translatable("message.bunker.size.shared_wall");
            }
        }
        if (BunkerGeometry.overlapsOwn(test, others)) return Component.translatable("message.bunker.size.overlap");
        for (Area o : others) {
            if (test.isInterior(o.anchor.getX(), o.anchor.getY(), o.anchor.getZ())) return Component.translatable("message.bunker.size.overlap");
        }
        if (foreignOverlap(b, test) != null) return Component.translatable("message.bunker.size.foreign");
        List<Area> all = new ArrayList<>();
        for (Area a : b.areas()) all.add(a == area ? test : a);
        if (!BunkerGeometry.connected(all)) return Component.translatable("message.bunker.size.disconnected");
        return null;
    }

    @Nullable
    public Component applyBounds(Bunker b, Area area, int[] nb) {
        Component error = checkBounds(b, area, nb);
        if (error != null) return error;
        area.setBounds(nb);
        structureChanged(b);
        return null;
    }

    // ================================================================ комнаты: имя, порядок, включение

    public static final int MAX_NAME = 24;

    /** v3 6.3: до 24 символов, без кодов форматирования. */
    public static String sanitizeName(String raw) {
        if (raw == null) return "";
        String s = raw.replace("§", "").replaceAll("\\p{Cntrl}", "").trim();
        if (s.length() > MAX_NAME) s = s.substring(0, MAX_NAME);
        return s;
    }

    public void moveRoom(Bunker b, BlockPos pos, boolean up) {
        List<Map.Entry<BlockPos, Area>> list = new ArrayList<>(b.rooms.entrySet());
        int i = -1;
        for (int k = 0; k < list.size(); k++) if (list.get(k).getKey().equals(pos)) i = k;
        int j = up ? i - 1 : i + 1;
        if (i < 0 || j < 0 || j >= list.size()) return;
        Map.Entry<BlockPos, Area> tmp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, tmp);
        b.rooms.clear();
        for (Map.Entry<BlockPos, Area> e : list) b.rooms.put(e.getKey(), e.getValue());
        data.setDirty();
    }

    /** Игроки, чьи глаза в данной области бункера. */
    public int playersIn(Bunker b, Area a) {
        int n = 0;
        for (ServerPlayer p : level.players()) {
            BlockPos eye = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
            if (a.contains(eye) && inZone(b, eye)) n++;
        }
        return n;
    }

    // ================================================================ уровни

    public void setCoreLevel(Bunker b, int lvl) {
        b.level = Math.max(1, Math.min(3, lvl));
        updateLevelProperty(b.core, b.level);
        data.setDirty();
    }

    public void setRoomLevel(Bunker b, BlockPos room, int lvl) {
        Area a = b.rooms.get(room);
        if (a == null) return;
        a.level = Math.max(1, Math.min(3, lvl));
        updateLevelProperty(room, a.level);
        data.setDirty();
    }

    private void updateLevelProperty(BlockPos pos, int lvl) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BunkerPartBlock.LEVEL)) level.setBlock(pos, state.setValue(BunkerPartBlock.LEVEL, lvl), 3);
        if (level.getBlockEntity(pos) instanceof BunkerPartBlockEntity part) part.setLevel(lvl);
    }

    // ================================================================ подсветка

    public static List<BlockPos> boxes(List<Area> areas) {
        List<BlockPos> out = new ArrayList<>();
        for (Area a : areas) {
            if (out.size() + 2 > 64) break;
            out.add(new BlockPos(a.minX, a.minY, a.minZ));
            out.add(new BlockPos(a.maxX, a.maxY, a.maxZ));
        }
        return out;
    }

    public void sendHighlight(ServerPlayer player, Bunker b, boolean withMissing) {
        List<BlockPos> missing = withMissing ? List.copyOf(b.missing) : List.of();
        PacketDistributor.sendToPlayer(player, new HighlightPayload(boxes(b.areas()), missing, List.of(),
                BunkerConfig.BUNKER_HIGHLIGHT_SECONDS.get() * 20));
    }

    /** Подсветка вариантов/предпросмотра: рамки областей бункера плюс выделенные рамки кандидатов. */
    public void sendPreview(ServerPlayer player, Bunker b, List<Area> preview) {
        List<Area> areas = new ArrayList<>(preview);
        areas.addAll(b.areas());
        List<BlockPos> markers = new ArrayList<>();
        for (Area a : preview) {
            if (markers.size() >= HighlightPayload.MAX_MARKERS) break;
            markers.add(a.anchor);
        }
        PacketDistributor.sendToPlayer(player, new HighlightPayload(boxes(areas), List.of(), markers,
                BunkerConfig.BUNKER_HIGHLIGHT_SECONDS.get() * 20));
    }

    /** Отладка и команды: удалить запись о блоке, который больше не стоит в мире. */
    public void forgetPending(Bunker b) {
        for (Iterator<BlockPos> it = b.pendingRooms.iterator(); it.hasNext(); ) {
            BlockPos p = it.next();
            if (level.isLoaded(p) && !level.getBlockState(p).is(ModBlocks.BUNKER_ROOM.get())) it.remove();
        }
    }
}
