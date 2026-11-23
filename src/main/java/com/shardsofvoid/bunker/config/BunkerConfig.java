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
    public static final ModConfigSpec.IntValue ROOM_SIZE;
    public static final ModConfigSpec.EnumValue<Adjacency> ROOM_ADJACENCY;
    public static final ModConfigSpec.BooleanValue ROOM_BIND_REQUIRES_ACTIVE;

    // upgrade
    public static final ModConfigSpec.BooleanValue UPGRADE_REQUIRES_ACTIVE;

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
        ROOM_SIZE = b.comment("Размер куба комнаты (нечётный).")
                .defineInRange("size", 5, 3, 15);
        ROOM_ADJACENCY = b.comment("SHARED_WALL — общая стена, DOUBLE_WALL — у каждого куба своя стена вплотную.")
                .defineEnum("adjacency", Adjacency.SHARED_WALL);
        ROOM_BIND_REQUIRES_ACTIVE = b.comment("Привязать блок комнат можно только к активному ядру.")
                .define("bindRequiresActive", true);
        b.pop();

        b.push("upgrade");
        UPGRADE_REQUIRES_ACTIVE = b.comment("Улучшать ядро и комнаты можно только в состоянии «Активен».")
                .define("requiresActive", true);
        b.pop();

        SPEC = b.build();
    }

    private BunkerConfig() {}

    /** Размер комнаты, приведённый к нечётному. */
    public static int roomSize() {
        int s = ROOM_SIZE.get();
        return (s % 2 == 0) ? s + 1 : s;
    }

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
