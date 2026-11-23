package com.shardsofvoid.bunker.client;

import com.shardsofvoid.bunker.config.BunkerClientConfig;
import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.registry.ModMisc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.RandomSource;

/** Щелчки счётчика Гейгера: частота растёт со скоростью набора дозы, при нулевой скорости — тишина. */
public final class GeigerClicker {
    /** Щелчков в секунду на 1 ед./с скорости набора. */
    private static final double CLICKS_PER_UNIT = 4.0;
    private static final RandomSource RANDOM = RandomSource.create();

    private GeigerClicker() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || !ClientState.hasData || ClientState.rate <= 0) return;
        if (BunkerConfig.SPEC.isLoaded() && !BunkerConfig.RADIATION_GEIGER_SOUND.get()) return;
        double perTick = ClientState.rate * CLICKS_PER_UNIT / 20.0;
        double chance = Math.min(0.9, 1.0 - Math.exp(-perTick));
        if (RANDOM.nextDouble() < chance) {
            float volume = BunkerClientConfig.GEIGER_VOLUME.get().floatValue();
            if (volume <= 0) return;
            float pitch = 0.85F + RANDOM.nextFloat() * 0.3F;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModMisc.GEIGER_CLICK.get(), pitch, volume));
        }
    }
}
