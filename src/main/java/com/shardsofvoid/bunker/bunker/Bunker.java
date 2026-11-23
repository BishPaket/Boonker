package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Бункер: ядро, комнаты, уровень и состояние. Защитная зона и индексы — временные, на диск не пишутся. */
public final class Bunker {
    public enum State { NOT_READY, BUILDING, ACTIVE }

    public final UUID id;
    public UUID owner;
    public String ownerName;
    public final BlockPos core;
    public int level = 1;
    public State state = State.NOT_READY;
    public long createdAt;
    /** Позиция блока комнаты → уровень комнаты. Порядок — порядок установки. */
    public final Map<BlockPos, Integer> rooms = new LinkedHashMap<>();
    public final Set<UUID> trusted = new HashSet<>();

    // ---- временные данные
    /** Ячейки защитной зоны; null, если бункер не активен. */
    public LongOpenHashSet zone;
    public int shellPresent;
    public int shellExpected;
    public boolean sealed;
    public BunkerValidator.Outcome lastOutcome = BunkerValidator.Outcome.LEAK;
    public final List<BlockPos> missing = new ArrayList<>();
    /** Позиции ожидаемой оболочки с блоками, у которых есть свойство OPEN (двери, люки, калитки) → открыт ли. */
    public final Long2BooleanOpenHashMap openables = new Long2BooleanOpenHashMap();
    /** После перезапуска «Активен» восстанавливается только после успешной проверки. */
    public boolean awaitingRestore;
    /** Герметичность нарушена после активного состояния — над ядром идут частицы. */
    public boolean breached;
    public long lastValidated = Long.MIN_VALUE / 2;
    public List<ActivationConditions.Result> lastConditions = List.of();

    public Bunker(UUID id, UUID owner, String ownerName, BlockPos core) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.core = core.immutable();
    }

    public boolean isActive() {
        return state == State.ACTIVE && zone != null;
    }

    public boolean canManage(UUID player) {
        return owner.equals(player);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName == null ? "" : ownerName);
        tag.put("core", NbtUtils.writeBlockPos(core));
        tag.putInt("level", level);
        tag.putString("state", state.name());
        tag.putLong("createdAt", createdAt);
        ListTag roomList = new ListTag();
        rooms.forEach((pos, lvl) -> {
            CompoundTag r = new CompoundTag();
            r.put("pos", NbtUtils.writeBlockPos(pos));
            r.putInt("level", lvl);
            roomList.add(r);
        });
        tag.put("rooms", roomList);
        ListTag trustList = new ListTag();
        for (UUID t : trusted) trustList.add(StringTag.valueOf(t.toString()));
        tag.put("trusted", trustList);
        return tag;
    }

    public static Bunker load(CompoundTag tag) {
        BlockPos core = NbtUtils.readBlockPos(tag, "core").orElse(BlockPos.ZERO);
        Bunker b = new Bunker(tag.getUUID("id"), tag.getUUID("owner"), tag.getString("ownerName"), core);
        b.level = Math.max(1, Math.min(3, tag.getInt("level")));
        try {
            b.state = State.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException e) {
            b.state = State.NOT_READY;
        }
        // Раздел 8.1: «Активен» восстанавливается только после успешной проверки.
        if (b.state == State.ACTIVE) {
            b.state = State.BUILDING;
            b.awaitingRestore = true;
        }
        b.createdAt = tag.getLong("createdAt");
        ListTag roomList = tag.getList("rooms", Tag.TAG_COMPOUND);
        for (int i = 0; i < roomList.size(); i++) {
            CompoundTag r = roomList.getCompound(i);
            NbtUtils.readBlockPos(r, "pos").ifPresent(p -> b.rooms.put(p.immutable(), Math.max(1, Math.min(3, r.getInt("level")))));
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
