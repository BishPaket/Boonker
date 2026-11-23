package com.shardsofvoid.bunker.registry;

import com.shardsofvoid.bunker.BunkerMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** v3 8.5: «Обезвоживание» — нет естественной регенерации (миксин FoodData), Голод I и позже Слабость I. */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, BunkerMod.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> DEHYDRATION = EFFECTS.register("dehydration",
            () -> new Effect(MobEffectCategory.HARMFUL, 0xC9A35E));

    private static final class Effect extends MobEffect {
        Effect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    private ModEffects() {}
}
