package com.shardsofvoid.bunker.energy;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;

/** Снимок энергобаланса и жизнеобеспечения бункера за последний интервал (для вкладок интерфейса и HUD). */
public final class EnergyReport {
    public static final EnergyReport EMPTY = new EnergyReport();

    /** Источник: kind = "core", "foreign" или ключ MachineKind. */
    public record Source(String kind, BlockPos pos, int rate, String status, int level) {}

    /** Потребитель: area = true — область (name — имя или "", index — номер комнаты, 0 — ядро). */
    public record Consumer(boolean area, String name, int index, BlockPos pos, String size, int rate, String status) {}

    public record Storage(String kind, BlockPos pos, int stored, int capacity, String status) {}

    public int production;
    public int consumption;
    public long stored;
    public long capacity;
    public final List<Source> sources = new ArrayList<>();
    public final List<Consumer> consumers = new ArrayList<>();
    public final List<Storage> storages = new ArrayList<>();
    public boolean cableTooBig;

    // жизнеобеспечение
    public boolean lifeSupport;
    public double air = 100;
    public int players;
    public int water;
    public int waterCapacity;
    public int rawWater;
    public String filterUnitStatus = "";
    public int filterRemaining;
    public int filterMax;
    public String purifierStatus = "";
    public int cartridgeRemaining;
    public int cartridgeMax;

    public int balance() {
        return production - consumption;
    }

    /** Прогноз: ticks &gt; 0 — до полного заряда, &lt; 0 — запаса хватит, 0 — нет прогноза. */
    public long forecastTicks() {
        int net = balance();
        if (net < 0 && stored > 0) return -(stored / -net);
        if (net > 0 && stored < capacity) return (capacity - stored) / net;
        return 0;
    }

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putInt("production", production);
        t.putInt("consumption", consumption);
        t.putLong("stored", stored);
        t.putLong("capacity", capacity);
        t.putLong("forecast", forecastTicks());
        t.putBoolean("cableTooBig", cableTooBig);
        ListTag src = new ListTag();
        for (Source s : sources) {
            CompoundTag c = new CompoundTag();
            c.putString("kind", s.kind());
            c.put("pos", NbtUtils.writeBlockPos(s.pos()));
            c.putInt("rate", s.rate());
            c.putString("status", s.status());
            c.putInt("level", s.level());
            src.add(c);
        }
        t.put("sources", src);
        ListTag con = new ListTag();
        for (Consumer s : consumers) {
            CompoundTag c = new CompoundTag();
            c.putBoolean("area", s.area());
            c.putString("name", s.name());
            c.putInt("index", s.index());
            c.put("pos", NbtUtils.writeBlockPos(s.pos()));
            c.putString("size", s.size());
            c.putInt("rate", s.rate());
            c.putString("status", s.status());
            con.add(c);
        }
        t.put("consumers", con);
        ListTag sto = new ListTag();
        for (Storage s : storages) {
            CompoundTag c = new CompoundTag();
            c.putString("kind", s.kind());
            c.put("pos", NbtUtils.writeBlockPos(s.pos()));
            c.putInt("stored", s.stored());
            c.putInt("capacity", s.capacity());
            c.putString("status", s.status());
            sto.add(c);
        }
        t.put("storages", sto);
        return t;
    }

    public CompoundTag lifeTag() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("enabled", lifeSupport);
        t.putDouble("air", air);
        t.putInt("players", players);
        t.putInt("water", water);
        t.putInt("waterCapacity", waterCapacity);
        t.putInt("raw", rawWater);
        t.putString("fvu", filterUnitStatus);
        t.putInt("filter", filterRemaining);
        t.putInt("filterMax", filterMax);
        t.putString("purifier", purifierStatus);
        t.putInt("cartridge", cartridgeRemaining);
        t.putInt("cartridgeMax", cartridgeMax);
        return t;
    }
}
