package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.List;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.config.BunkerConfig.Adjacency;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Геометрия бункера (разделы 4.4, 5.4, 6.3–6.5): кубы ядра и комнат, ожидаемая оболочка,
 * проходные зоны общих граней, ограничивающий объём.
 */
public final class BunkerGeometry {
    public static final int CORE_HALF = 2; // куб 5×5×5

    /** Куб с центром center и полуразмером half (размер 2·half+1). */
    public record Cube(BlockPos center, int half, boolean isCore) {
        public int minX() { return center.getX() - half; }
        public int minY() { return center.getY() - half; }
        public int minZ() { return center.getZ() - half; }
        public int maxX() { return center.getX() + half; }
        public int maxY() { return center.getY() + half; }
        public int maxZ() { return center.getZ() + half; }

        public boolean contains(int x, int y, int z) {
            return x >= minX() && x <= maxX() && y >= minY() && y <= maxY() && z >= minZ() && z <= maxZ();
        }

        public boolean isShell(int x, int y, int z) {
            return contains(x, y, z) && (x == minX() || x == maxX() || y == minY() || y == maxY() || z == minZ() || z == maxZ());
        }

        public boolean isInterior(int x, int y, int z) {
            return x > minX() && x < maxX() && y > minY() && y < maxY() && z > minZ() && z < maxZ();
        }

        public boolean intersects(Cube o) {
            return minX() <= o.maxX() && maxX() >= o.minX() && minY() <= o.maxY() && maxY() >= o.minY()
                    && minZ() <= o.maxZ() && maxZ() >= o.minZ();
        }

        /** Толщина пересечения по самой тонкой оси (0 — не пересекаются). */
        public int minIntersectionThickness(Cube o) {
            if (!intersects(o)) return 0;
            int dx = Math.min(maxX(), o.maxX()) - Math.max(minX(), o.minX()) + 1;
            int dy = Math.min(maxY(), o.maxY()) - Math.max(minY(), o.minY()) + 1;
            int dz = Math.min(maxZ(), o.maxZ()) - Math.max(minZ(), o.minZ()) + 1;
            return Math.min(dx, Math.min(dy, dz));
        }
    }

    private BunkerGeometry() {}

    public static int roomHalf() {
        return (BunkerConfig.roomSize() - 1) / 2;
    }

    public static Cube coreCube(BlockPos core) {
        return new Cube(core, CORE_HALF, true);
    }

    public static Cube roomCube(BlockPos room) {
        return new Cube(room, roomHalf(), false);
    }

    public static List<Cube> cubes(Bunker bunker) {
        List<Cube> list = new ArrayList<>(1 + bunker.rooms.size());
        list.add(coreCube(bunker.core));
        for (BlockPos room : bunker.rooms.keySet()) list.add(roomCube(room));
        return list;
    }

    /** Расстояние между центрами соседних кубов по правилу смежности. */
    public static int adjacencyStep(Cube a, Cube b, Adjacency mode) {
        return a.half() + b.half() + (mode == Adjacency.DOUBLE_WALL ? 1 : 0);
    }

    /** Ось, по которой кубы смежны, или null. Центры совпадают по двум осям и отстоят на шаг по третьей. */
    public static Direction adjacentDirection(Cube from, Cube to, Adjacency mode) {
        int step = adjacencyStep(from, to, mode);
        int dx = to.center().getX() - from.center().getX();
        int dy = to.center().getY() - from.center().getY();
        int dz = to.center().getZ() - from.center().getZ();
        if (dy == 0 && dz == 0 && Math.abs(dx) == step) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (dx == 0 && dz == 0 && Math.abs(dy) == step) return dy > 0 ? Direction.UP : Direction.DOWN;
        if (dx == 0 && dy == 0 && Math.abs(dz) == step) return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        return null;
    }

