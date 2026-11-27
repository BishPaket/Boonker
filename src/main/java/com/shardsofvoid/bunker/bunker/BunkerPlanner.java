package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.block.BunkerPartBlock;
import com.shardsofvoid.bunker.block.BunkerPartBlockEntity;
import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.config.BunkerConfig.Adjacency;
import com.shardsofvoid.bunker.energy.EnergySystem;
import com.shardsofvoid.bunker.item.BunkerRoomItem;
import com.shardsofvoid.bunker.network.BunkerActionPayload;
import com.shardsofvoid.bunker.network.BunkerScreenPayload;
import com.shardsofvoid.bunker.registry.ModBlocks;
import com.shardsofvoid.bunker.registry.ModComponents;
import com.shardsofvoid.bunker.registry.ModItems;
import com.shardsofvoid.bunker.registry.ModMisc;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Пульт проектирования бункера: стоит впритык к ядру, показывает бункер миникартой и сам строит новую комнату.
 * Игрок выбирает, к какой области и с какой стороны примыкает комната, её внутренний размер по X/Y/Z и смещение.
 * Стройка: оболочка из бункерного металла (недостающие блоки), выкапывание внутреннего объёма и прохода 1×2 в
 * общей стене, блок комнаты в центре пола. Металл и блок комнаты берутся из инвентаря игрока и хранилищ бункера,
 * всё добытое кладётся в хранилища (не влезло — игроку, затем на пол у пульта).
 */
public final class BunkerPlanner {
    private BunkerPlanner() {}

    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    /** Черновик комнаты в открытом пульте. */
    private static final class View {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        int target;
        int side;
        int sx = 3, sy = 3, sz = 3;
        int du, dv;
        boolean sideView;
        /** 0 — новая комната, 1 — список комнат (улучшение и перестройка). */
        int mode;
        int selected;
        @Nullable int[] edit;
        @Nullable Component message;
        boolean messageOk;

        View(ResourceKey<Level> dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos.immutable();
        }
    }

    private static final Map<UUID, View> VIEWS = new HashMap<>();

    public static void forget(UUID player) {
        VIEWS.remove(player);
    }

