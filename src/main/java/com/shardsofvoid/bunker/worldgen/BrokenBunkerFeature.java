package com.shardsofvoid.bunker.worldgen;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.BunkerMod;
import com.shardsofvoid.bunker.registry.ModBlocks;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Заброшенный бункер под землёй: 2–7 комнат разного размера из сломанного бункерного металла, со щелями и мусором,
 * проходами между комнатами и сундуками (от одного двойного у двухкомнатного до пяти, из них двух двойных, у семикомнатного).
 * С шансом 20% — радиотехнический стол SimpleRadio. Всё укладывается в 3×3 чанка вокруг точки генерации.
 */
public class BrokenBunkerFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, BunkerMod.id("chests/broken_bunker"));
    private static final int[] CHESTS = {0, 0, 1, 2, 3, 3, 4, 5};
    private static final int[] DOUBLES = {0, 0, 1, 1, 1, 1, 2, 2};

    public BrokenBunkerFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** Комната: внешние границы (оболочка включена). */
    private record Room(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean interior(int x, int y, int z) {
            return x > minX && x < maxX && y > minY && y < maxY && z > minZ && z < maxZ;
        }

        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        /** Пересечение, отличное от общей стены толщиной 1. */
        boolean clashes(Room o) {
            int ix = Math.min(maxX, o.maxX) - Math.max(minX, o.minX) + 1;
            int iy = Math.min(maxY, o.maxY) - Math.max(minY, o.minY) + 1;
            int iz = Math.min(maxZ, o.maxZ) - Math.max(minZ, o.minZ) + 1;
            if (ix <= 0 || iy <= 0 || iz <= 0) return false;
            return ix > 1 && iy > 1 && iz > 1;
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos origin = ctx.origin();
        // писать можно в 3×3 чанка вокруг чанка генерации; оставляем блок запаса
        int cx0 = (origin.getX() >> 4) * 16, cz0 = (origin.getZ() >> 4) * 16;
        int loX = cx0 - 15, hiX = cx0 + 30, loZ = cz0 - 15, hiZ = cz0 + 30;
        int floor = origin.getY();
        if (floor < level.getMinBuildHeight() + 6) return false;

        int want = 2 + r.nextInt(6);
        List<Room> rooms = new ArrayList<>();
        List<long[]> doors = new ArrayList<>(); // {x, y, z, axis}: проход 1×2
        Room first = room(origin.getX() - 2 - r.nextInt(2), floor, origin.getZ() - 2 - r.nextInt(2), r);
        if (!fits(first, loX, hiX, loZ, hiZ)) return false;
        rooms.add(first);
        for (int attempt = 0; rooms.size() < want && attempt < 60; attempt++) {
            Room parent = rooms.get(r.nextInt(rooms.size()));
            Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            int ix = 3 + r.nextInt(4), iy = 3 + r.nextInt(2), iz = 3 + r.nextInt(4);
            int ox = ix + 2, oy = iy + 2, oz = iz + 2;
            int minX, minZ;
            if (side.getAxis() == Direction.Axis.X) {
                minX = side == Direction.EAST ? parent.maxX : parent.minX - ox + 1;
                int lo = parent.minZ - oz + 4, hi = parent.maxZ - 3; // общая грань не меньше 4 по Z
                if (hi < lo) continue;
                minZ = lo + r.nextInt(hi - lo + 1);
            } else {
                minZ = side == Direction.SOUTH ? parent.maxZ : parent.minZ - oz + 1;
                int lo = parent.minX - ox + 4, hi = parent.maxX - 3;
                if (hi < lo) continue;
                minX = lo + r.nextInt(hi - lo + 1);
            }
            Room c = new Room(minX, parent.minY, minZ, minX + ox - 1, parent.minY + oy - 1, minZ + oz - 1);
            if (!fits(c, loX, hiX, loZ, hiZ)) continue;
            boolean ok = true;
            for (Room e : rooms) if (c.clashes(e)) ok = false;
            if (!ok) continue;
            // проход посередине общей грани, на уровне пола
            int y = parent.minY + 1;
            if (side.getAxis() == Direction.Axis.X) {
                int wallX = side == Direction.EAST ? parent.maxX : parent.minX;
                int z0 = Math.max(parent.minZ, c.minZ) + 1, z1 = Math.min(parent.maxZ, c.maxZ) - 1;
                doors.add(new long[] {wallX, y, (z0 + z1) / 2});
            } else {
                int wallZ = side == Direction.SOUTH ? parent.maxZ : parent.minZ;
                int x0 = Math.max(parent.minX, c.minX) + 1, x1 = Math.min(parent.maxX, c.maxX) - 1;
                doors.add(new long[] {(x0 + x1) / 2, y, wallZ});
            }
            rooms.add(c);
        }
        if (rooms.size() < 2) return false;

        // грунт должен быть сплошным: не в пещере, не в воде
        int air = 0, total = 0;
        for (Room rm : rooms) {
            for (int x = rm.minX; x <= rm.maxX; x += 2) {
                for (int y = rm.minY; y <= rm.maxY; y += 2) {
                    for (int z = rm.minZ; z <= rm.maxZ; z += 2) {
                        BlockState s = level.getBlockState(new BlockPos(x, y, z));
                        total++;
                        if (!s.getFluidState().isEmpty() || s.is(Blocks.BEDROCK)) return false;
                        if (s.isAir()) air++;
                    }
                }
            }
        }
        if (air * 100 > total * 12) return false;

        LongOpenHashSet doorCells = new LongOpenHashSet();
        for (long[] d : doors) {
            doorCells.add(BlockPos.asLong((int) d[0], (int) d[1], (int) d[2]));
            doorCells.add(BlockPos.asLong((int) d[0], (int) d[1] + 1, (int) d[2]));
        }

        BlockState broken = ModBlocks.BROKEN_BUNKER_METAL.get().defaultBlockState();
        for (Room rm : rooms) {
            for (int x = rm.minX; x <= rm.maxX; x++) {
                for (int y = rm.minY; y <= rm.maxY; y++) {
                    for (int z = rm.minZ; z <= rm.maxZ; z++) {
                        BlockPos p = new BlockPos(x, y, z);
                        boolean inside = false;
                        for (Room o : rooms) if (o.interior(x, y, z)) inside = true;
                        if (inside || doorCells.contains(p.asLong())) {
                            level.setBlock(p, Blocks.CAVE_AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                            continue;
                        }
                        float roll = r.nextFloat();
                        BlockState s = roll < 0.05F ? Blocks.CAVE_AIR.defaultBlockState()
                                : roll < 0.11F ? Blocks.COBBLESTONE.defaultBlockState()
                                : roll < 0.15F ? Blocks.GRAVEL.defaultBlockState()
                                : broken;
                        // дыры только в стенах, не в полу — и не там, где проход
                        if (s.isAir() && y == rm.minY) s = broken;
                        if (s.is(Blocks.GRAVEL) && y == rm.maxY) s = broken;
                        level.setBlock(p, s, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        // мусор на полу
        for (Room rm : rooms) {
            for (int x = rm.minX + 1; x < rm.maxX; x++) {
                for (int z = rm.minZ + 1; z < rm.maxZ; z++) {
                    float roll = r.nextFloat();
                    BlockPos p = new BlockPos(x, rm.minY + 1, z);
                    if (doorCells.contains(p.asLong()) || isDoorFront(p, doorCells)) continue;
                    if (roll < 0.04F) level.setBlock(p, Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_CLIENTS);
                    else if (roll < 0.07F) level.setBlock(p.above(r.nextInt(rm.maxY - rm.minY - 1)), Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }

        // сундуки
        int n = rooms.size();
        int doubles = DOUBLES[n], singles = CHESTS[n] - DOUBLES[n];
        LongOpenHashSet used = new LongOpenHashSet();
        int roomCursor = r.nextInt(n);
        for (int i = 0; i < doubles + singles; i++) {
            boolean dbl = i < doubles;
            for (int attempt = 0; attempt < 20; attempt++) {
                Room rm = rooms.get((roomCursor + i + attempt) % n);
                if (placeChest(level, r, rm, rooms, dbl, doorCells, used)) break;
            }
        }
        // радиотехнический стол SimpleRadio
        if (r.nextFloat() < 0.20F) {
            Block bench = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("simpleradio", "radiosmither")).orElse(null);
            if (bench != null) {
                for (int attempt = 0; attempt < 20; attempt++) {
                    Room rm = rooms.get(r.nextInt(n));
                    Spot spot = wallSpot(r, rm, rooms, false, doorCells, used);
                    if (spot == null) continue;
                    level.setBlock(spot.pos(), withFacing(bench.defaultBlockState(), spot.facing()), Block.UPDATE_CLIENTS);
                    used.add(spot.pos().asLong());
                    break;
                }
            }
        }
        return true;
    }

    private static Room room(int minX, int minY, int minZ, RandomSource r) {
        int ix = 3 + r.nextInt(4), iy = 3 + r.nextInt(2), iz = 3 + r.nextInt(4);
        return new Room(minX, minY, minZ, minX + ix + 1, minY + iy + 1, minZ + iz + 1);
    }

    private static boolean fits(Room rm, int loX, int hiX, int loZ, int hiZ) {
        return rm.minX >= loX && rm.maxX <= hiX && rm.minZ >= loZ && rm.maxZ <= hiZ;
    }

    private static boolean isDoorFront(BlockPos p, LongOpenHashSet doors) {
        for (Direction d : Direction.Plane.HORIZONTAL) if (doors.contains(p.relative(d).asLong())) return true;
        return false;
    }

    /** Место у стены на полу: pos — клетка, facing — куда смотрит (в комнату). */
    private record Spot(BlockPos pos, Direction facing, BlockPos second) {}

    @Nullable
    private static Spot wallSpot(RandomSource r, Room rm, List<Room> rooms, boolean dbl, LongOpenHashSet doors, LongOpenHashSet used) {
        Direction wall = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        int y = rm.minY + 1;
        int x, z;
        Direction along = wall.getClockWise();
        switch (wall) {
            case NORTH -> { z = rm.minZ + 1; x = rm.minX + 1 + r.nextInt(rm.maxX - rm.minX - 1); }
            case SOUTH -> { z = rm.maxZ - 1; x = rm.minX + 1 + r.nextInt(rm.maxX - rm.minX - 1); }
            case WEST -> { x = rm.minX + 1; z = rm.minZ + 1 + r.nextInt(rm.maxZ - rm.minZ - 1); }
            default -> { x = rm.maxX - 1; z = rm.minZ + 1 + r.nextInt(rm.maxZ - rm.minZ - 1); }
        }
        BlockPos p = new BlockPos(x, y, z);
        BlockPos second = p.relative(along);
        if (!free(p, rm, doors, used) || (dbl && (!rm.interior(second.getX(), second.getY(), second.getZ()) || !free(second, rm, doors, used)))) {
            return null;
        }
        // стена за спиной должна быть стеной, а не проходом в соседнюю комнату
        if (doors.contains(p.relative(wall).asLong()) || (dbl && doors.contains(second.relative(wall).asLong()))) return null;
        return new Spot(p, wall.getOpposite(), second);
    }

    private static boolean free(BlockPos p, Room rm, LongOpenHashSet doors, LongOpenHashSet used) {
        if (used.contains(p.asLong()) || isDoorFront(p, doors)) return false;
        return rm.interior(p.getX(), p.getY(), p.getZ());
    }

    private static boolean placeChest(WorldGenLevel level, RandomSource r, Room rm, List<Room> rooms, boolean dbl,
                                      LongOpenHashSet doors, LongOpenHashSet used) {
        Spot s = wallSpot(r, rm, rooms, dbl, doors, used);
        if (s == null) return false;
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, s.facing());
        if (dbl) {
            // у половины LEFT пара лежит по часовой от facing, у RIGHT — против (ChestBlock.getConnectedDirection)
            boolean posIsLeft = s.second().equals(s.pos().relative(s.facing().getClockWise()));
            level.setBlock(s.pos(), chest.setValue(ChestBlock.TYPE, posIsLeft ? ChestType.LEFT : ChestType.RIGHT), Block.UPDATE_CLIENTS);
            level.setBlock(s.second(), chest.setValue(ChestBlock.TYPE, posIsLeft ? ChestType.RIGHT : ChestType.LEFT), Block.UPDATE_CLIENTS);
            RandomizableContainer.setBlockEntityLootTable(level, r, s.second(), LOOT);
            used.add(s.second().asLong());
        } else {
            level.setBlock(s.pos(), chest, Block.UPDATE_CLIENTS);
        }
        RandomizableContainer.setBlockEntityLootTable(level, r, s.pos(), LOOT);
        used.add(s.pos().asLong());
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState withFacing(BlockState state, Direction facing) {
        Property<?> prop = state.getBlock().getStateDefinition().getProperty("facing");
        if (prop != null && prop.getPossibleValues().contains(facing)) {
            return state.setValue((Property) prop, (Comparable) facing);
        }
        return state;
    }
}
