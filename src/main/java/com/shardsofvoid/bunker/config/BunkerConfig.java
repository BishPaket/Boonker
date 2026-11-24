package com.shardsofvoid.bunker.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Серверный конфиг (serverconfig/bunker-server.toml). Синхронизируется клиентам при входе.
 * Ключи повторяют раздел 8.3 ТЗ; дополнительные ключи помечены в комментариях.
 */
public final class BunkerConfig {
    public static final ModConfigSpec SPEC;

    // radiation
    public static final ModConfigSpec.BooleanValue RADIATION_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RADIATION_DIMENSIONS;
    public static final ModConfigSpec.DoubleValue RADIATION_BASE_RATE;
    public static final ModConfigSpec.IntValue RADIATION_CHECK_INTERVAL;
    public static final ModConfigSpec.BooleanValue RADIATION_RESET_ON_DEATH;
    public static final ModConfigSpec.BooleanValue RADIATION_DECAY_IN_BUNKER;
    public static final ModConfigSpec.DoubleValue RADIATION_BUNKER_DECAY_RATE;
    public static final ModConfigSpec.BooleanValue RADIATION_GEIGER_SOUND;
    public static final ModConfigSpec.DoubleValue RADIATION_MAX_DOSE;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> RADIATION_STAGE_THRESHOLDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RADIATION_DEPTH_MULTIPLIERS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STAGE1_EFFECTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STAGE2_EFFECTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STAGE3_EFFECTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STAGE4_EFFECTS;
    public static final ModConfigSpec.IntValue STAGE3_NAUSEA_INTERVAL;
    public static final ModConfigSpec.IntValue STAGE3_NAUSEA_DURATION;
    public static final ModConfigSpec.DoubleValue STAGE4_DAMAGE;
    public static final ModConfigSpec.IntValue STAGE4_DAMAGE_INTERVAL;
    public static final ModConfigSpec.DoubleValue WEATHER_RAIN_MULT;
    public static final ModConfigSpec.DoubleValue WEATHER_THUNDER_MULT;
    public static final ModConfigSpec.DoubleValue WEATHER_ACID_RAIN_MULT;
    public static final ModConfigSpec.BooleanValue WEATHER_REQUIRE_EXPOSURE;
    public static final ModConfigSpec.BooleanValue WASTELANDS_OVERRIDE;

    // suit
    public static final ModConfigSpec.DoubleValue SUIT_MAX_PROTECTION;
    public static final ModConfigSpec.BooleanValue SUIT_DURABILITY_LOSS;
    public static final ModConfigSpec.IntValue SUIT_WEAR_INTERVAL;
    public static final ModConfigSpec.DoubleValue SUIT_SHARE_HELMET;
    public static final ModConfigSpec.DoubleValue SUIT_SHARE_CHEST;
    public static final ModConfigSpec.DoubleValue SUIT_SHARE_LEGS;
    public static final ModConfigSpec.DoubleValue SUIT_SHARE_BOOTS;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> SUIT_LEVEL_COEFFICIENTS;

    // bunker
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BUNKER_DIMENSIONS;
    public static final ModConfigSpec.IntValue BUNKER_CORE_MAX_Y;
    public static final ModConfigSpec.BooleanValue BUNKER_CORE_REQUIRE_UNDERGROUND;
    public static final ModConfigSpec.BooleanValue BUNKER_CORE_KEEP_ON_DEATH;
    public static final ModConfigSpec.IntValue BUNKER_MAX_PER_PLAYER;
    public static final ModConfigSpec.IntValue BUNKER_VALIDATION_MAX_CELLS;
    public static final ModConfigSpec.IntValue BUNKER_VALIDATION_INTERVAL;
    public static final ModConfigSpec.IntValue BUNKER_VALIDATIONS_PER_TICK;
    public static final ModConfigSpec.IntValue BUNKER_HIGHLIGHT_SECONDS;

    // bunkerMetal
    public static final ModConfigSpec.BooleanValue BUNKER_METAL_OWNER_ONLY_BREAK;
    public static final ModConfigSpec.BooleanValue BUNKER_METAL_BLOCK_FAKE_PLAYERS;

