package com.shardsofvoid.bunker.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.shardsofvoid.bunker.registry.ModEffects;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/** v3 8.5: «Обезвоживание» отключает естественную регенерацию здоровья от сытости. */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    @Redirect(method = "tick(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"), require = 0)
    private void bunker$noRegenWhenDehydrated(Player player, float amount) {
        if (player.hasEffect(ModEffects.DEHYDRATION)) return;
        player.heal(amount);
    }
}
