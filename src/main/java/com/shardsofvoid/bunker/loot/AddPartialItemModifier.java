package com.shardsofvoid.bunker.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.shardsofvoid.bunker.item.EnergyCellItem;
import com.shardsofvoid.bunker.item.FilterItem;
import com.shardsofvoid.bunker.registry.ModComponents;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * v3 5.2 / 8.6: с шансом chance добавить в лут предмет с частичным зарядом или ресурсом.
 * min/max — доля заряда (энергоячейка) или оставшегося ресурса (фильтр), случайно в этом диапазоне.
 * Какие сундуки — задаётся условиями (обычно neoforge:loot_table_id), всё настраивается датапаком.
 */
public final class AddPartialItemModifier extends LootModifier {
    public static final MapCodec<AddPartialItemModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).and(inst.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(m -> m.item),
            Codec.floatRange(0, 1).fieldOf("chance").forGetter(m -> m.chance),
            Codec.floatRange(0, 1).optionalFieldOf("min", 0f).forGetter(m -> m.min),
            Codec.floatRange(0, 1).optionalFieldOf("max", 1f).forGetter(m -> m.max)
    )).apply(inst, AddPartialItemModifier::new));

    private final Item item;
    private final float chance;
    private final float min;
    private final float max;

    public AddPartialItemModifier(LootItemCondition[] conditions, Item item, float chance, float min, float max) {
        super(conditions);
        this.item = item;
        this.chance = chance;
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (context.getRandom().nextFloat() >= chance) return loot;
        ItemStack stack = new ItemStack(item);
        float fraction = Mth.lerp(context.getRandom().nextFloat(), min, max);
        if (item instanceof EnergyCellItem) {
            EnergyCellItem.setEnergy(stack, Math.round(EnergyCellItem.capacity() * fraction));
        } else if (item instanceof FilterItem filter) {
            int wear = Math.round(filter.resource() * (1 - fraction));
            stack.set(ModComponents.FILTER_WEAR.get(), Mth.clamp(wear, 0, Math.max(0, filter.resource() - 1)));
        }
        loot.add(stack);
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