    // room
    public static final ModConfigSpec.IntValue ROOM_MIN_SHARED_FACE;
    public static final ModConfigSpec.EnumValue<Adjacency> ROOM_ADJACENCY;
    public static final ModConfigSpec.BooleanValue ROOM_BIND_REQUIRES_ACTIVE;

    // upgrade
    public static final ModConfigSpec.BooleanValue UPGRADE_REQUIRES_ACTIVE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends Integer>> EXPANSION_TIER_COST;
    public static final ModConfigSpec.ConfigValue<String> EXPANSION_TIER_ITEM;
    public static final ModConfigSpec.IntValue EXPANSION_TIER_BUDGET;
    public static final ModConfigSpec.IntValue EXPANSION_TIER_HORIZONTAL;

    // v3: area
    public static final ModConfigSpec.IntValue AREA_MAX_HORIZONTAL;
    public static final ModConfigSpec.IntValue AREA_MAX_VERTICAL;

    // v3: door
    public static final ModConfigSpec.IntValue DOOR_GRACE_SECONDS;
    public static final ModConfigSpec.IntValue DOOR_AUTO_CLOSE_SECONDS;
    public static final ModConfigSpec.BooleanValue DOOR_LOCKED_BY_DEFAULT;

    // v3: energy
    public static final ModConfigSpec.IntValue ENERGY_INTERVAL;
    public static final ModConfigSpec.DoubleValue ENERGY_AREA_BASE;
    public static final ModConfigSpec.DoubleValue ENERGY_AREA_PER_CELL;
    public static final ModConfigSpec.IntValue ENERGY_RESTORE_DELAY;
    public static final ModConfigSpec.IntValue ENERGY_MAX_PER_TYPE;
    public static final ModConfigSpec.IntValue ENERGY_MAX_INLETS;
    public static final ModConfigSpec.IntValue ENERGY_MAX_CABLE_NETWORK;
    public static final ModConfigSpec.BooleanValue ENERGY_ACCEPT_FOREIGN;
    public static final ModConfigSpec.IntValue ENERGY_FOREIGN_MAX_RATE;
    public static final ModConfigSpec.BooleanValue ENERGY_SURFACE_TORNADO_IMMUNE;
    public static final ModConfigSpec.IntValue ENERGY_FVU_BASE;
    public static final ModConfigSpec.IntValue ENERGY_FVU_CELLS_PER_FE;
    public static final ModConfigSpec.IntValue ENERGY_PURIFIER;
    public static final ModConfigSpec.IntValue ENERGY_RECYCLER;
    public static final ModConfigSpec.ConfigValue<String> RECYCLER_INPUT;
    public static final ModConfigSpec.DoubleValue RECYCLER_CHANCE;
    public static final ModConfigSpec.IntValue RECYCLER_SECONDS;
    public static final ModConfigSpec.IntValue RECYCLER_FAIL_MIN;
    public static final ModConfigSpec.IntValue RECYCLER_FAIL_MAX;
    public static final ModConfigSpec.IntValue ENERGY_STATION_PER_PART;

    // v3: generators and storage
    public static final ModConfigSpec.IntValue GEN_FURNACE_RATE;
    public static final ModConfigSpec.BooleanValue GEN_FURNACE_INDOOR_PENALTY;
    public static final ModConfigSpec.IntValue GEN_SOLAR_PEAK;
    public static final ModConfigSpec.DoubleValue GEN_SOLAR_RAIN;
    public static final ModConfigSpec.DoubleValue GEN_SOLAR_THUNDER;
    public static final ModConfigSpec.IntValue BATTERY_CAPACITY;
    public static final ModConfigSpec.IntValue BATTERY_RATE;
    public static final ModConfigSpec.IntValue CELL_CAPACITY;
    public static final ModConfigSpec.IntValue RACK_RATE_PER_CELL;

    // v3: station
    public static final ModConfigSpec.IntValue STATION_MAX_PER_BUNKER;
    public static final ModConfigSpec.ConfigValue<String> STATION_MATERIAL;
    public static final ModConfigSpec.BooleanValue SUIT_ANVIL_REPAIR;

