package com.shardsofvoid.bunker.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Стартовый конфиг (config/bunker-startup.toml): читается до регистрации предметов,
 * поэтому здесь лежат параметры ArmorMaterial костюма. Значения должны совпадать
 * на сервере и у клиентов — конфиг не синхронизируется.
 */
public final class BunkerStartupConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue ARMOR_HELMET;
    public static final ModConfigSpec.IntValue ARMOR_CHEST;
    public static final ModConfigSpec.IntValue ARMOR_LEGS;
    public static final ModConfigSpec.IntValue ARMOR_BOOTS;
    public static final ModConfigSpec.DoubleValue ARMOR_TOUGHNESS;
    public static final ModConfigSpec.DoubleValue ARMOR_KNOCKBACK_RESISTANCE;
    public static final ModConfigSpec.IntValue DURABILITY_MULTIPLIER;
    public static final ModConfigSpec.IntValue ENCHANTABILITY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("ArmorMaterial радиационного костюма. По умолчанию — как у железной брони.").push("suit");
        ARMOR_HELMET = b.defineInRange("armorHelmet", 2, 0, 30);
        ARMOR_CHEST = b.defineInRange("armorChestplate", 6, 0, 30);
        ARMOR_LEGS = b.defineInRange("armorLeggings", 5, 0, 30);
        ARMOR_BOOTS = b.defineInRange("armorBoots", 2, 0, 30);
        ARMOR_TOUGHNESS = b.defineInRange("toughness", 0.0, 0.0, 20.0);
        ARMOR_KNOCKBACK_RESISTANCE = b.defineInRange("knockbackResistance", 0.0, 0.0, 1.0);
        DURABILITY_MULTIPLIER = b.comment("Множитель прочности (железо = 15).")
                .defineInRange("durabilityMultiplier", 15, 1, 1000);
        ENCHANTABILITY = b.defineInRange("enchantability", 9, 0, 100);
        b.pop();
        SPEC = b.build();
    }

    private BunkerStartupConfig() {}
}
