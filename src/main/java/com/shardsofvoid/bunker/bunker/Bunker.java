package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.energy.EnergyReport;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Бункер: область ядра, комнаты (каждая со своей областью), уровень, состояние, энергия и жизнеобеспечение.
 * Защитная зона, индексы и текущий баланс — временные, на диск не пишутся (ТЗ v3, 10.1).
 */
public final class Bunker {
    public enum State { NOT_READY, BUILDING, ACTIVE }

    public final UUID id;
    public UUID owner;
    public String ownerName;
    public final BlockPos core;
    public int level = 1;
    public State state = State.NOT_READY;
    public long createdAt;
    /** Область ядра (v3, 1.3). */
    public Area coreArea;
    /** Позиция блока комнаты → область. Порядок = приоритет: первые отключаются последними (v3, 3.5). */
    public final LinkedHashMap<BlockPos, Area> rooms = new LinkedHashMap<>();
    /** Блоки комнат в состоянии «Размещение» (v3, 1.4): в бункер не входят, лимит не занимают. */
    public final Set<BlockPos> pendingRooms = new LinkedHashSet<>();
    public final Set<UUID> trusted = new HashSet<>();
    /** v3, 2.3: «Запереть гермодвери». */
    public boolean doorsLocked;
    /** Улучшения расширений за незерит (доступны на ядре III): каждое даёт бюджет и предел размера по X/Z. */
    public int expansionTier;
    /** v3, 8.3: воздух, %. */
    public double air = 100.0;
    /** v3, 4.2 / 10.1: время подключения энергоблоков к сети бункера (позиция → игровое время). */
    public final Long2LongOpenHashMap connectedAt = new Long2LongOpenHashMap();

    // ---- временные данные: герметичность
    /** Ячейки заливки (защищённый объём); null, если бункер не активен. Отключённые комнаты отсекаются при запросе. */
    public LongOpenHashSet zone;
    public int shellPresent;
    public int shellExpected;
    public boolean sealed;
    public BunkerValidator.Outcome lastOutcome = BunkerValidator.Outcome.LEAK;
    public final List<BlockPos> missing = new ArrayList<>();
    /** Открываемые блоки оболочки (двери, люки, калитки) → открыт ли. */
    public final Long2BooleanOpenHashMap openables = new Long2BooleanOpenHashMap();
    /** Гермодвери и гермолюки → игровое время, с которого они открыты (шлюзовой режим, v3 2.2). */
    public final Map<Long, Long> doorOpenSince = new HashMap<>();
    public boolean awaitingRestore;
    public boolean breached;
    /** Защита снята из-за гермодвери, открытой дольше шлюзового времени. */
    public boolean doorBreach;
    public long lastValidated = Long.MIN_VALUE / 2;
    public List<ActivationConditions.Result> lastConditions = List.of();

    // ---- временные данные: энергия и жизнеобеспечение
    public EnergyReport energy = EnergyReport.EMPTY;
    public boolean stationShed;
    public boolean purifierShed;
    public boolean recyclerShed;
    public long lastRestore;
    public int playersInZone;
    public double airWearAccumulator;
    public boolean warnedAir;
    public boolean warnedFilter;
    /** Чистая вода бункера, mB (бак активной станции водоочистки), для HUD. */
    public int waterStock;
    public int waterCapacity;

