package com.shardsofvoid.bunker.item;

import java.util.List;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** v3 8.6: фильтр-поглотитель (air = true) или водяной картридж. Износ — в data component filter_wear. */
public class FilterItem extends Item {
    private final boolean air;

    public FilterItem(boolean air, Properties properties) {
        super(properties);
        this.air = air;
    }

    public boolean isAir() {
        return air;
    }

    public int resource() {
        if (!BunkerConfig.SPEC.isLoaded()) return air ? 120 : 16;
        return air ? BunkerConfig.FILTER_AIR_RESOURCE.get() : BunkerConfig.FILTER_WATER_RESOURCE.get();
    }

    public static int wear(ItemStack stack) {
        Integer w = stack.get(ModComponents.FILTER_WEAR.get());
        return w == null ? 0 : w;
    }

    /** Остаток ресурса, ед. */
    public static int remaining(ItemStack stack) {
        if (!(stack.getItem() instanceof FilterItem f)) return 0;
        return Math.max(0, f.resource() - wear(stack));
    }

    /** Израсходовать units единиц; true — фильтр отработал (его надо убрать). */
    public static boolean addWear(ItemStack stack, int units) {
        if (!(stack.getItem() instanceof FilterItem f)) return true;
        int w = wear(stack) + units;
        stack.set(ModComponents.FILTER_WEAR.get(), w);
        return w >= f.resource();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return wear(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * remaining(stack) / Math.max(1, resource()));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return air ? 0xB0B0B0 : 0x3F76E4;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bunker.filter", remaining(stack), resource()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(air ? "tooltip.bunker.filter.air" : "tooltip.bunker.filter.water").withStyle(ChatFormatting.DARK_GRAY));
    }
}
