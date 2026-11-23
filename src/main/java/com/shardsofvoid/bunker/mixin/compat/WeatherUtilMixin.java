package com.shardsofvoid.bunker.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.shardsofvoid.bunker.registry.ModMisc;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Раздел 3.3: общие проверки Weather2 «можно ли схватить / удалить блок». */
@Pseudo
@Mixin(targets = "weather2.util.WeatherUtil", remap = false)
public abstract class WeatherUtilMixin {
    @Inject(method = "shouldGrabBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void bunker$noGrab(Level level, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (ModMisc.isProtected(state)) cir.setReturnValue(false);
    }

    @Inject(method = "shouldRemoveBlock(Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void bunker$noRemove(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (ModMisc.isProtected(state)) cir.setReturnValue(false);
    }
}
