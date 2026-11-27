package com.shardsofvoid.bunker.safezone;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Безопасные зоны измерения (SavedData bunker_safe_zones): центр — блок безопасной зоны, полуразмеры по X/Y/Z.
 * Хранятся отдельно от блока, поэтому зона работает, даже когда чанк с блоком не загружен.
 */
public final class SafeZoneData extends SavedData {
    public static final String NAME = "bunker_safe_zones";
    public static final Factory<SafeZoneData> FACTORY = new Factory<>(SafeZoneData::new, SafeZoneData::load);

    public record Zone(BlockPos center, int rx, int ry, int rz) {
        public boolean contains(BlockPos p) {
            return Math.abs(p.getX() - center.getX()) <= rx && Math.abs(p.getY() - center.getY()) <= ry
                    && Math.abs(p.getZ() - center.getZ()) <= rz;
        }
    }

    private final Map<BlockPos, Zone> zones = new HashMap<>();

    public static SafeZoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static boolean isSafe(ServerLevel level, BlockPos pos) {
        SafeZoneData d = get(level);
        if (d.zones.isEmpty()) return false;
        for (Zone z : d.zones.values()) if (z.contains(pos)) return true;
        return false;
    }

    public void put(BlockPos center, int rx, int ry, int rz) {
        zones.put(center.immutable(), new Zone(center.immutable(), rx, ry, rz));
        setDirty();
    }

    public void remove(BlockPos center) {
        if (zones.remove(center) != null) setDirty();
    }

    public Zone zone(BlockPos center) {
        return zones.get(center);
    }

    public Iterable<Zone> all() {
        return zones.values();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Zone z : zones.values()) {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", z.center().asLong());
            c.putIntArray("r", new int[] {z.rx(), z.ry(), z.rz()});
            list.add(c);
        }
        tag.put("zones", list);
        return tag;
    }

    public static SafeZoneData load(CompoundTag tag, HolderLookup.Provider registries) {
        SafeZoneData d = new SafeZoneData();
        ListTag list = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            int[] r = c.getIntArray("r");
            if (r.length != 3) continue;
            BlockPos p = BlockPos.of(c.getLong("pos"));
            d.zones.put(p, new Zone(p, r[0], r[1], r[2]));
        }
        return d;
    }
}
