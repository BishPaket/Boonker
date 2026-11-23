package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.BunkerMod;
import com.shardsofvoid.bunker.block.BunkerPartBlockEntity;
import com.shardsofvoid.bunker.bunker.Bunker.State;
import com.shardsofvoid.bunker.bunker.BunkerGeometry.Cube;
import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.config.BunkerConfig.Adjacency;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Бункеры одного измерения: данные, индексы (позиции ядер, комнат, наблюдаемой оболочки, чанков, зон)
 * и очередь проверок герметичности. Всё выполняется на серверном потоке.
 */
public final class BunkerManager {
    private static final Map<ResourceKey<Level>, BunkerManager> MANAGERS = new HashMap<>();

    private final ServerLevel level;
    private final BunkerSavedData data;

    private final Long2ObjectOpenHashMap<Bunker> byCore = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Bunker> byRoom = new Long2ObjectOpenHashMap<>();
    /** Позиции ожидаемой оболочки → бункер. Реакция на изменение блока без перебора бункеров. */
    private final Long2ObjectOpenHashMap<Bunker> watched = new Long2ObjectOpenHashMap<>();
    /** Чанки ограничивающего объёма → бункеры (для перепроверки при загрузке чанков). */
    private final Long2ObjectOpenHashMap<List<Bunker>> byChunk = new Long2ObjectOpenHashMap<>();
    /** Ячейки защитных зон → бункер. Проверка игрока за O(1). */
    private final Long2ObjectOpenHashMap<Bunker> zoneIndex = new Long2ObjectOpenHashMap<>();
    private final Map<UUID, LongOpenHashSet> watchedKeys = new HashMap<>();
    private final Map<UUID, LongOpenHashSet> chunkKeys = new HashMap<>();
    /** Очередь проверок: бункер → тик, не раньше которого его можно проверять. */
    private final Object2LongLinkedOpenHashMap<UUID> queue = new Object2LongLinkedOpenHashMap<>();

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
    public Bunker watchedAt(BlockPos pos) {
        return watched.get(pos.asLong());
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

    /** Все бункеры игрока во всех измерениях, от старых к новым. */
    public static List<Located> bunkersOf(MinecraftServer server, UUID owner) {
        List<Located> out = new ArrayList<>();
        for (ServerLevel lvl : server.getAllLevels()) {
            for (Bunker b : get(lvl).all()) if (b.owner.equals(owner)) out.add(new Located(lvl, b));
        }
        out.sort(Comparator.<Located>comparingLong(l -> l.bunker().createdAt).thenComparing(l -> l.bunker().id));
        return out;
    }

    /** Порядковый номер бункера среди бункеров владельца (1 — самый старый). */
    public static int ownershipRank(MinecraftServer server, Bunker bunker) {
        List<Located> list = bunkersOf(server, bunker.owner);
        for (int i = 0; i < list.size(); i++) if (list.get(i).bunker() == bunker) return i + 1;
        return list.size() + 1;
    }

    // ================================================================ зона

    /** Сведения о зоне, в которой находится ячейка: бонусы уровня куба (bunker_upgrades). */
    public record ZoneInfo(Bunker bunker, double doseDecay, List<BunkerUpgrades.EffectDef> effectDefs) {
        public List<MobEffectInstance> effects(int duration) {
            if (effectDefs.isEmpty()) return List.of();
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
        BunkerUpgrades.LevelDef def = BunkerUpgrades.core(b.level);
        if (!BunkerGeometry.coreCube(b.core).contains(pos.getX(), pos.getY(), pos.getZ())) {
            for (Map.Entry<BlockPos, Integer> room : b.rooms.entrySet()) {
                if (BunkerGeometry.roomCube(room.getKey()).contains(pos.getX(), pos.getY(), pos.getZ())) {
                    def = BunkerUpgrades.room(room.getValue());
                    break;
                }
            }
        }
        return new ZoneInfo(b, def.doseDecay(), def.effects());
    }

    // ================================================================ индексы

    private void index(Bunker b) {
        unindex(b);
        byCore.put(b.core.asLong(), b);
        for (BlockPos room : b.rooms.keySet()) byRoom.put(room.asLong(), b);

        List<Cube> cubes = BunkerGeometry.cubes(b);
        LongOpenHashSet keys = new LongOpenHashSet(BunkerGeometry.expectedShell(cubes, BunkerConfig.ROOM_ADJACENCY.get()));
        // проходные ячейки тоже наблюдаем: закрытие прохода блоком тега меняет картину
        keys.addAll(BunkerGeometry.passages(cubes, BunkerConfig.ROOM_ADJACENCY.get()));
        for (LongIterator it = keys.iterator(); it.hasNext(); ) watched.put(it.nextLong(), b);
        watchedKeys.put(b.id, keys);

        int[] bb = BunkerGeometry.bounds(cubes, 1);
        LongOpenHashSet chunks = new LongOpenHashSet();
        for (int cx = SectionPos.blockToSectionCoord(bb[0]); cx <= SectionPos.blockToSectionCoord(bb[3]); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(bb[2]); cz <= SectionPos.blockToSectionCoord(bb[5]); cz++) {
                long key = ChunkPos.asLong(cx, cz);
                chunks.add(key);
                byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
            }
        }
        chunkKeys.put(b.id, chunks);
    }

    private void unindex(Bunker b) {
        byCore.remove(b.core.asLong(), b);
        byRoom.long2ObjectEntrySet().removeIf(e -> e.getValue() == b);
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

    // ================================================================ очередь проверок

    /** Поставить бункер в очередь. События склеиваются: один бункер — не чаще раза в 5 тиков. */
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

    /** Тик измерения: очередь проверок, страховочный таймер, двери, частицы при нарушении герметичности. */
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
    }

    private void pollOpenables() {
        for (Bunker b : data.bunkers.values()) {
            if (b.openables.isEmpty() || queue.containsKey(b.id)) continue;
            for (Long2BooleanMap.Entry e : b.openables.long2BooleanEntrySet()) {
                BlockPos pos = BlockPos.of(e.getLongKey());
                if (!level.isLoaded(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (!state.hasProperty(BlockStateProperties.OPEN) || state.getValue(BlockStateProperties.OPEN) != e.getBooleanValue()) {
                    schedule(b);
                    break;
                }
            }
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

    /** Изменение блока в мире (NeighborNotify и т. п.). */
    public void onBlockChanged(BlockPos pos) {
        Bunker b = watched.get(pos.asLong());
        if (b != null) schedule(b);
    }

    public void onChunkLoad(ChunkPos pos) {
        List<Bunker> list = byChunk.get(pos.toLong());
        if (list != null) for (Bunker b : list) schedule(b, 2);
    }

    /** Немедленная проверка: условия → оболочка → заливка → смена состояния. */
    public void validateNow(Bunker b) {
        b.lastValidated = level.getGameTime();
        queue.removeLong(b.id);

        // Самовосстановление: ядро или комнаты исчезли без события (правка мира, /setblock).
        if (level.isLoaded(b.core) && !level.getBlockState(b.core).is(ModBlocks.BUNKER_CORE.get())) {
            BunkerMod.LOGGER.warn("Ядро бункера {} на {} отсутствует — бункер удалён", b.id, b.core);
            removeBunker(b);
            return;
        }
        boolean roomsChanged = b.rooms.keySet().removeIf(p -> level.isLoaded(p) && !level.getBlockState(p).is(ModBlocks.BUNKER_ROOM.get()));
        if (roomsChanged) {
            index(b);
            data.setDirty();
        }

        List<ActivationConditions.Result> conditions = ActivationConditions.evaluate(level, b);
        b.lastConditions = conditions;
        BunkerValidator.Result r = BunkerValidator.validate(level, b);
        b.shellPresent = r.shellPresent();
        b.shellExpected = r.shellExpected();
        b.missing.clear();
        b.missing.addAll(r.missing());
        b.openables.clear();
        b.openables.putAll(r.openables());
        if (r.outcome() == BunkerValidator.Outcome.UNLOADED) {
            return; // проверка откладывается, состояние не меняется
        }
        b.lastOutcome = r.outcome();
        b.sealed = r.outcome() == BunkerValidator.Outcome.SEALED;

        State old = b.state;
        boolean conditionsOk = ActivationConditions.allOk(conditions);
        State next;
        LongOpenHashSet zone = null;
        if (!conditionsOk) {
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
            if (old != State.ACTIVE && !b.awaitingRestore) notifyOwner(b, Component.translatable("message.bunker.activated")
                    .withStyle(ChatFormatting.GREEN), false);
        } else if (wasActive) {
            b.breached = true;
            if (!b.awaitingRestore) notifyOwner(b, Component.translatable("message.bunker.breached",
                    b.core.getX(), b.core.getY(), b.core.getZ()).withStyle(ChatFormatting.RED), true);
        }
        if (old == State.NOT_READY && next != State.NOT_READY) {
            // условия выполнены — подсветить границы у владельца, если он рядом
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(b.owner);
            if (owner != null && owner.level() == level && owner.distanceToSqr(b.core.getX(), b.core.getY(), b.core.getZ()) < 64 * 64) {
                sendHighlight(owner, b, false);
            }
        }
        b.awaitingRestore = false;
        if (old != next) data.setDirty();
    }

    private void notifyOwner(Bunker b, Component message, boolean alarm) {
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(b.owner);
        if (owner == null) return;
        owner.sendSystemMessage(message);
        if (alarm) owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), ModMisc.BUNKER_BREACH.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    // ================================================================ ядро

    public void onCorePlaced(BlockPos pos, @Nullable LivingEntity placer, ItemStack stack) {
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
        Bunker b = new Bunker(UUID.randomUUID(), ownerId, ownerName, pos);
        b.createdAt = level.getGameTime();
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

    /** Удаление бункера: зона исчезает, комнаты отвязываются (блоки остаются, но не работают). */
    public void removeBunker(Bunker b) {
        setZone(b, null);
        unindex(b);
        queue.removeLong(b.id);
        for (BlockPos room : b.rooms.keySet()) {
            if (level.isLoaded(room) && level.getBlockEntity(room) instanceof BunkerPartBlockEntity be) be.unbind();
        }
        data.bunkers.remove(b.id);
        data.setDirty();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(b.owner);
        if (owner != null && b.id.toString().equals(owner.getData(ModAttachments.BUNKER_ID))) {
            owner.setData(ModAttachments.BUNKER_ID, "");
        }
    }

    /** Предмет ядра возвращается владельцу (раздел 4.6). */
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

    // ================================================================ комнаты

    /** Проверка установки комнаты (раздел 6.3). null — можно ставить, иначе причина отказа. */
    @Nullable
    public static Component checkRoomPlacement(ServerPlayer player, ServerLevel level, BlockPos pos, LinkedCore link) {
        if (!level.dimension().equals(link.dimension())) {
            return Component.translatable("message.bunker.room.other_dimension").withStyle(ChatFormatting.RED);
        }
        BunkerManager m = get(level);
        Bunker b = m.byId(link.bunkerId());
        if (b == null || !b.core.equals(link.pos())) {
            return Component.translatable("message.bunker.room.core_missing").withStyle(ChatFormatting.RED);
        }
        if (!b.owner.equals(player.getUUID()) && !player.hasPermissions(2)) {
            return Component.translatable("message.bunker.room.not_owner").withStyle(ChatFormatting.RED);
        }
        int max = BunkerUpgrades.maxRooms(b.level);
        if (b.rooms.size() >= max) {
            return Component.translatable("message.bunker.room.limit", b.rooms.size(), max).withStyle(ChatFormatting.RED);
        }
        return m.checkCube(b, pos);
    }

    @Nullable
    private Component checkCube(Bunker b, BlockPos pos) {
        Adjacency mode = BunkerConfig.ROOM_ADJACENCY.get();
        Cube cube = BunkerGeometry.roomCube(pos);
        List<Cube> own = BunkerGeometry.cubes(b);
        boolean adjacent = false;
        for (Cube c : own) {
            if (BunkerGeometry.adjacentDirection(c, cube, mode) != null) {
                adjacent = true;
                break;
            }
        }
        if (!adjacent) return Component.translatable("message.bunker.room.not_adjacent").withStyle(ChatFormatting.RED);
        for (Cube c : own) {
            int t = c.minIntersectionThickness(cube);
            if (t == 0) continue;
            if (mode == Adjacency.SHARED_WALL && t == 1) continue; // общая стена или ребро
            return Component.translatable("message.bunker.room.overlap").withStyle(ChatFormatting.RED);
        }
        for (Bunker other : data.bunkers.values()) {
            if (other == b) continue;
            for (Cube c : BunkerGeometry.cubes(other)) {
                if (c.intersects(cube)) {
                    return Component.translatable("message.bunker.room.overlap_foreign", other.ownerName).withStyle(ChatFormatting.RED);
                }
            }
        }
        return null;
    }

    /** Допустимые центры новых комнат у каждой стороны кубов бункера. */
    public List<BlockPos> allowedRoomCenters(Bunker b) {
        List<BlockPos> out = new ArrayList<>();
        if (b.rooms.size() >= BunkerUpgrades.maxRooms(b.level)) return out;
        for (BlockPos p : BunkerGeometry.candidateRoomCenters(BunkerGeometry.cubes(b), BunkerConfig.ROOM_ADJACENCY.get())) {
            if (checkCube(b, p) == null) out.add(p);
            if (out.size() >= HighlightPayload.MAX_MARKERS) break;
        }
        return out;
    }

    public static void sendAllowedRoomHighlight(ServerPlayer player, LinkedCore link) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(link.dimension())) return;
        BunkerManager m = get(level);
        Bunker b = m.byId(link.bunkerId());
        if (b == null) return;
        PacketDistributor.sendToPlayer(player, new HighlightPayload(boxes(b), List.of(), m.allowedRoomCenters(b),
                BunkerConfig.BUNKER_HIGHLIGHT_SECONDS.get() * 20));
    }

    public void onRoomPlaced(BlockPos pos, @Nullable LivingEntity placer, ItemStack stack) {
        LinkedCore link = stack.get(ModComponents.LINKED_CORE.get());
        if (link == null || byRoom(pos) != null) return;
        Bunker b = byId(link.bunkerId());
        if (b == null) return;
        Integer lvl = stack.get(ModComponents.ROOM_LEVEL.get());
        int roomLevel = lvl == null ? 1 : lvl;
        b.rooms.put(pos.immutable(), roomLevel);
        data.setDirty();
        index(b);
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(com.shardsofvoid.bunker.block.BunkerPartBlock.LEVEL)) {
            level.setBlock(pos, state.setValue(com.shardsofvoid.bunker.block.BunkerPartBlock.LEVEL, roomLevel), 3);
        }
        if (level.getBlockEntity(pos) instanceof BunkerPartBlockEntity be) be.bind(b.id, b.owner, b.ownerName);
        if (placer instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.bunker.room.placed", b.rooms.size(),
                    BunkerUpgrades.maxRooms(b.level)).withStyle(ChatFormatting.GREEN), true);
        }
        validateNow(b);
    }

    public void onRoomRemoved(BlockPos pos) {
        Bunker b = byRoom(pos);
        if (b == null) return;
        unlinkRoom(b, pos);
    }

    public void unlinkRoom(Bunker b, BlockPos pos) {
        if (b.rooms.remove(pos) == null) return;
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BunkerPartBlockEntity be) be.unbind();
        data.setDirty();
        index(b);
        validateNow(b);
    }

