package com.shardsofvoid.bunker.loot;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.shardsofvoid.bunker.registry.ModMisc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

/** Записать случайное целое в custom_data предмета (например, число сигарет в пачке Tobacconery). */
public final class SetCustomIntFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetCustomIntFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).and(i.group(
            Codec.STRING.fieldOf("key").forGetter(f -> f.key),
            NumberProviders.CODEC.fieldOf("value").forGetter(f -> f.value)))
            .apply(i, SetCustomIntFunction::new));

    private final String key;
    private final NumberProvider value;

    private SetCustomIntFunction(List<LootItemCondition> conditions, String key, NumberProvider value) {
        super(conditions);
        this.key = key;
        this.value = value;
    }

    @Override
    public LootItemFunctionType<SetCustomIntFunction> getType() {
        return ModMisc.SET_CUSTOM_INT.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        int v = value.getInt(context);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(key, v));
        return stack;
    }
}