    // v3: life support
    public static final ModConfigSpec.BooleanValue LIFE_SUPPORT_ENABLED;
    public static final ModConfigSpec.BooleanValue FILTER_REQUIRE_OPEN_INTAKE;
    public static final ModConfigSpec.IntValue FILTER_AIR_RESOURCE;
    public static final ModConfigSpec.IntValue FILTER_WATER_RESOURCE;
    public static final ModConfigSpec.DoubleValue AIR_RESTORE_PER_MIN;
    public static final ModConfigSpec.DoubleValue AIR_DRAIN_PER_PLAYER_MIN;
    public static final ModConfigSpec.IntValue WATER_PURIFY_SECONDS;
    public static final ModConfigSpec.ConfigValue<String> WATER_BOTTLE_ITEM;

    public enum Adjacency { SHARED_WALL, DOUBLE_WALL }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("radiation");
        RADIATION_ENABLED = b.comment("Радиация включена во всём мире. Меняется командами /radiation enable|disable.")
                .define("enabled", true);
        RADIATION_DIMENSIONS = b.comment("Измерения, где действует радиация.")
                .defineListAllowEmpty("dimensions", List.of("minecraft:overworld"), () -> "minecraft:overworld", o -> o instanceof String);
        RADIATION_BASE_RATE = b.comment("Базовая скорость набора дозы, ед./с. Меняется командой /radiation rate.")
                .defineInRange("baseRate", 1.0, 0.0, 1000.0);
        RADIATION_CHECK_INTERVAL = b.comment("Период серверной проверки игрока, тиков.")
                .defineInRange("checkInterval", 20, 1, 200);
        RADIATION_RESET_ON_DEATH = b.comment("Сбрасывать дозу при смерти.")
                .define("resetOnDeath", true);
        RADIATION_DECAY_IN_BUNKER = b.comment("Спад дозы внутри активного бункера.")
                .define("decayInBunker", false);
        RADIATION_BUNKER_DECAY_RATE = b.comment("[доп.] Скорость спада в бункере, ед./с (если decayInBunker = true).")
                .defineInRange("bunkerDecayRate", 0.5, 0.0, 1000.0);
        RADIATION_GEIGER_SOUND = b.comment("Звук счётчика Гейгера на клиенте.")
                .define("geigerSound", true);
        RADIATION_MAX_DOSE = b.comment("[доп.] Максимум дозы. 100 совпадает со шкалой Wastelands.")
                .defineInRange("maxDose", 100.0, 1.0, 100000.0);
        RADIATION_STAGE_THRESHOLDS = b.comment("[доп.] Пороги стадий I–IV в долях от максимума.")
                .defineList("stageThresholds", List.of(0.25, 0.5, 0.75, 1.0), () -> 0.5, o -> o instanceof Double d && d >= 0 && d <= 1);
        RADIATION_DEPTH_MULTIPLIERS = b.comment(
                        "Таблица depthMult: строки \"minY..maxY=множитель\", например \"-64..0=1.5\".",
                        "Для высоты вне всех диапазонов множитель 1.0. Пустой список = одинаково везде.")
                .defineListAllowEmpty("depthMultipliers", List.of(), () -> "-64..0=1.0", o -> o instanceof String);
        b.comment("Эффекты стадий: строки \"id_эффекта уровень\", уровень с нуля (0 = I).");
        STAGE1_EFFECTS = b.defineListAllowEmpty("stage1Effects", List.of("minecraft:hunger 0"), () -> "minecraft:hunger 0", o -> o instanceof String);
        STAGE2_EFFECTS = b.defineListAllowEmpty("stage2Effects", List.of("minecraft:hunger 0", "minecraft:weakness 0"), () -> "minecraft:hunger 0", o -> o instanceof String);
        STAGE3_EFFECTS = b.defineListAllowEmpty("stage3Effects", List.of("minecraft:hunger 0", "minecraft:weakness 0", "minecraft:slowness 0"), () -> "minecraft:hunger 0", o -> o instanceof String);
        STAGE4_EFFECTS = b.defineListAllowEmpty("stage4Effects", List.of("minecraft:hunger 0", "minecraft:weakness 0", "minecraft:slowness 0"), () -> "minecraft:hunger 0", o -> o instanceof String);
        STAGE3_NAUSEA_INTERVAL = b.comment("Стадии III–IV: период приступа тошноты, секунд (0 = выкл.).")
                .defineInRange("stage3NauseaInterval", 45, 0, 3600);
        STAGE3_NAUSEA_DURATION = b.comment("Длительность приступа тошноты, секунд.")
                .defineInRange("stage3NauseaDuration", 8, 1, 600);
        STAGE4_DAMAGE = b.comment("Стадия IV: радиационный урон за удар.")
                .defineInRange("stage4Damage", 2.0, 0.0, 1000.0);
        STAGE4_DAMAGE_INTERVAL = b.comment("Стадия IV: период урона, секунд.")
                .defineInRange("stage4DamageInterval", 10, 1, 3600);

