package com.shardsofvoid.bunker.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.shardsofvoid.bunker.registry.ModMisc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

/**
 * Страховка раздела 3.2: даже если взрыв какого-то мода не вызывает ExplosionEvent.Detonate,
 * блоки мода убираются из списка разрушаемых перед финализацией взрыва.
 */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Shadow
    @Final
    private Level level;

    @Shadow
    public abstract List<BlockPos> getToBlow();

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void bunker$protectBlocks(boolean spawnParticles, CallbackInfo ci) {
        getToBlow().removeIf(pos -> ModMisc.isProtected(level.getBlockState(pos)));
    }
}
