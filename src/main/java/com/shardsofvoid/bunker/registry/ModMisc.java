package com.shardsofvoid.bunker.registry;

import com.shardsofvoid.bunker.BunkerMod;
import com.shardsofvoid.bunker.item.RadSuitUpgradeRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Звуки, сериализаторы рецептов, теги, тип урона. */
public final class ModMisc {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, BunkerMod.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, BunkerMod.MODID);

    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, BunkerMod.MODID);

    /** v3 5.2 / 8.6: частично заряженные ячейки и изношенные фильтры в сундуках структур. */
    public static final DeferredHolder<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier>,
            com.mojang.serialization.MapCodec<com.shardsofvoid.bunker.loot.AddPartialItemModifier>> ADD_PARTIAL_ITEM =
            LOOT_MODIFIERS.register("add_partial_item", () -> com.shardsofvoid.bunker.loot.AddPartialItemModifier.CODEC);

    public static final DeferredRegister<net.minecraft.world.level.storage.loot.entries.LootPoolEntryType> LOOT_ENTRIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.LOOT_POOL_ENTRY_TYPE, BunkerMod.MODID);
    public static final DeferredRegister<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType<?>> LOOT_FUNCTIONS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.LOOT_FUNCTION_TYPE, BunkerMod.MODID);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.FEATURE, BunkerMod.MODID);

    public static final DeferredHolder<net.minecraft.world.level.storage.loot.entries.LootPoolEntryType,
            net.minecraft.world.level.storage.loot.entries.LootPoolEntryType> SOFT_ITEM_ENTRY = LOOT_ENTRIES.register("soft_item",
            () -> new net.minecraft.world.level.storage.loot.entries.LootPoolEntryType(com.shardsofvoid.bunker.loot.SoftItemEntry.CODEC));
    public static final DeferredHolder<net.minecraft.world.level.storage.loot.entries.LootPoolEntryType,
            net.minecraft.world.level.storage.loot.entries.LootPoolEntryType> TACZ_ENTRY = LOOT_ENTRIES.register("tacz",
            () -> new net.minecraft.world.level.storage.loot.entries.LootPoolEntryType(com.shardsofvoid.bunker.loot.TaczEntry.CODEC));
    public static final DeferredHolder<net.minecraft.world.level.storage.loot.functions.LootItemFunctionType<?>,
            net.minecraft.world.level.storage.loot.functions.LootItemFunctionType<com.shardsofvoid.bunker.loot.SetCustomIntFunction>> SET_CUSTOM_INT =
            LOOT_FUNCTIONS.register("set_custom_int",
                    () -> new net.minecraft.world.level.storage.loot.functions.LootItemFunctionType<>(com.shardsofvoid.bunker.loot.SetCustomIntFunction.CODEC));
    public static final DeferredHolder<net.minecraft.world.level.levelgen.feature.Feature<?>, com.shardsofvoid.bunker.worldgen.BrokenBunkerFeature> BROKEN_BUNKER =
            FEATURES.register("broken_bunker", com.shardsofvoid.bunker.worldgen.BrokenBunkerFeature::new);

    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_CLICK = SOUNDS.register("geiger_click",
            () -> SoundEvent.createVariableRangeEvent(BunkerMod.id("geiger_click")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BUNKER_BREACH = SOUNDS.register("bunker_breach",
            () -> SoundEvent.createVariableRangeEvent(BunkerMod.id("bunker_breach")));

    public static final DeferredHolder<RecipeSerializer<?>, RadSuitUpgradeRecipe.Serializer> RADSUIT_UPGRADE =
            RECIPE_SERIALIZERS.register("radsuit_upgrade", RadSuitUpgradeRecipe.Serializer::new);

    /** Тег bunker:shell_blocks — блоки, которые считаются оболочкой (раздел 3.4). */
    public static final TagKey<Block> SHELL_BLOCKS = TagKey.create(Registries.BLOCK, BunkerMod.id("shell_blocks"));
    /** Блоки, которые не разрушают взрывы и торнадо (металл, ядро, комнаты). */
    public static final TagKey<Block> INDESTRUCTIBLE = TagKey.create(Registries.BLOCK, BunkerMod.id("indestructible"));
    /** v3 2: гермодверь и гермолюк — шлюзовой режим. */
    public static final TagKey<Block> AIRLOCKS = TagKey.create(Registries.BLOCK, BunkerMod.id("airlocks"));
    /** v3 4.3: кабели и генераторы на поверхности (защищаются только при energy.surfaceTornadoImmune). */
    public static final TagKey<Block> SURFACE_ENERGY = TagKey.create(Registries.BLOCK, BunkerMod.id("surface_energy"));
    /** Части радиационного костюма (база рецептов улучшения). */
    public static final TagKey<Item> RADSUIT = TagKey.create(Registries.ITEM, BunkerMod.id("radsuit"));

    /** v3 8.3: урон спёртым воздухом. */
    public static final ResourceKey<DamageType> STALE_AIR_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, BunkerMod.id("stale_air"));
    /** Тип урона bunker:radiation (data-driven, обходит броню через тег). */
    public static final ResourceKey<DamageType> RADIATION_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, BunkerMod.id("radiation"));

    /** Блок не разрушают взрывы, торнадо и «фейковые игроки»: тег indestructible, плюс энергоблоки при energy.surfaceTornadoImmune. */
    public static boolean isProtected(net.minecraft.world.level.block.state.BlockState state) {
        if (state.is(INDESTRUCTIBLE)) return true;
        return state.is(SURFACE_ENERGY) && com.shardsofvoid.bunker.config.BunkerConfig.SPEC.isLoaded()
                && com.shardsofvoid.bunker.config.BunkerConfig.ENERGY_SURFACE_TORNADO_IMMUNE.get();
    }

    private ModMisc() {}
}