        b.push("weather");
        WEATHER_RAIN_MULT = b.comment("Множитель за обычный дождь (1.0 = без влияния).")
                .defineInRange("rainMultiplier", 1.0, 0.0, 100.0);
        WEATHER_THUNDER_MULT = b.comment("Множитель за грозу.")
                .defineInRange("thunderMultiplier", 1.0, 0.0, 100.0);
        WEATHER_ACID_RAIN_MULT = b.comment("Множитель за кислотный дождь Better Weather (если мод установлен).")
                .defineInRange("acidRainMultiplier", 1.0, 0.0, 100.0);
        WEATHER_REQUIRE_EXPOSURE = b.comment("Погодный множитель действует, только если на игрока падает дождь (открытое небо над ним).")
                .define("requireExposure", true);
        b.pop();

        WASTELANDS_OVERRIDE = b.comment(
                        "Отключить собственное начисление дозы Wastelands и вести дозу этим модом.",
                        "Шкала Wastelands (Rad-Away, счётчик Гейгера, команды) зеркалится в нашу дозу.")
                .define("wastelandsOverride", true);
        b.pop();

        b.push("suit");
        SUIT_MAX_PROTECTION = b.comment("Верхняя граница защиты костюма (0–1).")
                .defineInRange("maxProtection", 0.9, 0.0, 1.0);
        SUIT_DURABILITY_LOSS = b.comment("Износ костюма под радиацией.")
                .define("durabilityLoss", true);
        SUIT_WEAR_INTERVAL = b.comment("[доп.] Период износа, секунд (1 ед. прочности каждой надетой части).")
                .defineInRange("wearInterval", 30, 1, 3600);
        SUIT_SHARE_HELMET = b.comment("[доп.] Доли защиты частей.").defineInRange("shareHelmet", 0.25, 0.0, 1.0);
        SUIT_SHARE_CHEST = b.defineInRange("shareChestplate", 0.40, 0.0, 1.0);
        SUIT_SHARE_LEGS = b.defineInRange("shareLeggings", 0.20, 0.0, 1.0);
        SUIT_SHARE_BOOTS = b.defineInRange("shareBoots", 0.15, 0.0, 1.0);
        SUIT_LEVEL_COEFFICIENTS = b.comment("[доп.] Коэффициенты уровней I, II, III.")
                .defineList("levelCoefficients", List.of(0.5, 0.75, 0.9), () -> 0.5, o -> o instanceof Double d && d >= 0 && d <= 1);
        b.pop();

        b.push("bunker");
        BUNKER_DIMENSIONS = b.comment("Измерения, где бункер можно активировать.")
                .defineListAllowEmpty("dimensions", List.of("minecraft:overworld"), () -> "minecraft:overworld", o -> o instanceof String);
        BUNKER_MAX_PER_PLAYER = b.comment("Максимум бункеров на игрока.")
                .defineInRange("maxPerPlayer", 1, 1, 100);
        BUNKER_HIGHLIGHT_SECONDS = b.comment("[доп.] Сколько секунд держится подсветка границ.")
                .defineInRange("highlightSeconds", 60, 5, 600);
        b.push("core");
        BUNKER_CORE_MAX_Y = b.comment("Ядро активируется только на этой высоте и ниже.")
                .defineInRange("maxY", 30, -64, 320);
        BUNKER_CORE_REQUIRE_UNDERGROUND = b.comment("Над ядром не должно быть открытого неба.")
                .define("requireUnderground", true);
        BUNKER_CORE_KEEP_ON_DEATH = b.comment("Предмет ядра остаётся у игрока после смерти.")
                .define("keepOnDeath", true);
        b.pop();
        b.push("validation");
        BUNKER_VALIDATION_MAX_CELLS = b.comment("Лимит ячеек заливки, выше которого проверка считается проваленной.")
                .defineInRange("maxCells", 20000, 100, 1_000_000);
        BUNKER_VALIDATION_INTERVAL = b.comment("Страховочная перепроверка, тиков.")
                .defineInRange("interval", 200, 20, 72000);
        BUNKER_VALIDATIONS_PER_TICK = b.comment("[доп.] Сколько проверок бункеров за тик на мир, остальные ждут в очереди.")
                .defineInRange("perTick", 4, 1, 64);
        b.pop();
        b.pop();

