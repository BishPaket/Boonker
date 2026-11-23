package com.shardsofvoid.bunker.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.shardsofvoid.bunker.config.BunkerConfig;

import net.minecraft.server.level.ServerPlayer;

/**
 * Раздел 1.4: доза начисляется только одной системой. Собственный тик радиации Wastelands
 * (начисление по поверхности/небу, спад, симптомы) отключается — дозу ведёт этот мод,
 * а шкала Wastelands зеркалится (см. WastelandsCompat), поэтому Rad-Away и счётчик Гейгера работают.
 */
@Pseudo
@Mixin(targets = "org.takesome.necrosteam.radiation.RadiationManager", remap = false)
public abstract class WastelandsRadiationMixin {
    @Inject(method = "tick(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void bunker$overrideTick(ServerPlayer player, CallbackInfo ci) {
        if (BunkerConfig.SPEC.isLoaded() && BunkerConfig.WASTELANDS_OVERRIDE.get()) ci.cancel();
    }
}
