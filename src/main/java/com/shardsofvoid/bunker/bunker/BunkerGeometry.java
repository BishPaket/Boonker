package com.shardsofvoid.bunker.bunker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.config.BunkerConfig.Adjacency;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Геометрия областей (ТЗ v3, 1.5): общая грань, проходные зоны, ожидаемая оболочка, связность.
 */
public final class BunkerGeometry {
    private BunkerGeometry() {}

    /**
     * Общая грань двух областей. dir — сторона области a, к которой примыкает b.
     * Прямоугольник [u0..u1]×[v0..v1] лежит в плоскостях planeA (стена a) и planeB (стена b; в SHARED_WALL совпадают).
     */
    public record Face(Direction dir, int planeA, int planeB, int u0, int u1, int v0, int v1) {
        public int width() { return u1 - u0 + 1; }
        public int height() { return v1 - v0 + 1; }
    }

    public static Adjacency mode() {
        return BunkerConfig.ROOM_ADJACENCY.get();
    }

    public static int minFace() {
        return BunkerConfig.ROOM_MIN_SHARED_FACE.get();
    }

    /** Общая грань a и b по правилу v3 1.5 или null, если области не смежны. */
    public static Face sharedFace(Area a, Area b, Adjacency mode) {
        int gap = mode == Adjacency.DOUBLE_WALL ? 1 : 0;
        // X
        if (a.maxX + gap == b.minX || b.maxX + gap == a.minX) {
            Face f = face(a.maxX + gap == b.minX ? Direction.EAST : Direction.WEST, a, b,
                    Math.max(a.minY, b.minY), Math.min(a.maxY, b.maxY), Math.max(a.minZ, b.minZ), Math.min(a.maxZ, b.maxZ));
            if (f != null) return f;
        }
        // Y
        if (a.maxY + gap == b.minY || b.maxY + gap == a.minY) {
            Face f = face(a.maxY + gap == b.minY ? Direction.UP : Direction.DOWN, a, b,
                    Math.max(a.minX, b.minX), Math.min(a.maxX, b.maxX), Math.max(a.minZ, b.minZ), Math.min(a.maxZ, b.maxZ));
            if (f != null) return f;
        }
        // Z
        if (a.maxZ + gap == b.minZ || b.maxZ + gap == a.minZ) {
            return face(a.maxZ + gap == b.minZ ? Direction.SOUTH : Direction.NORTH, a, b,
                    Math.max(a.minX, b.minX), Math.min(a.maxX, b.maxX), Math.max(a.minY, b.minY), Math.min(a.maxY, b.maxY));
        }
        return null;
    }

    private static Face face(Direction dir, Area a, Area b, int u0, int u1, int v0, int v1) {
        if (u1 - u0 + 1 <= 0 || v1 - v0 + 1 <= 0) return null;
        return new Face(dir, a.wall(dir), b.wall(dir.getOpposite()), u0, u1, v0, v1);
    }

    /** Смежны ли области: общая грань не меньше room.minSharedFace × room.minSharedFace. */
    public static boolean adjacent(Area a, Area b) {
        Face f = sharedFace(a, b, mode());
        int min = minFace();
        return f != null && f.width() >= min && f.height() >= min;
    }

    /** Есть ли у области a общая стена со стороны dir с какой-либо из others. */
    public static boolean isSharedWall(Area a, Direction dir, List<Area> others) {
        for (Area o : others) {
            if (o == a) continue;
            Face f = sharedFace(a, o, mode());
            if (f != null && f.dir() == dir && f.width() >= minFace() && f.height() >= minFace()) return true;
        }
        return false;
    }

    /** Проходные зоны: общие грани без периметра (в DOUBLE_WALL — в обеих стенах). */
    public static LongOpenHashSet passages(List<Area> areas) {
        Adjacency mode = mode();
        int min = minFace();
        LongOpenHashSet out = new LongOpenHashSet();
        for (int i = 0; i < areas.size(); i++) {
            for (int j = i + 1; j < areas.size(); j++) {
                Face f = sharedFace(areas.get(i), areas.get(j), mode);
                if (f == null || f.width() < min || f.height() < min) continue;
                addPlane(out, f, f.planeA());
                if (mode == Adjacency.DOUBLE_WALL) addPlane(out, f, f.planeB());
            }
        }
        return out;
    }

