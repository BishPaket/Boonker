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
    /** Части радиационного костюма (база рецептов улучшения). */
    public static final TagKey<Item> RADSUIT = TagKey.create(Registries.ITEM, BunkerMod.id("radsuit"));

    /** Тип урона bunker:radiation (data-driven, обходит броню через тег). */
    public static final ResourceKey<DamageType> RADIATION_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, BunkerMod.id("radiation"));

    private ModMisc() {}
}
