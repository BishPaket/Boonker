package com.shardsofvoid.bunker.item;

import java.util.List;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** v3 5.2: энергоячейка. Заряд — в data component stored_energy; полоска на предмете и подсказка. */
public class EnergyCellItem extends Item {
    public static final int DEFAULT_CAPACITY = 50_000;

    public EnergyCellItem(Properties properties) {
        super(properties);
    }

    public static int capacity() {
        return BunkerConfig.SPEC.isLoaded() ? BunkerConfig.CELL_CAPACITY.get() : DEFAULT_CAPACITY;
    }

    public static int energy(ItemStack stack) {
        Integer e = stack.get(ModComponents.STORED_ENERGY.get());
        return e == null ? 0 : e;
    }

    public static void setEnergy(ItemStack stack, int energy) {
        stack.set(ModComponents.STORED_ENERGY.get(), Mth.clamp(energy, 0, capacity()));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * energy(stack) / Math.max(1, capacity()));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x40C8FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bunker.energy", String.format("%,d", energy(stack)).replace(',', ' '),
                String.format("%,d", capacity()).replace(',', ' ')).withStyle(ChatFormatting.AQUA));
    }
}