    private static void addPlane(LongOpenHashSet out, Face f, int plane) {
        for (int u = f.u0() + 1; u <= f.u1() - 1; u++) {
            for (int v = f.v0() + 1; v <= f.v1() - 1; v++) {
                out.add(switch (f.dir().getAxis()) {
                    case X -> BlockPos.asLong(plane, u, v);
                    case Y -> BlockPos.asLong(u, plane, v);
                    case Z -> BlockPos.asLong(u, v, plane);
                });
            }
        }
    }

    /** Ожидаемая оболочка (v3 1.5): объединение оболочек областей без проходных зон и без чужих внутренних объёмов. */
    public static LongLinkedOpenHashSet expectedShell(List<Area> areas) {
        LongOpenHashSet pass = passages(areas);
        LongLinkedOpenHashSet out = new LongLinkedOpenHashSet();
        for (Area a : areas) {
            for (int x = a.minX; x <= a.maxX; x++) {
                for (int y = a.minY; y <= a.maxY; y++) {
                    for (int z = a.minZ; z <= a.maxZ; z++) {
                        if (a.isInterior(x, y, z)) continue;
                        long key = BlockPos.asLong(x, y, z);
                        if (pass.contains(key) || insideAnyInterior(areas, x, y, z)) continue;
                        out.add(key);
                    }
                }
            }
        }
        return out;
    }

    private static boolean insideAnyInterior(List<Area> areas, int x, int y, int z) {
        for (Area a : areas) if (a.isInterior(x, y, z)) return true;
        return false;
    }

    /** Ограничивающий объём с запасом: {minX,minY,minZ,maxX,maxY,maxZ}. */
    public static int[] bounds(List<Area> areas, int margin) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (Area a : areas) {
            b[0] = Math.min(b[0], a.minX - margin);
            b[1] = Math.min(b[1], a.minY - margin);
            b[2] = Math.min(b[2], a.minZ - margin);
            b[3] = Math.max(b[3], a.maxX + margin);
            b[4] = Math.max(b[4], a.maxY + margin);
            b[5] = Math.max(b[5], a.maxZ + margin);
        }
        return b;
    }

    /** Все комнаты связаны с областью ядра цепочкой смежных областей (areas.get(0) — ядро). */
    public static boolean connected(List<Area> areas) {
        if (areas.size() <= 1) return true;
        boolean[] seen = new boolean[areas.size()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        seen[0] = true;
        queue.add(0);
        int count = 1;
        while (!queue.isEmpty()) {
            int i = queue.poll();
            for (int j = 0; j < areas.size(); j++) {
                if (seen[j] || !adjacent(areas.get(i), areas.get(j))) continue;
                seen[j] = true;
                count++;
                queue.add(j);
            }
        }
        return count == areas.size();
    }

    /** Пересечения с областями своего бункера допустимы только толщиной в одну стену. */
    public static boolean overlapsOwn(Area a, List<Area> others) {
        for (Area o : others) {
            if (o == a) continue;
            int t = a.intersectionThickness(o);
            if (t == 0) continue;
            if (mode() == Adjacency.SHARED_WALL && t == 1) continue;
            return true;
        }
        return false;
    }

    /** Варианты области базового размера, в которых pos — допустимый якорь (v3 1.2, 1.4). */
    public static List<Area> baseCandidates(BlockPos pos) {
        List<Area> out = new ArrayList<>();
        for (int dx = 0; dx <= 4; dx++) {
            for (int dy = 0; dy <= 4; dy++) {
                for (int dz = 0; dz <= 4; dz++) {
                    int minX = pos.getX() - dx, minY = pos.getY() - dy, minZ = pos.getZ() - dz;
                    Area a = new Area(pos, minX, minY, minZ, minX + 4, minY + 4, minZ + 4);
                    if (a.isValidAnchor(pos)) out.add(a);
                }
            }
        }
        return out;
    }
}
