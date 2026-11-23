package com.shardsofvoid.bunker.radiation;

import java.util.ArrayList;
import java.util.List;

import com.shardsofvoid.bunker.BunkerMod;
import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.item.RadSuitItem;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/** Множители формулы rate = base × depthMult × weatherMult × (1 − suitProtection) × bunkerMult. */
public final class RadiationMath {
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private record DepthRange(int minY, int maxY, double mult) {}

    private static List<? extends String> cachedDepthSource;
    private static List<DepthRange> cachedDepth = List.of();

    private RadiationMath() {}

    /** depthMult по таблице из конфига; вне диапазонов — 1.0. */
    public static double depthMultiplier(int y) {
        List<? extends String> src = BunkerConfig.RADIATION_DEPTH_MULTIPLIERS.get();
        if (src != cachedDepthSource) {
            cachedDepth = parseDepth(src);
            cachedDepthSource = src;
        }
        for (DepthRange r : cachedDepth) {
            if (y >= r.minY && y <= r.maxY) return r.mult;
        }
        return 1.0;
    }

    private static List<DepthRange> parseDepth(List<? extends String> src) {
        List<DepthRange> out = new ArrayList<>();
        for (String line : src) {
            try {
                String[] kv = line.split("=");
                String[] range = kv[0].trim().split("\\.\\.");
                out.add(new DepthRange(Integer.parseInt(range[0].trim()), Integer.parseInt(range[1].trim()),
                        Double.parseDouble(kv[1].trim())));
            } catch (RuntimeException e) {
                BunkerMod.LOGGER.warn("radiation.depthMultipliers: не удалось разобрать строку '{}'", line);
            }
        }
        return List.copyOf(out);
    }

    /** suitProtection = Σ(доля части × коэффициент её уровня), не выше suit.maxProtection. */
    public static double suitProtection(LivingEntity entity) {
        double sum = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getItem() instanceof RadSuitItem suit && suit.getType().getSlot() == slot && !RadSuitItem.isBroken(stack)) {
                sum += partShare(suit.getType()) * BunkerConfig.suitCoefficient(RadSuitItem.level(stack));
            }
        }
        return Math.min(sum, BunkerConfig.SUIT_MAX_PROTECTION.get());
    }

    public static double partShare(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> BunkerConfig.SUIT_SHARE_HELMET.get();
            case CHESTPLATE -> BunkerConfig.SUIT_SHARE_CHEST.get();
            case LEGGINGS -> BunkerConfig.SUIT_SHARE_LEGS.get();
            case BOOTS -> BunkerConfig.SUIT_SHARE_BOOTS.get();
            default -> 0.0;
        };
    }

    /** Номер стадии 0–4 по доле от максимума дозы. */
    public static int stageOf(double dose, double maxDose) {
        double f = maxDose <= 0 ? 0 : dose / maxDose;
        int stage = 0;
        for (int s = 1; s <= 4; s++) {
            if (f + 1e-9 >= BunkerConfig.stageThreshold(s)) stage = s;
        }
        return stage;
    }
}
