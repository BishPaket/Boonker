package com.shardsofvoid.bunker.energy;

/** Состояние энергоблока для интерфейса и индикатора (v3 6.1, 8.7). */
public enum MachineStatus {
    NO_LINK,        // «Нет связи» — не подключён ни к одному бункеру
    WORKING,        // «Работает»
    IDLE,           // «Ожидание»
    NO_FUEL,        // «Нет топлива»
    NIGHT,          // «Ночь»
    NO_SKY,         // «Нет неба»
    EXCESS,         // «Лишний» — лимит генераторов/хранилищ этого типа
    NO_POWER,       // «Нет питания»
    SHED,           // «Обесточено» — отключено при нехватке энергии
    NO_FILTER,      // «Нет фильтра»
    INTAKE_CLOSED,  // «Воздухозабор закрыт»
    NO_WATER,       // «Нет сырой воды»
    FULL,           // «Бак полон»
    OUTSIDE,        // «Вне защитной зоны»
    ROOM_OFF,       // «Комната отключена»
    INACTIVE,       // «Бункер не активен»
    NO_INPUT,       // «Нет сырья»
    OUTPUT_FULL;    // «Выход заполнен»

    private static final MachineStatus[] VALUES = values();

    public static MachineStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NO_LINK;
    }

    public String key() {
        return name().toLowerCase();
    }
}