    /** Ядро, к которому примыкает пульт, или null. */
    @Nullable
    public static Bunker coreNear(ServerLevel level, BlockPos pos) {
        BunkerManager m = BunkerManager.get(level);
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.isLoaded(n) && level.getBlockState(n).is(ModBlocks.BUNKER_CORE.get())) {
                Bunker b = m.byCore(n);
                if (b != null) return b;
            }
        }
        return null;
    }

    public static boolean hasCoreNeighbour(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) if (level.getBlockState(pos.relative(d)).is(ModBlocks.BUNKER_CORE.get())) return true;
        return false;
    }

    private static boolean canEdit(ServerPlayer player, Bunker b) {
        return b.owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    // ================================================================ открытие и обновление

    public static void open(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        Bunker b = coreNear(level, pos);
        if (b == null) {
            player.displayClientMessage(Component.translatable("message.bunker.planner.no_core").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!canEdit(player, b)) {
            player.displayClientMessage(Component.translatable("message.bunker.foreign", b.ownerName).withStyle(ChatFormatting.RED), true);
            return;
        }
        View v = VIEWS.get(player.getUUID());
        if (v == null || !v.pos.equals(pos) || !v.dimension.equals(level.dimension())) {
            v = new View(level.dimension(), pos);
            VIEWS.put(player.getUUID(), v);
            autoSide(BunkerManager.get(level), b, v);
        }
        BunkerManager.get(level).validateNow(b);
        send(player, b, v, true);
    }

    public static void tickViews(ServerLevel level) {
        if (VIEWS.isEmpty() || level.getGameTime() % 20 != 10) return;
        for (Iterator<Map.Entry<UUID, View>> it = VIEWS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, View> e = it.next();
            View v = e.getValue();
            if (!v.dimension.equals(level.dimension())) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
            Bunker b = player == null ? null : coreNear(level, v.pos);
            if (player == null || b == null || player.serverLevel() != level
                    || player.distanceToSqr(Vec3.atCenterOf(v.pos)) > MAX_DISTANCE_SQR || !canEdit(player, b)) {
                it.remove();
                continue;
            }
            send(player, b, v, false);
        }
    }

    public static boolean isPlanner(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(com.shardsofvoid.bunker.registry.ModMachines.BUNKER_PLANNER.get());
    }

    // ================================================================ действия

    public static void handle(ServerPlayer player, BunkerActionPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        CompoundTag args = payload.args();
        String action = args.getString("a");
        if (action.equals("close")) {
            VIEWS.remove(player.getUUID());
            return;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) return;
        Bunker b = coreNear(level, pos);
        if (b == null || !canEdit(player, b)) return;
        BunkerManager m = BunkerManager.get(level);
        View v = VIEWS.get(player.getUUID());
        if (v == null || !v.pos.equals(pos)) {
            v = new View(level.dimension(), pos);
            VIEWS.put(player.getUUID(), v);
            autoSide(m, b, v);
        }
        v.message = null;
        List<Area> areas = b.areas();
        int maxH = BunkerManager.maxHorizontal(b);
        int maxV = BunkerManager.maxVertical(b);
        switch (action) {
            case "target" -> {
                int step = args.getInt("step") >= 0 ? 1 : -1;
                v.target = Math.floorMod(v.target + step, Math.max(1, areas.size()));
                v.du = v.dv = 0;
            }
            case "select" -> {
                int i = args.getInt("index");
                if (i >= 0 && i < areas.size()) {
                    if (v.mode == 1) {
                        if (v.selected != i) v.edit = null;
                        v.selected = i;
                    } else {
                        v.target = i;
                        v.du = v.dv = 0;
                    }
                }
            }
            case "mode" -> {
                v.mode = v.mode == 0 ? 1 : 0;
                v.edit = null;
            }
            case "pick" -> {
                int step = args.getInt("step") >= 0 ? 1 : -1;
                v.selected = Math.floorMod(v.selected + step, Math.max(1, areas.size()));
                v.edit = null;
            }
            case "wall" -> {
                Area a = selectedArea(b, v);
                int dir = args.getInt("dir");
                if (a == null || dir < 0 || dir >= 6) return;
                Area d = a.copy();
                if (v.edit != null) d.setBounds(v.edit);
                d.moveWall(Direction.from3DDataValue(dir), args.getInt("delta") >= 0 ? 1 : -1);
                v.edit = java.util.Arrays.equals(d.bounds(), a.bounds()) ? null : d.bounds();
                m.sendPreview(player, b, List.of(d));
            }
            case "reset" -> {
                v.edit = null;
                m.sendHighlight(player, b, false);
            }
            case "rebuild" -> rebuild(player, m, b, v);
            case "show" -> {
                Area a = selectedArea(b, v);
                if (a != null && v.edit != null) {
                    Area d = a.copy();
                    d.setBounds(v.edit);
                    m.sendPreview(player, b, List.of(d));
                } else {
                    m.sendHighlight(player, b, true);
                }
            }
            case "upgrade" -> upgradeArea(player, m, b, v);
            case "side" -> {
                int step = args.getInt("step") >= 0 ? 1 : -1;
                v.side = Math.floorMod(v.side + step, SIDES.length);
                v.du = v.dv = 0;
            }
            case "size" -> {
                int axis = args.getInt("axis");
                int delta = args.getInt("delta") >= 0 ? 1 : -1;
                if (axis == 0) v.sx = clamp(v.sx + delta, Area.BASE_INNER, maxH);
                if (axis == 1) v.sy = clamp(v.sy + delta, Area.BASE_INNER, maxV);
                if (axis == 2) v.sz = clamp(v.sz + delta, Area.BASE_INNER, maxH);
            }
            case "offset" -> {
                int axis = args.getInt("axis");
                int delta = args.getInt("delta") >= 0 ? 1 : -1;
                if (axis == 0) v.du = clamp(v.du + delta, -16, 16);
                else v.dv = clamp(v.dv + delta, -16, 16);
            }
            case "view" -> v.sideView = !v.sideView;
            case "preview" -> {
                Area draft = draft(b, v);
                if (draft != null) m.sendPreview(player, b, List.of(draft));
            }
            case "build" -> build(player, m, b, v);
            default -> {
                return;
            }
        }
        if (v.mode == 0 && !action.equals("build") && !action.equals("view") && !action.equals("mode")) {
            Area draft = draft(b, v);
            if (draft != null) m.sendPreview(player, b, List.of(draft));
        }
        send(player, b, v, false);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Первая сторона области ядра, где помещается комната 3×3×3, — чтобы черновик сразу был годным. */
    private static void autoSide(BunkerManager m, Bunker b, View v) {
        for (int s = 0; s < SIDES.length; s++) {
            v.side = s;
            Area d = draft(b, v);
            if (d != null && geometryError(m, b, d) == null) return;
        }
        v.side = 0;
    }

    // ================================================================ геометрия черновика

    /** Область новой комнаты: примыкает к выбранной области выбранной стороной, центр по грани + смещение. */
    @Nullable
    static Area draft(Bunker b, View v) {
        List<Area> areas = b.areas();
        if (areas.isEmpty()) return null;
        Area t = areas.get(Math.floorMod(v.target, areas.size()));
        Direction side = SIDES[Math.floorMod(v.side, SIDES.length)];
        int gap = BunkerGeometry.mode() == Adjacency.DOUBLE_WALL ? 1 : 0;
        int ox = v.sx + 2, oy = v.sy + 2, oz = v.sz + 2;
        int minX, minY, minZ;
        int cx = (t.minX + t.maxX) / 2, cz = (t.minZ + t.maxZ) / 2;
        switch (side) {
            case EAST -> {
                minX = t.maxX + gap;
                minZ = cz - (oz - 1) / 2 + v.du;
                minY = t.minY + v.dv;
            }
            case WEST -> {
                minX = t.minX - gap - ox + 1;
                minZ = cz - (oz - 1) / 2 + v.du;
                minY = t.minY + v.dv;
            }
            case SOUTH -> {
                minZ = t.maxZ + gap;
                minX = cx - (ox - 1) / 2 + v.du;
                minY = t.minY + v.dv;
            }
            case NORTH -> {
                minZ = t.minZ - gap - oz + 1;
                minX = cx - (ox - 1) / 2 + v.du;
                minY = t.minY + v.dv;
            }
            case UP -> {
                minY = t.maxY + gap;
                minX = cx - (ox - 1) / 2 + v.du;
                minZ = cz - (oz - 1) / 2 + v.dv;
            }
            default -> {
                minY = t.minY - gap - oy + 1;
                minX = cx - (ox - 1) / 2 + v.du;
                minZ = cz - (oz - 1) / 2 + v.dv;
            }
        }
        int maxX = minX + ox - 1, maxY = minY + oy - 1, maxZ = minZ + oz - 1;
        // блок комнаты — в центре пола (для комнаты над областью — в центре потолка, пол там общий)
        int ay = side == Direction.UP ? maxY : minY;
        BlockPos anchor = new BlockPos((minX + maxX) / 2, ay, (minZ + maxZ) / 2);
        return new Area(anchor, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Nullable
    private static Area targetArea(Bunker b, View v) {
        List<Area> areas = b.areas();
        return areas.isEmpty() ? null : areas.get(Math.floorMod(v.target, areas.size()));
    }

    /** Проверки формы без мира: лимиты, бюджет, примыкание, пересечения. */
    @Nullable
    private static Component geometryError(BunkerManager m, Bunker b, Area d) {
        int maxRooms = BunkerUpgrades.maxRooms(b.level);
        if (b.rooms.size() + b.pendingRooms.size() >= maxRooms) return Component.translatable("message.bunker.room.limit", b.rooms.size(), maxRooms);
        int maxH = BunkerManager.maxHorizontal(b), maxV = BunkerManager.maxVertical(b);
        if (d.innerX() > maxH || d.innerZ() > maxH || d.innerY() > maxV) {
            return Component.translatable("message.bunker.size.axis_limit", maxH, maxV);
        }
        List<Area> own = b.areas();
        boolean adjacent = false;
        for (Area a : own) if (BunkerGeometry.adjacent(a, d)) adjacent = true;
        if (!adjacent) return Component.translatable("message.bunker.planner.face", BunkerGeometry.minFace());
        if (BunkerGeometry.overlapsOwn(d, own)) return Component.translatable("message.bunker.size.overlap");
        if (m.foreignOverlap(b, d) != null) return Component.translatable("message.bunker.size.foreign");
        for (Area a : own) if (d.isInterior(a.anchor.getX(), a.anchor.getY(), a.anchor.getZ())) return Component.translatable("message.bunker.size.overlap");
        if (!d.isValidAnchor(d.anchor)) return Component.translatable("message.bunker.size.anchor");
        return null;
    }

    /** Что нужно сделать в мире. */
    private record Plan(List<BlockPos> shell, List<BlockPos> dig, List<BlockPos> door, int metal, @Nullable Component error) {}

    private static Plan plan(ServerLevel level, Bunker b, Area d, @Nullable Area target) {
        List<Area> all = new ArrayList<>(b.areas());
        all.add(d);
        return planCells(level, all, d, null, doorway(b, d, target));
    }

    /** План перестройки существующей области под новые границы: старый внутренний объём не трогается. */
    private static Plan rebuildPlan(ServerLevel level, Bunker b, Area old, Area next) {
        List<Area> all = new ArrayList<>(b.areas());
        all.replaceAll(a -> a == old ? next : a);
        return planCells(level, all, next, old, new LongOpenHashSet());
    }

    /**
     * Что сделать в клетках области d: недостающая оболочка — металл, внутренний объём — выкопать.
     * old — прежние границы перестраиваемой области: её внутренний объём (вещи игроков) остаётся как есть.
     */
    private static Plan planCells(ServerLevel level, List<Area> all, Area d, @Nullable Area old, LongOpenHashSet doorKeys) {
        LongLinkedOpenHashSet expected = BunkerGeometry.expectedShell(all);
        List<BlockPos> shell = new ArrayList<>();
        List<BlockPos> dig = new ArrayList<>();
        List<BlockPos> door = new ArrayList<>();
        int metal = 0;
        Component error = null;
        for (int x = d.minX; x <= d.maxX && error == null; x++) {
            for (int y = d.minY; y <= d.maxY && error == null; y++) {
                for (int z = d.minZ; z <= d.maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    long key = p.asLong();
                    if (!level.isLoaded(p)) {
                        error = Component.translatable("message.bunker.planner.unloaded");
                        break;
                    }
                    BlockState s = level.getBlockState(p);
                    if (doorKeys.contains(key)) continue;
                    if (d.isInterior(x, y, z)) {
                        if (s.isAir() || (old != null && old.isInterior(x, y, z))) continue;
                        error = breakError(level, p, s, false);
                        dig.add(p);
                    } else if (expected.contains(key)) {
                        if (p.equals(d.anchor)) {
                            if (!s.isAir() && !s.canBeReplaced()) error = breakError(level, p, s, false);
                            continue;
                        }
                        if (s.is(ModMisc.SHELL_BLOCKS)) continue; // общая стена соседней области или установка в стене
                        if (!s.isAir() && !s.canBeReplaced()) error = breakError(level, p, s, false);
                        shell.add(p);
                        metal++;
                    }
                    if (error != null) break;
                }
            }
        }
        for (long key : doorKeys) {
            if (error != null) break;
            BlockPos p = BlockPos.of(key);
            if (!level.isLoaded(p)) {
                error = Component.translatable("message.bunker.planner.unloaded");
                break;
            }
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            if (s.is(ModMisc.AIRLOCKS)) continue; // там уже стоит гермодверь
            error = breakError(level, p, s, true);
            door.add(p);
        }
        return new Plan(shell, dig, door, metal, error);
    }

    /**
     * Проход 1×2 посередине общей грани с выбранной областью (если она не смежна — с первой смежной),
     * для комнаты сверху или снизу — люк 1×1. Проход лежит в проходной зоне, на герметичность не влияет.
     */
    private static LongOpenHashSet doorway(Bunker b, Area d, @Nullable Area preferred) {
        LongOpenHashSet out = new LongOpenHashSet();
        Adjacency mode = BunkerGeometry.mode();
        List<Area> order = new ArrayList<>();
        if (preferred != null) order.add(preferred);
        order.addAll(b.areas());
        for (Area a : order) {
            BunkerGeometry.Face f = BunkerGeometry.sharedFace(a, d, mode);
            if (f == null || f.width() < BunkerGeometry.minFace() || f.height() < BunkerGeometry.minFace()) continue;
            int[] planes = mode == Adjacency.DOUBLE_WALL ? new int[] {f.planeA(), f.planeB()} : new int[] {f.planeA()};
            for (int plane : planes) {
                switch (f.dir().getAxis()) {
                    case X -> { // u — высота (Y), v — Z
                        int z = (f.v0() + f.v1()) / 2;
                        out.add(BlockPos.asLong(plane, f.u0() + 1, z));
                        out.add(BlockPos.asLong(plane, f.u0() + 2, z));
                    }
                    case Z -> { // u — X, v — высота (Y)
                        int x = (f.u0() + f.u1()) / 2;
                        out.add(BlockPos.asLong(x, f.v0() + 1, plane));
                        out.add(BlockPos.asLong(x, f.v0() + 2, plane));
                    }
                    case Y -> out.add(BlockPos.asLong((f.u0() + f.u1()) / 2, plane, (f.v0() + f.v1()) / 2));
                }
            }
            break;
        }
        return out;
    }

    @Nullable
    private static Component breakError(ServerLevel level, BlockPos p, BlockState s, boolean door) {
        if (level.getBlockEntity(p) != null) {
            return Component.translatable("message.bunker.planner.block_entity", s.getBlock().getName(), p.getX(), p.getY(), p.getZ());
        }
        if (s.getDestroySpeed(level, p) < 0 || (!door && ModMisc.isProtected(s) && !s.is(ModBlocks.BUNKER_METAL.get()))) {
            return Component.translatable("message.bunker.planner.unbreakable", s.getBlock().getName(), p.getX(), p.getY(), p.getZ());
        }
        return null;
    }

    // ================================================================ материалы

    private static Predicate<ItemStack> metalTest() {
        return s -> s.is(ModItems.BUNKER_METAL.get());
    }

    private static Predicate<ItemStack> roomTest(Bunker b) {
        return s -> {
            if (!(s.getItem() instanceof BunkerRoomItem)) return false;
            ModComponents.LinkedCore link = s.get(ModComponents.LINKED_CORE.get());
            return link == null || link.bunkerId().equals(b.id);
        };
    }

    /** Уровень лучшего доступного блока комнаты (0 — нет ни одного). */
    private static int roomItemLevel(ServerPlayer player, ServerLevel level, Bunker b) {
        int best = 0;
        Predicate<ItemStack> test = roomTest(b);
        for (ItemStack s : player.getInventory().items) if (!s.isEmpty() && test.test(s)) best = Math.max(best, levelOf(s));
        for (IItemHandler h : BunkerStorage.handlers(level, b)) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty() && test.test(s)) best = Math.max(best, levelOf(s));
            }
        }
        return best;
    }

    private static int levelOf(ItemStack s) {
        Integer l = s.get(ModComponents.ROOM_LEVEL.get());
        return l == null ? 1 : l;
    }

    // ================================================================ стройка

    private static void build(ServerPlayer player, BunkerManager m, Bunker b, View v) {
        ServerLevel level = player.serverLevel();
        Area d = draft(b, v);
        Component error = d == null ? Component.translatable("message.bunker.planner.no_draft") : geometryError(m, b, d);
        int roomLevel = roomItemLevel(player, level, b);
        if (error == null && roomLevel == 0) error = Component.translatable("message.bunker.planner.no_room_item");
        if (error == null) {
            int budget = BunkerManager.roomBudget(b, roomLevel);
            if (d.expansionUsed() > budget) error = Component.translatable("message.bunker.size.budget", d.expansionUsed(), budget);
        }
        Plan plan = error == null ? plan(level, b, d, targetArea(b, v)) : null;
        if (error == null && plan.error() != null) error = plan.error();
        if (error != null) {
            v.message = error;
            v.messageOk = false;
            return;
        }

        // материалы: блок комнаты нужного (лучшего) уровня и металл
        int lvl = roomLevel;
        List<ItemStack> roomItems = new ArrayList<>();
        Predicate<ItemStack> roomTest = roomTest(b).and(s -> levelOf(s) == lvl);
        if (!BunkerStorage.take(player, level, b, roomTest, 1, roomItems) || roomItems.isEmpty()) {
            v.message = Component.translatable("message.bunker.planner.no_room_item");
            v.messageOk = false;
            return;
        }
        if (!BunkerStorage.take(player, level, b, metalTest(), plan.metal(), null)) {
            if (!player.hasInfiniteMaterials()) BunkerStorage.give(player, level, b, roomItems.get(0), v.pos);
            v.message = Component.translatable("message.bunker.planner.no_metal", plan.metal(),
                    BunkerStorage.count(player, level, b, metalTest()));
            v.messageOk = false;
            return;
        }

        // 1. оболочка (сначала — чтобы песок и вода снаружи не провалились внутрь), 2. объём, 3. проход
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        List<ItemStack> drops = new ArrayList<>();
        BlockState metal = ModBlocks.BUNKER_METAL.get().defaultBlockState();
        for (BlockPos p : plan.shell()) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir()) drops.addAll(Block.getDrops(s, level, p, null, player, tool));
            level.setBlock(p, metal, Block.UPDATE_ALL);
        }
        for (BlockPos p : plan.dig()) {
            BlockState s = level.getBlockState(p);
            drops.addAll(Block.getDrops(s, level, p, null, player, tool));
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (BlockPos p : plan.door()) {
            BlockState s = level.getBlockState(p);
            drops.addAll(Block.getDrops(s, level, p, null, player, tool));
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }

        // 4. блок комнаты
        BlockState old = level.getBlockState(d.anchor);
        if (!old.isAir()) drops.addAll(Block.getDrops(old, level, d.anchor, null, player, tool));
        Direction facing = d.anchor.getY() == d.maxY ? Direction.DOWN : Direction.UP;
        BlockState room = ModBlocks.BUNKER_ROOM.get().defaultBlockState()
                .setValue(BunkerPartBlock.LEVEL, Math.max(1, Math.min(3, lvl))).setValue(BunkerPartBlock.FACING, facing);
        level.setBlock(d.anchor, room, Block.UPDATE_ALL);
        if (level.getBlockEntity(d.anchor) instanceof BunkerPartBlockEntity be) {
            be.bind(b.id, b.owner, b.ownerName);
            be.setLevel(lvl);
        }
        m.attachBuiltRoom(b, d, lvl, player);

        // добытое — в хранилища бункера
        List<IItemHandler> storages = BunkerStorage.handlers(level, b);
        BunkerStorage.giveAll(player, level, b, mergeStacks(drops), v.pos, storages);

        level.playSound(null, v.pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
        m.validateNow(b);
        m.sendHighlight(player, b, false);
        v.message = Component.translatable("message.bunker.planner.built", d.sizeText(), plan.metal(), plan.dig().size() + plan.door().size());
        v.messageOk = true;
        v.du = v.dv = 0;
    }

    // ================================================================ комнаты: перестройка и улучшение

    @Nullable
    private static Area selectedArea(Bunker b, View v) {
        List<Area> areas = b.areas();
        if (areas.isEmpty()) return null;
        v.selected = Math.floorMod(v.selected, areas.size());
        return areas.get(v.selected);
    }

    /** Вскопать, поставить, собрать добытое. Возвращает добытые стеки (ещё не розданные). */
    private static List<ItemStack> execute(ServerPlayer player, ServerLevel level, Plan plan) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        List<ItemStack> drops = new ArrayList<>();
        BlockState metal = ModBlocks.BUNKER_METAL.get().defaultBlockState();
        for (BlockPos p : plan.shell()) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir()) drops.addAll(Block.getDrops(s, level, p, null, player, tool));
            level.setBlock(p, metal, Block.UPDATE_ALL);
        }
        for (BlockPos p : plan.dig()) {
            BlockState s = level.getBlockState(p);
            drops.addAll(Block.getDrops(s, level, p, null, player, tool));
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (BlockPos p : plan.door()) {
            BlockState s = level.getBlockState(p);
            drops.addAll(Block.getDrops(s, level, p, null, player, tool));
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        return drops;
    }

    /** Новые границы выбранной области + достройка: новые стены из металла, новый объём выкапывается. */
    private static void rebuild(ServerPlayer player, BunkerManager m, Bunker b, View v) {
        ServerLevel level = player.serverLevel();
        Area a = selectedArea(b, v);
        if (a == null || v.edit == null) return;
        Component error = m.checkBounds(b, a, v.edit);
        Area next = a.copy();
        next.setBounds(v.edit);
        Plan plan = error == null ? rebuildPlan(level, b, a, next) : null;
        if (error == null && plan.error() != null) error = plan.error();
        if (error == null && !BunkerStorage.take(player, level, b, metalTest(), plan.metal(), null)) {
            error = Component.translatable("message.bunker.planner.no_metal", plan.metal(), BunkerStorage.count(player, level, b, metalTest()));
        }
        if (error == null) {
            error = m.applyBounds(b, a, v.edit);
            if (error != null && !player.hasInfiniteMaterials() && plan.metal() > 0) {
                BunkerStorage.give(player, level, b, new ItemStack(ModItems.BUNKER_METAL.get(), plan.metal()), v.pos);
            }
        }
        if (error != null) {
            v.message = error;
            v.messageOk = false;
            return;
        }
        List<ItemStack> drops = execute(player, level, plan);
        BunkerStorage.giveAll(player, level, b, mergeStacks(drops), v.pos, BunkerStorage.handlers(level, b));
        level.playSound(null, v.pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
        m.validateNow(b);
        m.sendHighlight(player, b, false);
        v.message = Component.translatable("message.bunker.planner.rebuilt", EnergySystem.roomName(b, a), a.sizeText(), plan.metal(),
                plan.dig().size());
        v.messageOk = true;
        v.edit = null;
    }

    private static int levelOfArea(Bunker b, Area a) {
        return a == b.coreArea ? b.level : a.level;
    }

    private static List<BunkerUpgrades.Cost> upgradeCost(Bunker b, Area a) {
        int next = levelOfArea(b, a) + 1;
        return (a == b.coreArea ? BunkerUpgrades.core(next) : BunkerUpgrades.room(next)).cost();
    }

    @Nullable
    private static Component upgradeBlocker(ServerPlayer player, Bunker b, Area a) {
        if (levelOfArea(b, a) >= 3) return Component.translatable("gui.bunker.upgrade.max");
        if (BunkerConfig.UPGRADE_REQUIRES_ACTIVE.get() && b.state != Bunker.State.ACTIVE) {
            return Component.translatable("gui.bunker.upgrade.need_active");
        }
        if (!BunkerActions.canPay(player, b, upgradeCost(b, a))) return Component.translatable("gui.bunker.upgrade.not_enough");
        return null;
    }

    private static void upgradeArea(ServerPlayer player, BunkerManager m, Bunker b, View v) {
        Area a = selectedArea(b, v);
        if (a == null) return;
        Component blocker = upgradeBlocker(player, b, a);
        if (blocker == null && !BunkerActions.pay(player, b, upgradeCost(b, a))) blocker = Component.translatable("gui.bunker.upgrade.not_enough");
        if (blocker != null) {
            v.message = blocker;
            v.messageOk = false;
            return;
        }
        int next = levelOfArea(b, a) + 1;
        if (a == b.coreArea) m.setCoreLevel(b, next);
        else m.setRoomLevel(b, a.anchor, next);
        player.serverLevel().playSound(null, v.pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        v.message = Component.translatable("message.bunker.planner.upgraded", EnergySystem.roomName(b, a),
                com.shardsofvoid.bunker.radiation.RadiationSystem.roman(next));
        v.messageOk = true;
    }

    private static void writeRooms(CompoundTag d, ServerPlayer player, BunkerManager m, Bunker b, View v, HolderLookup.Provider reg) {
        Area a = selectedArea(b, v);
        if (a == null) return;
        ServerLevel level = player.serverLevel();
        d.putInt("selected", v.selected);
        d.putString("selName", BunkerActions.json(EnergySystem.roomName(b, a), reg));
        d.putInt("selLevel", levelOfArea(b, a));
        d.putString("selSize", a.sizeText());
        d.putInt("selFe", EnergySystem.areaCost(a));
        d.putInt("budget", BunkerManager.expansionBudget(b, a));
        List<Area> others = new ArrayList<>(b.areas());
        others.remove(a);
        byte[] shared = new byte[6];
        for (Direction dir : Direction.values()) shared[dir.get3DDataValue()] = (byte) (BunkerGeometry.isSharedWall(a, dir, others) ? 1 : 0);
        d.putByteArray("shared", shared);
        Component error = null;
        if (v.edit != null) {
            Area next = a.copy();
            next.setBounds(v.edit);
            d.putIntArray("edit", v.edit);
            d.putString("editSize", next.sizeText());
            d.putInt("editFe", EnergySystem.areaCost(next));
            d.putInt("used", next.expansionUsed());
            error = m.checkBounds(b, a, v.edit);
            if (error == null) {
                Plan plan = rebuildPlan(level, b, a, next);
                d.putInt("metal", plan.metal());
                d.putInt("dig", plan.dig().size());
                d.putInt("metalHave", player.hasInfiniteMaterials() ? plan.metal() : BunkerStorage.count(player, level, b, metalTest()));
                if (plan.error() != null) error = plan.error();
            }
        } else {
            d.putInt("used", a.expansionUsed());
        }
        if (error != null) d.putString("error", BunkerActions.json(error, reg));
        // улучшение
        if (levelOfArea(b, a) < 3) {
            StringBuilder cost = new StringBuilder();
            Component costText = Component.empty();
            for (BunkerUpgrades.Cost c : upgradeCost(b, a)) {
                int have = player.hasInfiniteMaterials() ? c.count() : BunkerStorage.count(player, level, b, c::test);
                costText = Component.empty().append(costText).append(cost.isEmpty() ? "" : ", ")
                        .append(Component.literal(c.count() + "× ")).append(c.displayStack().getHoverName())
                        .append(Component.literal(" (" + have + ")"));
                cost.append('x');
            }
            Component blocker = upgradeBlocker(player, b, a);
            d.putBoolean("canUpgrade", blocker == null);
            Component info = Component.translatable("gui.bunker.planner.upgrade_cost",
                    com.shardsofvoid.bunker.radiation.RadiationSystem.roman(levelOfArea(b, a) + 1), costText);
            if (blocker != null) info = Component.empty().append(blocker).append(" · ").append(info);
            d.putString("upgradeInfo", BunkerActions.json(info, reg));
        }
    }

    private static List<ItemStack> mergeStacks(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : in) {
            if (s.isEmpty()) continue;
            boolean merged = false;
            for (ItemStack o : out) {
                if (ItemStack.isSameItemSameComponents(o, s) && o.getCount() < o.getMaxStackSize()) {
                    int n = Math.min(s.getCount(), o.getMaxStackSize() - o.getCount());
                    o.grow(n);
                    s.shrink(n);
                    if (s.isEmpty()) {
                        merged = true;
                        break;
                    }
                }
            }
            if (!merged && !s.isEmpty()) out.add(s.copy());
        }
        return out;
    }

    // ================================================================ данные экрана

    private static void send(ServerPlayer player, Bunker b, View v, boolean open) {
        ServerLevel level = player.serverLevel();
        BunkerManager m = BunkerManager.get(level);
        HolderLookup.Provider reg = player.registryAccess();
        CompoundTag d = new CompoundTag();
        d.putBoolean("planner", true);
        d.putString("owner", b.ownerName == null ? "" : b.ownerName);
        d.putInt("state", b.state.ordinal());
        d.putIntArray("core", new int[] {b.core.getX(), b.core.getY(), b.core.getZ()});
        d.putIntArray("console", new int[] {v.pos.getX(), v.pos.getY(), v.pos.getZ()});
        d.putIntArray("player", new int[] {player.getBlockX(), player.getBlockY(), player.getBlockZ()});
        List<Area> areas = b.areas();
        ListTag list = new ListTag();
        for (Area a : areas) {
            CompoundTag c = new CompoundTag();
            c.putIntArray("b", a.bounds());
            c.putString("name", BunkerActions.json(EnergySystem.roomName(b, a), reg));
            c.putString("status", a == b.coreArea ? "core" : a.manualOff ? "manual_off" : a.unpowered ? "unpowered" : "on");
            list.add(c);
        }
        d.put("areas", list);
        v.target = Math.floorMod(v.target, Math.max(1, areas.size()));
        d.putInt("target", v.target);
        d.putString("side", SIDES[Math.floorMod(v.side, SIDES.length)].getSerializedName());
        d.putIntArray("size", new int[] {v.sx, v.sy, v.sz});
        d.putIntArray("offset", new int[] {v.du, v.dv});
        d.putBoolean("sideView", v.sideView);

        d.putInt("maxH", BunkerManager.maxHorizontal(b));
        d.putInt("maxV", BunkerManager.maxVertical(b));
        d.putInt("rooms", b.rooms.size());
        d.putInt("roomLimit", BunkerUpgrades.maxRooms(b.level));
        d.putInt("mode", v.mode);
        if (v.mode == 1) {
            writeRooms(d, player, m, b, v, reg);
            if (v.message != null) {
                d.putString("msg", BunkerActions.json(v.message, reg));
                d.putBoolean("msgOk", v.messageOk);
            }
            PacketDistributor.sendToPlayer(player, new BunkerScreenPayload(v.pos, false, open, 0, d));
            return;
        }

        Area draft = draft(b, v);
        Component error = draft == null ? Component.translatable("message.bunker.planner.no_draft") : geometryError(m, b, draft);
        int roomLevel = roomItemLevel(player, level, b);
        d.putInt("roomItemLevel", roomLevel);
        if (draft != null) {
            d.putIntArray("draft", draft.bounds());
            d.putString("draftSize", draft.sizeText());
            d.putInt("draftFe", EnergySystem.areaCost(draft));
            int budget = BunkerManager.roomBudget(b, Math.max(1, roomLevel));
            d.putInt("used", draft.expansionUsed());
            d.putInt("budget", budget);
            if (error == null && draft.expansionUsed() > budget) {
                error = Component.translatable("message.bunker.size.budget", draft.expansionUsed(), budget);
            }
            if (error == null) {
                Plan plan = plan(level, b, draft, targetArea(b, v));
                d.putInt("metal", plan.metal());
                d.putInt("dig", plan.dig().size() + plan.door().size());
                d.putInt("metalHave", player.hasInfiniteMaterials() ? plan.metal() : BunkerStorage.count(player, level, b, metalTest()));
                if (plan.error() != null) error = plan.error();
            }
        }
        if (error == null && roomLevel == 0) error = Component.translatable("message.bunker.planner.no_room_item");
        if (error != null) d.putString("error", BunkerActions.json(error, reg));
        if (v.message != null) {
            d.putString("msg", BunkerActions.json(v.message, reg));
            d.putBoolean("msgOk", v.messageOk);
        }
        PacketDistributor.sendToPlayer(player, new BunkerScreenPayload(v.pos, false, open, 0, d));
    }
}