        b.push("bunkerMetal");
        BUNKER_METAL_OWNER_ONLY_BREAK = b.comment("Оболочку активного бункера ломают только владелец, доверенные и операторы.")
                .define("ownerOnlyBreak", false);
        BUNKER_METAL_BLOCK_FAKE_PLAYERS = b.comment("[доп.] Запретить ломать блоки мода «фейковым игрокам» (торнадо Weather2, машины).")
                .define("blockFakePlayers", true);
        b.pop();

        b.push("room");
        ROOM_MIN_SHARED_FACE = b.comment("v3 1.5: минимальная общая грань соседних областей, блоков (5 → проход не меньше 3×3).")
                .defineInRange("minSharedFace", 5, 3, 17);
        ROOM_ADJACENCY = b.comment("SHARED_WALL — общая стена, DOUBLE_WALL — у каждого куба своя стена вплотную.")
                .defineEnum("adjacency", Adjacency.SHARED_WALL);
        ROOM_BIND_REQUIRES_ACTIVE = b.comment("Привязать блок комнат можно только к активному ядру.")
                .define("bindRequiresActive", true);
        b.pop();

        b.push("upgrade");
        UPGRADE_REQUIRES_ACTIVE = b.comment("Улучшать ядро и комнаты можно только в состоянии «Активен».")
                .define("requiresActive", true);
        EXPANSION_TIER_COST = b.comment("«Улучшить расширения» (ядро III): цена каждого шага в предметах expansionTierItem.",
                        "Число элементов — сколько шагов доступно.")
                .defineList("expansionTierCost", java.util.List.of(2, 4, 6), () -> 2, o -> o instanceof Integer i && i >= 0);
        EXPANSION_TIER_ITEM = b.comment("Предмет для «Улучшить расширения».").define("expansionTierItem", "minecraft:netherite_ingot");
        EXPANSION_TIER_BUDGET = b.comment("+ к бюджету расширения каждой области за шаг.").defineInRange("expansionTierBudget", 2, 0, 64);
        EXPANSION_TIER_HORIZONTAL = b.comment("+ к пределу внутреннего размера по X и Z за шаг (по Y предел не растёт).")
                .defineInRange("expansionTierHorizontal", 2, 0, 64);
        b.pop();

        b.push("area");
        AREA_MAX_HORIZONTAL = b.comment("v3 1.6: предел внутреннего размера области по X и Z.")
                .defineInRange("maxHorizontal", 7, 3, 31);
        AREA_MAX_VERTICAL = b.comment("v3 1.6: предел внутреннего размера области по Y.")
                .defineInRange("maxVertical", 5, 3, 31);
        b.pop();

        b.push("door");
        DOOR_GRACE_SECONDS = b.comment("v3 2.2: сколько секунд открытая гермодверь/гермолюк не снимает защиту.")
                .defineInRange("graceSeconds", 10, 0, 600);
        DOOR_AUTO_CLOSE_SECONDS = b.comment("v3 2.2: через сколько секунд гермодверь закрывается сама (если нет сигнала редстоуна).")
                .defineInRange("autoCloseSeconds", 5, 1, 600);
        DOOR_LOCKED_BY_DEFAULT = b.comment("v3 2.3: гермодвери нового бункера заперты.")
                .define("lockedByDefault", false);
        b.pop();

