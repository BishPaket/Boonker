package com.shardsofvoid.bunker.energy;

/** Типы энергоблоков мода (v3 3–8). Тип генератора — тип источника для лимита «один каждого типа» (4.2). */
public enum MachineKind {
    FURNACE_GENERATOR(true),
    SOLAR_PANEL(true),
    BATTERY(false),
    CELL_RACK(false),
    POWER_INLET(false),
    SUIT_STATION(false),
    AIR_FILTER_UNIT(false),
    WATER_PURIFIER(false);

    public final boolean generator;

    MachineKind(boolean generator) {
        this.generator = generator;
    }

    private static final MachineKind[] VALUES = values();

    public static MachineKind byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : POWER_INLET;
    }

    public String key() {
        return name().toLowerCase();
    }
}
