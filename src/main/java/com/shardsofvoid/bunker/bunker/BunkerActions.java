package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.bunker.Bunker.State;
import com.shardsofvoid.bunker.block.BunkerPartBlock;
import com.shardsofvoid.bunker.block.BunkerPartBlockEntity;
import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.energy.EnergySystem;
import com.shardsofvoid.bunker.network.BunkerActionPayload;
import com.shardsofvoid.bunker.network.BunkerScreenPayload;
import com.shardsofvoid.bunker.registry.ModBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Интерфейс ядра и блока комнаты (v3, раздел 6): вкладки, кнопки, черновик размера, размещение комнаты.
 * Клиент только показывает присланный CompoundTag и шлёт действия; всё перепроверяется на сервере.
 * Пока экран открыт, данные вкладки обновляются раз в секунду.
 */
public final class BunkerActions {
    public static final int TAB_OVERVIEW = 0;
    public static final int TAB_ROOMS = 1;
    public static final int TAB_ENERGY = 2;
    public static final int TAB_LIFE = 3;
    public static final int TAB_SIZE = 4;

    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;
    private static final int RATE_LIMIT = 10;

    /** Открытый экран игрока. draft — черновик границ во вкладке «Размер», null — без изменений. */
    private static final class View {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        int tab;
        int[] draft;
        int variant;
        @Nullable BlockPos confirmRoom;
        @Nullable Component message;
        boolean messageOk;

        View(ResourceKey<Level> dimension, BlockPos pos, int tab) {
            this.dimension = dimension;
            this.pos = pos.immutable();
            this.tab = tab;
        }
    }

    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private static final Map<UUID, long[]> RATE = new HashMap<>();

    private BunkerActions() {}

    /** Контекст блока: менеджер, бункер, комната ли это, область (null — блок в «Размещении»). */
    private record Target(BunkerManager manager, @Nullable Bunker bunker, boolean room, BlockPos pos, @Nullable Area area, boolean placing) {}

    @Nullable
    private static Target resolve(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        BunkerManager m = BunkerManager.get(level);
        if (state.is(ModBlocks.BUNKER_CORE.get())) {
            Bunker b = m.byCore(pos);
            return new Target(m, b, false, pos, b == null ? null : b.coreArea, false);
        }
        if (state.is(ModBlocks.BUNKER_ROOM.get())) {
            Bunker b = m.byRoom(pos);
            if (b != null) return new Target(m, b, true, pos, b.rooms.get(pos), false);
            Bunker pending = m.byPending(pos);
            return new Target(m, pending, true, pos, null, pending != null);
        }
        return null;
    }