        b.push("bunker");
        b.push("energy");
        ENERGY_INTERVAL = b.comment("v3 3.1: период расчёта энергии, тиков (весь интервал считается сразу).")
                .defineInRange("interval", 20, 1, 200);
        ENERGY_AREA_BASE = b.comment("v3 3.3: P_base — потребление области 3×3×3, FE/t.")
                .defineInRange("areaBase", 10.0, 0.0, 100000.0);
        ENERGY_AREA_PER_CELL = b.comment("v3 3.3: k — FE/t за каждую ячейку объёма сверх 27.")
                .defineInRange("areaPerCell", 0.5, 0.0, 10000.0);
        ENERGY_RESTORE_DELAY = b.comment("v3 3.5: период автоматического включения обесточенной комнаты, тиков.")
                .defineInRange("restoreDelay", 100, 20, 72000);
        ENERGY_MAX_PER_TYPE = b.comment("v3 4.2: сколько генераторов одного типа работает на бункер.")
                .defineInRange("maxPerType", 1, 1, 64);
        ENERGY_MAX_INLETS = b.comment("v3 4.3: энерговводов на бункер.")
                .defineInRange("maxInlets", 1, 1, 16);
        ENERGY_MAX_CABLE_NETWORK = b.comment("v3 4.3: максимум блоков кабеля в одной сети.")
                .defineInRange("maxCableNetwork", 512, 1, 100000);
        ENERGY_ACCEPT_FOREIGN = b.comment("v3 4.4: энерговвод принимает FE других модов (Create Crafts & Additions и т. п.).")
                .define("acceptForeignFE", true);
        ENERGY_FOREIGN_MAX_RATE = b.comment("v3 4.4: потолок «Внешнего ввода», FE/t.")
                .defineInRange("foreignMaxRate", 40, 0, 1_000_000);
        ENERGY_FVU_BASE = b.comment("[доп.] ФВУ: базовое потребление, FE/t.").defineInRange("filterUnitBase", 2, 0, 100000);
        ENERGY_FVU_CELLS_PER_FE = b.comment("[доп.] ФВУ: +1 FE/t за каждые начатые N ячеек зоны.").defineInRange("filterUnitCellsPerFE", 50, 1, 100000);
        ENERGY_PURIFIER = b.comment("[доп.] Станция водоочистки, FE/t, пока очищает.").defineInRange("purifier", 3, 0, 100000);
        ENERGY_RECYCLER = b.comment("Станция очистки металла, FE/t, пока работает.").defineInRange("recycler", 12, 0, 100000);
        ENERGY_STATION_PER_PART = b.comment("[доп.] Станция обслуживания: FE/t на чинящуюся часть.").defineInRange("stationPerPart", 10, 0, 100000);
        b.pop();
        b.pop();

        b.push("energy");
        ENERGY_SURFACE_TORNADO_IMMUNE = b.comment("v3 4.3: кабели и генераторы защищены от взрывов и торнадо.")
                .define("surfaceTornadoImmune", false);
        b.pop();

        b.push("generator");
        b.push("furnace");
        GEN_FURNACE_RATE = b.comment("Твердотопливный генератор, FE/t, пока горит.").defineInRange("rate", 8, 0, 100000);
        GEN_FURNACE_INDOOR_PENALTY = b.comment("Горящий в зоне бункера генератор ускоряет износ фильтра ФВУ.").define("indoorPenalty", true);
        b.pop();
        b.push("solar");
        GEN_SOLAR_PEAK = b.comment("Солнечная панель в полдень, FE/t.").defineInRange("peak", 20, 0, 100000);
        GEN_SOLAR_RAIN = b.comment("Множитель в дождь.").defineInRange("rainMultiplier", 0.5, 0.0, 1.0);
        GEN_SOLAR_THUNDER = b.comment("Множитель в грозу.").defineInRange("thunderMultiplier", 0.2, 0.0, 1.0);
        b.pop();
        b.pop();

        b.push("storage");
        BATTERY_CAPACITY = b.comment("v3 5.1: ёмкость аккумулятора, FE.").defineInRange("batteryCapacity", 200_000, 1, Integer.MAX_VALUE);
        BATTERY_RATE = b.comment("v3 5.1: заряд/разряд аккумулятора, FE/t.").defineInRange("batteryRate", 100, 1, 1_000_000);
        CELL_CAPACITY = b.comment("v3 5.2: ёмкость энергоячейки, FE (меняет ёмкость только новых расчётов, предмет хранит заряд).")
                .defineInRange("cellCapacity", 50_000, 1, Integer.MAX_VALUE);
        RACK_RATE_PER_CELL = b.comment("v3 5.3: заряд/разряд одной ячейки в стойке, FE/t.").defineInRange("rackRatePerCell", 50, 1, 1_000_000);
        b.pop();