    /**
     * Проходные ячейки: внутренняя часть общих граней смежных кубов (для куба 5 — 3×3).
     * В режиме DOUBLE_WALL — внутренние части обеих стоящих вплотную стен.
     */
    public static LongOpenHashSet passages(List<Cube> cubes, Adjacency mode) {
        LongOpenHashSet out = new LongOpenHashSet();
        for (int i = 0; i < cubes.size(); i++) {
            for (int j = i + 1; j < cubes.size(); j++) {
                Cube a = cubes.get(i);
                Cube b = cubes.get(j);
                Direction dir = adjacentDirection(a, b, mode);
                if (dir == null) continue;
                int inner = Math.min(a.half(), b.half()) - 1;
                addFace(out, a, dir, inner);
                if (mode == Adjacency.DOUBLE_WALL) addFace(out, b, dir.getOpposite(), inner);
            }
        }
        return out;
    }

    private static void addFace(LongOpenHashSet out, Cube cube, Direction dir, int inner) {
        BlockPos c = cube.center();
        int plane = cube.half();
        for (int u = -inner; u <= inner; u++) {
            for (int v = -inner; v <= inner; v++) {
                int x = c.getX(), y = c.getY(), z = c.getZ();
                switch (dir.getAxis()) {
                    case X -> { x += dir.getStepX() * plane; y += u; z += v; }
                    case Y -> { y += dir.getStepY() * plane; x += u; z += v; }
                    case Z -> { z += dir.getStepZ() * plane; x += u; y += v; }
                }
                out.add(BlockPos.asLong(x, y, z));
            }
        }
    }

    /** Ожидаемая оболочка: оболочки всех кубов без проходных зон. Порядок стабильный (для подсветки). */
    public static LongLinkedOpenHashSet expectedShell(List<Cube> cubes, Adjacency mode) {
        LongOpenHashSet pass = passages(cubes, mode);
        LongLinkedOpenHashSet out = new LongLinkedOpenHashSet();
        for (Cube cube : cubes) {
            for (int x = cube.minX(); x <= cube.maxX(); x++) {
                for (int y = cube.minY(); y <= cube.maxY(); y++) {
                    for (int z = cube.minZ(); z <= cube.maxZ(); z++) {
                        if (!cube.isShell(x, y, z)) continue;
                        long key = BlockPos.asLong(x, y, z);
                        if (pass.contains(key) || insideAnyInterior(cubes, x, y, z)) continue;
                        out.add(key);
                    }
                }
            }
        }
        return out;
    }

    private static boolean insideAnyInterior(List<Cube> cubes, int x, int y, int z) {
        for (Cube c : cubes) if (c.isInterior(x, y, z)) return true;
        return false;
    }

    /** Ограничивающий объём: объединение кубов плюс запас 1 блок. Возвращает {minX,minY,minZ,maxX,maxY,maxZ}. */
    public static int[] bounds(List<Cube> cubes, int margin) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (Cube c : cubes) {
            b[0] = Math.min(b[0], c.minX() - margin);
            b[1] = Math.min(b[1], c.minY() - margin);
            b[2] = Math.min(b[2], c.minZ() - margin);
            b[3] = Math.max(b[3], c.maxX() + margin);
            b[4] = Math.max(b[4], c.maxY() + margin);
            b[5] = Math.max(b[5], c.maxZ() + margin);
        }
        return b;
    }

    /** Центры допустимых новых комнат у каждой стороны каждого куба бункера (без проверки пересечений). */
    public static List<BlockPos> candidateRoomCenters(List<Cube> cubes, Adjacency mode) {
        List<BlockPos> out = new ArrayList<>();
        int roomHalf = roomHalf();
        for (Cube cube : cubes) {
            int step = cube.half() + roomHalf + (mode == Adjacency.DOUBLE_WALL ? 1 : 0);
            for (Direction d : Direction.values()) {
                BlockPos p = cube.center().relative(d, step);
                if (!out.contains(p)) out.add(p);
            }
        }
        return out;
    }
}
