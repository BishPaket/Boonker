package com.shardsofvoid.bunker.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.shardsofvoid.bunker.registry.ModMisc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Раздел 3.3: торнадо Weather2 не срывает и не уносит блоки тега bunker:indestructible. */
@Pseudo
@Mixin(targets = "weather2.weathersystem.storm.TornadoHelper", remap = false)
public abstract class TornadoHelperMixin {
    @Inject(method = "canGrab(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void bunker$protect(Level level, BlockState state, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (state.is(ModMisc.INDESTRUCTIBLE)) cir.setReturnValue(false);
    }
}