    public Bunker(UUID id, UUID owner, String ownerName, BlockPos core, Area coreArea) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.core = core.immutable();
        this.coreArea = coreArea;
    }

    public boolean isActive() {
        return state == State.ACTIVE && zone != null;
    }

    /** Все области: ядро первым, затем комнаты по приоритету. */
    public List<Area> areas() {
        List<Area> list = new ArrayList<>(1 + rooms.size());
        list.add(coreArea);
        list.addAll(rooms.values());
        return list;
    }

    /** Комната включена и запитана (область ядра — всегда). */
    public boolean isAreaOn(Area a) {
        return a == coreArea || (!a.manualOff && !a.unpowered);
    }

    /** Область, которой принадлежит ячейка; включённые области проверяются первыми (проход между вкл./выкл. — к включённой). */
    public Area areaAt(int x, int y, int z) {
        Area fallback = null;
        for (Area a : areas()) {
            if (!a.contains(x, y, z)) continue;
            if (isAreaOn(a)) return a;
            if (fallback == null) fallback = a;
        }
        return fallback;
    }

    public int roomIndex(BlockPos roomPos) {
        int i = 1;
        for (BlockPos p : rooms.keySet()) {
            if (p.equals(roomPos)) return i;
            i++;
        }
        return -1;
    }

    // ---------------------------------------------------------------- NBT

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("format", 3);
        tag.putUUID("id", id);
        tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName == null ? "" : ownerName);
        tag.put("core", NbtUtils.writeBlockPos(core));
        tag.putInt("level", level);
        tag.putString("state", state.name());
        tag.putLong("createdAt", createdAt);
        tag.put("coreArea", coreArea.save());
        ListTag roomList = new ListTag();
        for (Area a : rooms.values()) roomList.add(a.save());
        tag.put("areas", roomList);
        ListTag pending = new ListTag();
        for (BlockPos p : pendingRooms) pending.add(NbtUtils.writeBlockPos(p));
        tag.put("pending", pending);
        ListTag trustList = new ListTag();
        for (UUID t : trusted) trustList.add(StringTag.valueOf(t.toString()));
        tag.put("trusted", trustList);
        tag.putBoolean("doorsLocked", doorsLocked);
        tag.putInt("expansionTier", expansionTier);
        tag.putDouble("air", air);
        CompoundTag conn = new CompoundTag();
        connectedAt.long2LongEntrySet().forEach(e -> conn.putLong(Long.toString(e.getLongKey()), e.getLongValue()));
        tag.put("connected", conn);
        return tag;
    }

    public static Bunker load(CompoundTag tag) {
        BlockPos core = NbtUtils.readBlockPos(tag, "core").orElse(BlockPos.ZERO);
        boolean v3 = tag.getInt("format") >= 3;
        Area coreArea = v3 && tag.contains("coreArea") ? Area.load(tag.getCompound("coreArea")) : Area.centered(core, core);
        coreArea.anchor = core;
        Bunker b = new Bunker(tag.getUUID("id"), tag.getUUID("owner"), tag.getString("ownerName"), core, coreArea);
        b.level = Math.max(1, Math.min(3, tag.getInt("level")));
        try {
            b.state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            b.state = State.NOT_READY;
        }
        // «Активен» восстанавливается только после успешной проверки.
        if (b.state == State.ACTIVE) {
            b.state = State.BUILDING;
            b.awaitingRestore = true;
        }
        b.createdAt = tag.getLong("createdAt");

        if (v3) {
            ListTag list = tag.getList("areas", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Area a = Area.load(list.getCompound(i));
                b.rooms.put(a.anchor, a);
            }
            ListTag pending = tag.getList("pending", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < pending.size(); i++) {
                int[] p = pending.getIntArray(i);
                if (p.length == 3) b.pendingRooms.add(new BlockPos(p[0], p[1], p[2]));
            }
            b.doorsLocked = tag.getBoolean("doorsLocked");
            b.expansionTier = tag.getInt("expansionTier");
            b.air = tag.contains("air") ? tag.getDouble("air") : 100.0;
            CompoundTag conn = tag.getCompound("connected");
            for (String key : conn.getAllKeys()) {
                try {
                    b.connectedAt.put(Long.parseLong(key), conn.getLong(key));
                } catch (NumberFormatException ignored) {
                }
            }
        } else {
            // v3, 10.4: перенос сохранений v2 — область ядра «По центру», комнаты с прежними кубами 5×5×5,
            // имена «Комната N», приоритет по порядку создания, все включены, воздух 100%.
            ListTag roomList = tag.getList("rooms", Tag.TAG_COMPOUND);
            for (int i = 0; i < roomList.size(); i++) {
                CompoundTag r = roomList.getCompound(i);
                NbtUtils.readBlockPos(r, "pos").ifPresent(p -> {
                    Area a = Area.centered(p.immutable(), p);
                    a.level = Math.max(1, Math.min(3, r.getInt("level")));
                    b.rooms.put(a.anchor, a);
                });
            }
            b.doorsLocked = BunkerConfig.SPEC.isLoaded() && BunkerConfig.DOOR_LOCKED_BY_DEFAULT.get();
        }
        ListTag trustList = tag.getList("trusted", Tag.TAG_STRING);
        for (int i = 0; i < trustList.size(); i++) {
            try {
                b.trusted.add(UUID.fromString(trustList.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return b;
    }
}
