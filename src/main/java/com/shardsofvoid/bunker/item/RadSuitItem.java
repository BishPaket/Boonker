package com.shardsofvoid.bunker.item;

import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.radiation.RadiationMath;
import com.shardsofvoid.bunker.radiation.RadiationSystem;
import com.shardsofvoid.bunker.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Часть радиационного костюма. Уровень хранится в data component radsuit_level (1–3).
 * Как элитры, часть не исчезает при нулевой прочности, а становится «сломанной»: защиты не даёт,
 * чинится на наковальне железом.
 */
public class RadSuitItem extends ArmorItem {
    public static final int[] LEVEL_COLORS = {0xFFE0C040, 0xFF40B0E0, 0xFFB050E0};

    public RadSuitItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    public static int level(ItemStack stack) {
        Integer level = stack.get(ModComponents.RADSUIT_LEVEL.get());
        return level == null ? 1 : Math.max(1, Math.min(3, level));
    }

    public static int levelColor(int level) {
        return LEVEL_COLORS[Math.max(1, Math.min(3, level)) - 1];
    }

    public static boolean isBroken(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /** Не даём части исчезнуть: прочность останавливается на 1. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
        int room = stack.getMaxDamage() - 1 - stack.getDamageValue();
        return Math.max(0, Math.min(amount, room));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int level = level(stack);
        tooltip.add(Component.translatable("tooltip.bunker.radsuit.level", RadiationSystem.roman(level))
                .withStyle(s -> s.withColor(levelColor(level) & 0xFFFFFF)));
        if (BunkerConfig.SPEC.isLoaded()) {
            double protection = RadiationMath.partShare(getType()) * BunkerConfig.suitCoefficient(level);
            tooltip.add(Component.translatable("tooltip.bunker.radsuit.protection",
                    String.format("%.1f", protection * 100)).withStyle(ChatFormatting.GREEN));
        }
        int max = stack.getMaxDamage();
        int left = max - stack.getDamageValue();
        if (isBroken(stack)) {
            tooltip.add(Component.translatable("tooltip.bunker.radsuit.broken").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable("tooltip.bunker.radsuit.durability", left, max).withStyle(ChatFormatting.GRAY));
        }
    }
}
