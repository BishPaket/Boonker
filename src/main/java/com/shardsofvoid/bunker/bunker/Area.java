package com.shardsofvoid.bunker.bunker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/**
 * Область бункера (ТЗ v3, 1.1): прямоугольный объём с оболочкой толщиной 1 блок.
 * Границы хранятся по внешнему контуру (оболочка включена). У ядра одна область, у каждой комнаты своя.
 */
public final class Area {
    public static final int BASE_INNER = 3;

    /** Якорный блок: ядро или блок комнаты. */
    public BlockPos anchor;
    public int minX, minY, minZ, maxX, maxY, maxZ;
    /** Уровень комнаты (для области ядра не используется — уровень хранит бункер). */
    public int level = 1;
    /** Имя; пустая строка — имя по умолчанию («Центральная», «Комната N»). */
    public String name = "";
    /** Отключена вручную (v3, 6.2). */
    public boolean manualOff;

    // ---- временное
    /** Обесточена системой отключения нагрузки (v3, 3.5). */
    public boolean unpowered;

    public Area(BlockPos anchor, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.anchor = anchor.immutable();
        setBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public void setBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public void setBounds(int[] b) {
        setBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    public int[] bounds() {
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    public Area copy() {
        Area a = new Area(anchor, minX, minY, minZ, maxX, maxY, maxZ);
        a.level = level;
        a.name = name;
        a.manualOff = manualOff;
        a.unpowered = unpowered;
        return a;
    }

    /** Базовая область 3×3×3 с центром в pos (поведение v2, пресет «По центру»). */
    public static Area centered(BlockPos anchor, BlockPos center) {
        return new Area(anchor, center.getX() - 2, center.getY() - 2, center.getZ() - 2,
                center.getX() + 2, center.getY() + 2, center.getZ() + 2);
    }

    /** Пресет «В стене»: якорь — центр стены, область строится в сторону facing (к игроку). */
    public static Area inWall(BlockPos anchor, Direction facing) {
        return centered(anchor, anchor.relative(facing, 2));
    }

    // ---------------------------------------------------------------- размеры

    public int innerX() { return maxX - minX - 1; }
    public int innerY() { return maxY - minY - 1; }
    public int innerZ() { return maxZ - minZ - 1; }

    /** Внутренний объём V = X × Y × Z. */
    public int volume() {
        return Math.max(0, innerX()) * Math.max(0, innerY()) * Math.max(0, innerZ());
    }

    /** Использованный бюджет расширения: сумма сдвигов стен от базового 3×3×3. */
    public int expansionUsed() {
        return (innerX() - BASE_INNER) + (innerY() - BASE_INNER) + (innerZ() - BASE_INNER);
    }

    public String sizeText() {
        return innerX() + "×" + innerY() + "×" + innerZ();
    }

    // ---------------------------------------------------------------- геометрия

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(BlockPos p) {
        return contains(p.getX(), p.getY(), p.getZ());
    }

    public boolean isInterior(int x, int y, int z) {
        return x > minX && x < maxX && y > minY && y < maxY && z > minZ && z < maxZ;
    }

    public boolean isShell(int x, int y, int z) {
        return contains(x, y, z) && !isInterior(x, y, z);
    }

    /** Сколько координат точки лежат на границе: 0 — внутри, 1 — грань, 2 — ребро, 3 — угол. */
    public int boundaryAxes(int x, int y, int z) {
        int n = 0;
        if (x == minX || x == maxX) n++;
        if (y == minY || y == maxY) n++;
        if (z == minZ || z == maxZ) n++;
        return n;
    }

    /** v3 1.2: якорь стоит во внутреннем объёме или в грани оболочки, но не на ребре и не в углу. */
    public boolean isValidAnchor(BlockPos p) {
        return contains(p) && boundaryAxes(p.getX(), p.getY(), p.getZ()) <= 1;
    }

    public boolean intersects(Area o) {
        return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    /** Толщина пересечения по самой тонкой оси (0 — не пересекаются). */
    public int intersectionThickness(Area o) {
        if (!intersects(o)) return 0;
        int dx = Math.min(maxX, o.maxX) - Math.max(minX, o.minX) + 1;
        int dy = Math.min(maxY, o.maxY) - Math.max(minY, o.minY) + 1;
        int dz = Math.min(maxZ, o.maxZ) - Math.max(minZ, o.minZ) + 1;
        return Math.min(dx, Math.min(dy, dz));
    }

    /** Координата стены со стороны dir (по внешнему контуру). */
    public int wall(Direction dir) {
        return switch (dir) {
            case WEST -> minX;
            case EAST -> maxX;
            case DOWN -> minY;
            case UP -> maxY;
            case NORTH -> minZ;
            case SOUTH -> maxZ;
        };
    }

    /** Сдвинуть стену dir наружу (delta &gt; 0) или внутрь (delta &lt; 0). */
    public void moveWall(Direction dir, int delta) {
        switch (dir) {
            case WEST -> minX -= delta;
            case EAST -> maxX += delta;
            case DOWN -> minY -= delta;
            case UP -> maxY += delta;
            case NORTH -> minZ -= delta;
            case SOUTH -> maxZ += delta;
        }
    }

    public void translate(int dx, int dy, int dz) {
        minX += dx;
        maxX += dx;
        minY += dy;
        maxY += dy;
        minZ += dz;
        maxZ += dz;
    }

    public boolean sameBounds(Area o) {
        return minX == o.minX && minY == o.minY && minZ == o.minZ && maxX == o.maxX && maxY == o.maxY && maxZ == o.maxZ;
    }

    public boolean sameSize(Area o) {
        return innerX() == o.innerX() && innerY() == o.innerY() && innerZ() == o.innerZ();
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("anchor", NbtUtils.writeBlockPos(anchor));
        t.putIntArray("bounds", bounds());
        t.putInt("level", level);
        t.putString("name", name);
        t.putBoolean("manualOff", manualOff);
        return t;
    }

    public static Area load(CompoundTag t) {
        BlockPos anchor = NbtUtils.readBlockPos(t, "anchor").orElse(BlockPos.ZERO);
        int[] b = t.getIntArray("bounds");
        Area a = b.length == 6 ? new Area(anchor, b[0], b[1], b[2], b[3], b[4], b[5]) : centered(anchor, anchor);
        a.level = Math.max(1, Math.min(3, t.contains("level") ? t.getInt("level") : 1));
        a.name = t.getString("name");
        a.manualOff = t.getBoolean("manualOff");
        return a;
    }
}