    // ================================================================ уровни

    public void setCoreLevel(Bunker b, int lvl) {
        b.level = Math.max(1, Math.min(3, lvl));
        updateLevelProperty(b.core, b.level);
        data.setDirty();
    }

    public void setRoomLevel(Bunker b, BlockPos room, int lvl) {
        if (!b.rooms.containsKey(room)) return;
        int v = Math.max(1, Math.min(3, lvl));
        b.rooms.put(room, v);
        updateLevelProperty(room, v);
        data.setDirty();
    }

    private void updateLevelProperty(BlockPos pos, int lvl) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(com.shardsofvoid.bunker.block.BunkerPartBlock.LEVEL)) {
            level.setBlock(pos, state.setValue(com.shardsofvoid.bunker.block.BunkerPartBlock.LEVEL, lvl), 3);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BunkerPartBlockEntity part) part.setLevel(lvl);
    }

    public void markDirty() {
        data.setDirty();
    }

    // ================================================================ подсветка

    public static List<BlockPos> boxes(Bunker b) {
        List<BlockPos> out = new ArrayList<>();
        for (Cube c : BunkerGeometry.cubes(b)) {
            out.add(new BlockPos(c.minX(), c.minY(), c.minZ()));
            out.add(new BlockPos(c.maxX(), c.maxY(), c.maxZ()));
            if (out.size() >= 64) break;
        }
        return out;
    }

    /** Подсветка у игрока: границы кубов; withMissing — плюс до 64 недостающих блоков и допустимые комнаты. */
    public void sendHighlight(ServerPlayer player, Bunker b, boolean withMissing) {
        List<BlockPos> missing = withMissing ? List.copyOf(b.missing) : List.of();
        List<BlockPos> allowed = withMissing && b.state == State.ACTIVE ? allowedRoomCenters(b) : List.of();
        PacketDistributor.sendToPlayer(player, new HighlightPayload(boxes(b), missing, allowed,
                BunkerConfig.BUNKER_HIGHLIGHT_SECONDS.get() * 20));
    }
}
