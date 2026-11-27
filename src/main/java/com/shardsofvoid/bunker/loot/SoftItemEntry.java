package com.shardsofvoid.bunker.loot;

import java.util.List;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.shardsofvoid.bunker.registry.ModMisc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Предмет из другого мода по строковому id: если мода нет, запись просто ничего не даёт, а таблица не ломается
 * (обычная minecraft:item с неизвестным предметом выключает всю таблицу).
 */
public final class SoftItemEntry extends LootPoolSingletonContainer {
    public static final MapCodec<SoftItemEntry> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(e -> e.name))
            .and(singletonFields(i)).apply(i, SoftItemEntry::new));

    private final String name;

    private SoftItemEntry(String name, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.name = name;
    }

    @Override
    public LootPoolEntryType getType() {
        return ModMisc.SOFT_ITEM_ENTRY.get();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> output, LootContext context) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) return;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) return;
        output.accept(new ItemStack(item));
    }
}