    private static boolean isOp(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    private static boolean canEdit(ServerPlayer player, Bunker b) {
        return b.owner.equals(player.getUUID()) || isOp(player);
    }

    private static boolean inReach(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }

    /** v3 10.3: не больше 10 действий интерфейса в секунду на игрока. */
    private static boolean rateLimited(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long[] r = RATE.computeIfAbsent(player.getUUID(), k -> new long[] {now, 0});
        if (now - r[0] >= 20) {
            r[0] = now;
            r[1] = 0;
        }
        return ++r[1] > RATE_LIMIT;
    }

    public static void forget(UUID player) {
        VIEWS.remove(player);
        RATE.remove(player);
    }

    // ================================================================ открытие

    public static void openScreen(ServerPlayer player, BlockPos pos) {
        Target t = resolve(player.serverLevel(), pos);
        if (t == null) return;
        if (t.bunker() == null) {
            player.displayClientMessage(Component.translatable(t.room() ? "message.bunker.room.inactive" : "message.bunker.core.orphan")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Bunker b = t.bunker();
        if (!canEdit(player, b)) {
            player.displayClientMessage(Component.translatable("message.bunker.foreign", b.ownerName).withStyle(ChatFormatting.RED), true);
            return;
        }
        t.manager().validateNow(b); // v2 5.2: проверка при открытии интерфейса
        View v = new View(player.serverLevel().dimension(), pos, TAB_OVERVIEW);
        VIEWS.put(player.getUUID(), v);
        if (t.placing()) {
            List<Area> variants = t.manager().roomVariants(b, pos);
            if (!variants.isEmpty()) t.manager().sendPreview(player, b, List.of(variants.get(0)));
        } else if (b.state != State.NOT_READY) {
            t.manager().sendHighlight(player, b, false);
        }
        send(player, t, v, true);
    }

    /** Раз в секунду обновить открытые экраны игроков этого измерения. */
    public static void tickViews(ServerLevel level) {
        if (VIEWS.isEmpty() || level.getGameTime() % 20 != 0) return;
        for (Iterator<Map.Entry<UUID, View>> it = VIEWS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, View> e = it.next();
            View v = e.getValue();
            if (!v.dimension.equals(level.dimension())) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || player.serverLevel() != level || !inReach(player, v.pos)) {
                it.remove();
                continue;
            }
            Target t = resolve(level, v.pos);
            if (t == null || t.bunker() == null || !canEdit(player, t.bunker())) {
                it.remove();
                continue;
            }
            send(player, t, v, false);
        }
    }

    // ================================================================ действия

    public static void handle(ServerPlayer player, BunkerActionPayload payload) {
        CompoundTag args = payload.args();
        String action = args.getString("a");
        if (action.equals("close")) {
            VIEWS.remove(player.getUUID());
            return;
        }
        if (rateLimited(player)) return;
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        if (!inReach(player, pos)) return;
        Target t = resolve(level, pos);
        if (t == null || t.bunker() == null) return;
        Bunker b = t.bunker();
        if (!canEdit(player, b)) return;
        BunkerManager m = t.manager();

        View v = VIEWS.get(player.getUUID());
        if (v == null || !v.pos.equals(pos) || !v.dimension.equals(level.dimension())) {
            v = new View(level.dimension(), pos, TAB_OVERVIEW);
            VIEWS.put(player.getUUID(), v);
        }
        v.message = null;

        if (t.placing()) {
            handlePlacing(player, t, v, action, args);
            if (m.byId(b.id) != null) {
                Target now = resolve(level, pos);
                if (now != null && now.bunker() != null) send(player, now, v, false);
            }
            return;
        }
        Area area = t.area();
        if (area == null) return;

        switch (action) {
            case "tab" -> {
                int tab = args.getInt("tab");
                if (t.room() && tab != TAB_OVERVIEW && tab != TAB_SIZE) tab = TAB_OVERVIEW;
                v.tab = Math.max(0, Math.min(TAB_SIZE, tab));
                v.confirmRoom = null;
                if (v.tab == TAB_SIZE && v.draft != null) m.sendPreview(player, b, List.of(draftArea(area, v.draft)));
            }
            case "refresh" -> m.validateNow(b);
            case "bounds" -> {
                m.validateNow(b);
                m.sendHighlight(player, b, true);
            }
            case "upgrade" -> upgrade(player, t, v);
            case "lock" -> {
                if (!t.room()) {
                    b.doorsLocked = !b.doorsLocked;
                    m.markDirty();
                    v.message = Component.translatable(b.doorsLocked ? "message.bunker.doors.locked" : "message.bunker.doors.unlocked");
                    v.messageOk = true;
                }
            }
            case "rename" -> {
                Area target = t.room() ? area : areaByAnchor(b, NbtUtils.readBlockPos(args, "pos").orElse(null));
                if (target != null) {
                    target.name = BunkerManager.sanitizeName(args.getString("name"));
                    m.markDirty();
                }
            }
            case "toggle" -> toggle(player, t, v, args);
            case "move" -> {
                if (!t.room()) {
                    NbtUtils.readBlockPos(args, "pos").ifPresent(p -> m.moveRoom(b, p, args.getBoolean("up")));
                }
            }
            case "unlink" -> {
                if (!t.room()) {
                    NbtUtils.readBlockPos(args, "pos").ifPresent(p -> {
                        if (b.rooms.containsKey(p)) {
                            m.unlinkRoom(b, p);
                            player.displayClientMessage(Component.translatable("message.bunker.room.unlinked_done",
                                    p.getX(), p.getY(), p.getZ()).withStyle(ChatFormatting.YELLOW), true);
                        }
                    });
                }
            }
            case "wall" -> {
                int dir = args.getInt("dir");
                int delta = args.getInt("delta") >= 0 ? 1 : -1;
                if (dir < 0 || dir >= 6) return;
                Area d = draftArea(area, v.draft);
                d.moveWall(Direction.from3DDataValue(dir), delta);
                v.draft = d.bounds();
                preview(player, t, v);
            }
            case "shift" -> {
                if (t.room()) return;
                int dir = args.getInt("dir");
                if (dir < 0 || dir >= 6) return;
                Direction d = Direction.from3DDataValue(dir);
                Area a = draftArea(area, v.draft);
                a.translate(d.getStepX(), d.getStepY(), d.getStepZ());
                v.draft = a.bounds();
                preview(player, t, v);
            }
            case "preset" -> {
                if (t.room()) return;
                BlockState state = level.getBlockState(b.core);
                Direction facing = state.hasProperty(BunkerPartBlock.FACING) ? state.getValue(BunkerPartBlock.FACING) : Direction.UP;
                Area base = args.getString("mode").equals("center") ? Area.centered(b.core, b.core) : Area.inWall(b.core, facing);
                // пресет задаёт положение, размер расширения сохраняется
                Area d = draftArea(area, v.draft);
                int dx = d.innerX() - base.innerX(), dy = d.innerY() - base.innerY(), dz = d.innerZ() - base.innerZ();
                base.moveWall(Direction.EAST, dx);
                base.moveWall(Direction.UP, dy);
                base.moveWall(Direction.SOUTH, dz);
                if (!base.isValidAnchor(b.core)) base = args.getString("mode").equals("center")
                        ? Area.centered(b.core, b.core) : Area.inWall(b.core, facing);
                v.draft = base.bounds();
                preview(player, t, v);
            }
            case "reset" -> {
                v.draft = null;
                m.sendHighlight(player, b, false);
            }
            case "apply" -> {
                if (v.draft == null) return;
                Component error = m.applyBounds(b, area, v.draft);
                if (error != null) {
                    v.message = error;
                    v.messageOk = false;
                } else {
                    v.draft = null;
                    v.message = Component.translatable("message.bunker.size.applied");
                    v.messageOk = true;
                    m.validateNow(b);
                    m.sendHighlight(player, b, true);
                }
            }
            default -> {
                return;
            }
        }
        if (m.byId(b.id) != null) {
            Target now = resolve(level, pos);
            if (now != null && now.bunker() != null) send(player, now, v, false);
        }
    }

    private static void handlePlacing(ServerPlayer player, Target t, View v, String action, CompoundTag args) {
        Bunker b = t.bunker();
        BunkerManager m = t.manager();
        List<Area> variants = m.roomVariants(b, t.pos());
        switch (action) {
            case "variant" -> {
                if (variants.isEmpty()) return;
                int step = args.getInt("step") >= 0 ? 1 : -1;
                v.variant = Math.floorMod(v.variant + step, variants.size());
                m.sendPreview(player, b, List.of(variants.get(v.variant)));
            }
            case "confirm" -> {
                Component error = m.confirmPlacement(b, t.pos(), Math.min(v.variant, Math.max(0, variants.size() - 1)), player);
                if (error != null) {
                    v.message = error;
                    v.messageOk = false;
                } else {
                    v.variant = 0;
                    m.validateNow(b);
                    m.sendHighlight(player, b, false);
                }
            }
            default -> {}
        }
    }

    @Nullable
    private static Area areaByAnchor(Bunker b, @Nullable BlockPos pos) {
        if (pos == null) return null;
        if (pos.equals(b.core)) return b.coreArea;
        return b.rooms.get(pos);
    }

    private static Area draftArea(Area area, @Nullable int[] draft) {
        Area a = area.copy();
        if (draft != null) a.setBounds(draft);
        return a;
    }

    private static void preview(ServerPlayer player, Target t, View v) {
        if (v.draft != null && t.area() != null && java.util.Arrays.equals(v.draft, t.area().bounds())) v.draft = null;
        if (v.draft == null) {
            t.manager().sendHighlight(player, t.bunker(), false);
        } else {
            t.manager().sendPreview(player, t.bunker(), List.of(draftArea(t.area(), v.draft)));
        }
    }

    /** v3 6.2: отключение комнаты с игроками требует подтверждения. Область ядра не отключается. */
    private static void toggle(ServerPlayer player, Target t, View v, CompoundTag args) {
        Bunker b = t.bunker();
        BunkerManager m = t.manager();
        Area target = t.room() ? t.area() : NbtUtils.readBlockPos(args, "pos").map(b.rooms::get).orElse(null);
        if (target == null || target == b.coreArea) return;
        if (!target.manualOff) {
            int players = m.playersIn(b, target);
            if (players > 0 && !args.getBoolean("confirm")) {
                v.confirmRoom = target.anchor;
                return;
            }
        }
        v.confirmRoom = null;
        target.manualOff = !target.manualOff;
        m.markDirty();
        m.notifyOwner(b, Component.translatable(target.manualOff ? "message.bunker.room.disabled" : "message.bunker.room.enabled",
                EnergySystem.roomName(b, target)), false);
    }

    // ================================================================ улучшение

    private static int currentLevel(Target t) {
        return t.room() ? t.area().level : t.bunker().level;
    }

    private static BunkerUpgrades.LevelDef nextDef(Target t) {
        int next = currentLevel(t) + 1;
        return t.room() ? BunkerUpgrades.room(next) : BunkerUpgrades.core(next);
    }

    /** null — можно улучшать, иначе причина. */
    @Nullable
    private static Component upgradeBlocker(ServerPlayer player, Target t) {
        Bunker b = t.bunker();
        if (currentLevel(t) >= 3) return Component.translatable("gui.bunker.upgrade.max");
        if (BunkerConfig.UPGRADE_REQUIRES_ACTIVE.get() && b.state != State.ACTIVE) {
            return Component.translatable("gui.bunker.upgrade.need_active");
        }
        if (!player.hasInfiniteMaterials()) {
            for (BunkerUpgrades.Cost c : nextDef(t).cost()) {
                if (c.countIn(player) < c.count()) return Component.translatable("gui.bunker.upgrade.not_enough");
            }
        }
        return null;
    }

    private static void upgrade(ServerPlayer player, Target t, View v) {
        Component blocker = upgradeBlocker(player, t);
        if (blocker != null) {
            v.message = blocker;
            v.messageOk = false;
            return;
        }
        Bunker b = t.bunker();
        BunkerUpgrades.LevelDef def = nextDef(t);
        if (!player.hasInfiniteMaterials()) {
            for (BunkerUpgrades.Cost c : def.cost()) take(player, c);
        }
        if (t.room()) {
            t.manager().setRoomLevel(b, t.pos(), def.level());
        } else {
            t.manager().setCoreLevel(b, def.level());
        }
        player.serverLevel().playSound(null, t.pos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        v.message = Component.translatable("message.bunker.upgraded", romanOf(def.level()));
        v.messageOk = true;
    }

    private static void take(ServerPlayer player, BunkerUpgrades.Cost cost) {
        int left = cost.count();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && cost.test(s)) {
                int n = Math.min(left, s.getCount());
                s.shrink(n);
                left -= n;
            }
        }
        inv.setChanged();
    }

    private static String romanOf(int level) {
        return com.shardsofvoid.bunker.radiation.RadiationSystem.roman(level);
    }

    // ================================================================ данные экрана

    public static String json(Component c, HolderLookup.Provider registries) {
        return Component.Serializer.toJson(c, registries);
    }

    private static void send(ServerPlayer player, Target t, View v, boolean open) {
        Bunker b = t.bunker();
        HolderLookup.Provider reg = player.registryAccess();
        CompoundTag d = new CompoundTag();
        d.putInt("state", b.state.ordinal());
        d.putString("owner", b.ownerName == null ? "" : b.ownerName);
        d.putBoolean("placing", t.placing());
        d.putInt("coreLevel", b.level);
        d.putBoolean("doorsLocked", b.doorsLocked);
        d.putBoolean("doorBreach", b.doorBreach);
        d.putInt("roomCount", b.rooms.size());
        d.putInt("roomLimit", BunkerUpgrades.maxRooms(b.level));
        if (v.message != null) {
            d.putString("msg", json(v.message, reg));
            d.putBoolean("msgOk", v.messageOk);
        }

        if (t.placing()) {
            writePlacing(d, t, v);
        } else {
            Area area = t.area();
            d.putString("name", json(EnergySystem.roomName(b, area), reg));
            d.putString("rawName", area.name);
            d.putInt("level", area == b.coreArea ? b.level : area.level);
            switch (v.tab) {
                case TAB_ROOMS -> writeRooms(d, t, v, reg);
                case TAB_ENERGY -> d.put("energy", b.energy.toTag());
                case TAB_LIFE -> {
                    CompoundTag life = b.energy.lifeTag();
                    life.putBoolean("enabled", BunkerConfig.LIFE_SUPPORT_ENABLED.get());
                    d.put("life", life);
                }
                case TAB_SIZE -> writeSize(d, t, v, reg);
                default -> writeOverview(d, player, t, reg);
            }
        }
        PacketDistributor.sendToPlayer(player, new BunkerScreenPayload(t.pos(), t.room(), open, t.placing() ? TAB_OVERVIEW : v.tab, d));
    }

    private static void writeOverview(CompoundTag d, ServerPlayer player, Target t, HolderLookup.Provider reg) {
        Bunker b = t.bunker();
        ListTag conditions = new ListTag();
        for (ActivationConditions.Result r : b.lastConditions) {
            CompoundTag c = new CompoundTag();
            c.putString("label", json(r.label(), reg));
            c.putString("value", json(r.value(), reg));
            c.putBoolean("ok", r.ok());
            conditions.add(c);
        }
        d.put("conditions", conditions);
        d.putInt("shellPresent", b.shellPresent);
        d.putInt("shellExpected", b.shellExpected);
        d.putBoolean("sealed", b.sealed);
        d.putString("seal", "gui.bunker.seal." + b.lastOutcome.name().toLowerCase(java.util.Locale.ROOT));

        Area area = t.area();
        if (t.room()) {
            d.putBoolean("manualOff", area.manualOff);
            d.putBoolean("unpowered", area.unpowered);
            d.putInt("fe", EnergySystem.areaCost(area));
            d.putString("size", area.sizeText());
            d.putInt("players", t.manager().playersIn(b, area));
            d.putString("corePos", b.core.getX() + " " + b.core.getY() + " " + b.core.getZ());
        }

        int level = currentLevel(t);
        if (level < 3) {
            BunkerUpgrades.LevelDef def = nextDef(t);
            d.putInt("nextLevel", def.level());
            ListTag cost = new ListTag();
            for (BunkerUpgrades.Cost c : def.cost()) {
                ItemStack shown = c.displayStack();
                CompoundTag ct = new CompoundTag();
                ct.putString("item", BuiltInRegistries.ITEM.getKey(shown.getItem()).toString());
                ct.putInt("count", c.count());
                ct.putInt("have", player.hasInfiniteMaterials() ? c.count() : c.countIn(player));
                cost.add(ct);
            }
            d.put("cost", cost);
            Component blocker = upgradeBlocker(player, t);
            d.putBoolean("canUpgrade", blocker == null);
            Component info;
            if (blocker != null) {
                info = blocker;
            } else if (t.room()) {
                info = Component.translatable("gui.bunker.upgrade.room_result", romanOf(def.level()), def.expansion());
            } else {
                info = Component.translatable("gui.bunker.upgrade.core_result", romanOf(def.level()), def.maxRooms(),
                        def.generation(), def.expansion(), def.maxBatteries());
            }
            d.putString("upgradeInfo", json(info, reg));
        }
    }

    private static void writeRooms(CompoundTag d, Target t, View v, HolderLookup.Provider reg) {
        Bunker b = t.bunker();
        BunkerManager m = t.manager();
        ListTag list = new ListTag();
        for (Area a : b.areas()) {
            CompoundTag c = new CompoundTag();
            c.put("pos", NbtUtils.writeBlockPos(a.anchor));
            c.putString("name", json(EnergySystem.roomName(b, a), reg));
            c.putString("raw", a.name);
            c.putString("size", a.sizeText());
            c.putInt("level", a == b.coreArea ? b.level : a.level);
            c.putInt("fe", EnergySystem.areaCost(a));
            c.putString("status", a == b.coreArea ? "core" : a.manualOff ? "manual_off" : a.unpowered ? "unpowered" : "on");
            c.putBoolean("core", a == b.coreArea);
            list.add(c);
        }
        for (BlockPos p : b.pendingRooms) {
            CompoundTag c = new CompoundTag();
            c.put("pos", NbtUtils.writeBlockPos(p));
            c.putString("name", json(Component.translatable("gui.bunker.area.pending"), reg));
            c.putString("raw", "");
            c.putString("size", "—");
            c.putInt("level", m.level().getBlockEntity(p) instanceof BunkerPartBlockEntity be ? be.level() : 1);
            c.putInt("fe", 0);
            c.putString("status", "placing");
            c.putBoolean("core", false);
            list.add(c);
        }
        d.put("areas", list);
        if (v.confirmRoom != null) {
            Area a = b.rooms.get(v.confirmRoom);
            if (a != null) {
                CompoundTag c = new CompoundTag();
                c.put("pos", NbtUtils.writeBlockPos(v.confirmRoom));
                c.putInt("players", m.playersIn(b, a));
                c.putString("name", json(EnergySystem.roomName(b, a), reg));
                d.put("confirm", c);
            } else {
                v.confirmRoom = null;
            }
        }
    }

    private static void writeSize(CompoundTag d, Target t, View v, HolderLookup.Provider reg) {
        Bunker b = t.bunker();
        Area area = t.area();
        Area draft = draftArea(area, v.draft);
        d.putIntArray("bounds", area.bounds());
        d.putIntArray("draft", draft.bounds());
        d.putBoolean("changed", v.draft != null);
        d.putInt("budget", BunkerManager.expansionBudget(b, area));
        d.putInt("used", draft.expansionUsed());
        d.putInt("maxH", BunkerConfig.AREA_MAX_HORIZONTAL.get());
        d.putInt("maxV", BunkerConfig.AREA_MAX_VERTICAL.get());
        d.putString("curSize", area.sizeText());
        d.putString("draftSize", draft.sizeText());
        d.putInt("curFe", EnergySystem.areaCost(area));
        d.putInt("draftFe", EnergySystem.areaCost(draft));
        List<Area> others = new ArrayList<>(b.areas());
        others.remove(area);
        byte[] shared = new byte[6];
        for (Direction dir : Direction.values()) shared[dir.get3DDataValue()] = (byte) (BunkerGeometry.isSharedWall(area, dir, others) ? 1 : 0);
        d.putByteArray("shared", shared);
        d.putBoolean("canShift", !t.room() && b.rooms.isEmpty());
        d.putBoolean("isCore", !t.room());
        if (v.draft != null) {
            Component error = t.manager().checkBounds(b, area, v.draft);
            if (error != null) d.putString("error", json(error, reg));
        }
    }

    private static void writePlacing(CompoundTag d, Target t, View v) {
        List<Area> variants = t.manager().roomVariants(t.bunker(), t.pos());
        d.putInt("variants", variants.size());
        if (v.variant >= variants.size()) v.variant = 0;
        d.putInt("variant", v.variant);
        if (!variants.isEmpty()) {
            Area a = variants.get(v.variant);
            d.putIntArray("variantBounds", a.bounds());
            d.putString("variantSize", a.sizeText());
        }
    }

    /** Для команды /bunker info. */
    public static Component describe(Bunker b) {
        return Component.translatable("command.bunker.info.line", b.ownerName, b.core.getX(), b.core.getY(), b.core.getZ(),
                Component.translatable("gui.bunker.state." + b.state.name().toLowerCase(java.util.Locale.ROOT)), romanOf(b.level), b.rooms.size(),
                BunkerUpgrades.maxRooms(b.level), b.shellPresent, b.shellExpected);
    }
}