        b.push("station");
        STATION_MAX_PER_BUNKER = b.comment("v3 7.2: станций обслуживания на бункер.").defineInRange("maxPerBunker", 1, 1, 16);
        STATION_MATERIAL = b.comment("v3 7.2: расход материала, \"id_предмета N\" — 1 предмет на N единиц прочности (5-й слот).",
                        "Пусто — выключено. Пример: \"minecraft:iron_ingot 60\".")
                .define("materialPerDurability", "");
        b.pop();

        b.push("suit");
        SUIT_ANVIL_REPAIR = b.comment("v3 7.3: ремонт костюма на наковальне и в сетке крафта. false — только станция обслуживания.")
                .define("anvilRepair", false);
        b.pop();

        b.push("lifeSupport");
        LIFE_SUPPORT_ENABLED = b.comment("v3 8: воздух и вода бункера. Совет при обновлении: сначала false, включить через 1–2 сессии.")
                .define("enabled", true);
        AIR_RESTORE_PER_MIN = b.comment("Работающая ФВУ: +% воздуха в минуту.").defineInRange("airRestorePerMinute", 5.0, 0.0, 100.0);
        AIR_DRAIN_PER_PLAYER_MIN = b.comment("Без ФВУ: −% воздуха в минуту за каждого игрока в зоне.").defineInRange("airDrainPerPlayerMinute", 2.0, 0.0, 100.0);
        WATER_PURIFY_SECONDS = b.comment("Очистка 1000 mB, секунд.").defineInRange("purifySeconds", 30, 1, 3600);
        WATER_BOTTLE_ITEM = b.comment("Что получает игрок, набирая пустой бутылкой чистую воду (250 mB). Жажда — отдельным модом:",
                        "укажите здесь его «чистую» бутылку. minecraft:potion — обычная бутылка воды.")
                .define("bottleItem", "bunker:clean_water_bottle");
        b.pop();

        b.push("recycler");
        RECYCLER_INPUT = b.comment("Что перерабатывает станция очистки металла (металлолом Wastelands).")
                .define("input", "wastelands:scrap_metal");
        RECYCLER_CHANCE = b.comment("Шанс получить незеритовый обломок с одного металлолома.").defineInRange("successChance", 0.03, 0.0, 1.0);
        RECYCLER_SECONDS = b.comment("Секунд на один металлолом.").defineInRange("seconds", 10, 1, 3600);
        RECYCLER_FAIL_MIN = b.comment("Неудача: железных самородков от…").defineInRange("failNuggetsMin", 1, 0, 64);
        RECYCLER_FAIL_MAX = b.comment("…до.").defineInRange("failNuggetsMax", 5, 0, 64);
        b.pop();

        b.push("filter");
        FILTER_REQUIRE_OPEN_INTAKE = b.comment("v3 8.2: перед воздухозабором ФВУ снаружи должен быть неполный блок.")
                .define("requireOpenIntake", true);
        FILTER_AIR_RESOURCE = b.comment("Ресурс фильтра-поглотителя (ед., 1 ед. в минуту на игрока).").defineInRange("airResource", 120, 1, 100000);
        FILTER_WATER_RESOURCE = b.comment("Ресурс водяного картриджа (ед., 1 ед. на 1000 mB).").defineInRange("waterResource", 16, 1, 100000);
        b.pop();

        SPEC = b.build();
    }

    private BunkerConfig() {}


    public static double stageThreshold(int stage) {
        List<? extends Double> list = RADIATION_STAGE_THRESHOLDS.get();
        double[] def = {0.25, 0.5, 0.75, 1.0};
        int i = stage - 1;
        return i < list.size() ? list.get(i) : def[i];
    }

    public static double suitCoefficient(int level) {
        List<? extends Double> list = SUIT_LEVEL_COEFFICIENTS.get();
        double[] def = {0.5, 0.75, 0.9};
        int i = Math.max(1, Math.min(3, level)) - 1;
        return i < list.size() ? list.get(i) : def[i];
    }
}
