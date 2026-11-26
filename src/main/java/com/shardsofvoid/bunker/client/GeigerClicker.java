package com.shardsofvoid.bunker.client;

import com.shardsofvoid.bunker.config.BunkerClientConfig;
import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.registry.ModMisc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Щелчки счётчика Гейгера: частота растёт со скоростью набора дозы, при нулевой скорости — тишина.
 * Слышны, только если у игрока есть счётчик (radiation.geigerItem, по умолчанию из Wastelands).
 * Категория звука — «Игроки»: громкость меняется ползунком в настройках звука и geiger.volume.
 */
public final class GeigerClicker {
    /** Щелчков в секунду на 1 ед./с скорости набора. */
    private static final double CLICKS_PER_UNIT = 4.0;
    private static final RandomSource RANDOM = RandomSource.create();

    private static String cachedId;
    private static Item cachedItem;

    private GeigerClicker() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || !ClientState.hasData || ClientState.rate <= 0) return;
        if (BunkerConfig.SPEC.isLoaded() && !BunkerConfig.RADIATION_GEIGER_SOUND.get()) return;
        if (!hasCounter(mc.player)) return;
        double perTick = ClientState.rate * CLICKS_PER_UNIT / 20.0;
        double chance = Math.min(0.9, 1.0 - Math.exp(-perTick));
        if (RANDOM.nextDouble() < chance) {
            float volume = BunkerClientConfig.GEIGER_VOLUME.get().floatValue();
            if (volume <= 0) return;
            float pitch = 0.85F + RANDOM.nextFloat() * 0.3F;
            mc.getSoundManager().play(new SimpleSoundInstance(ModMisc.GEIGER_CLICK.get().getLocation(), SoundSource.PLAYERS,
                    volume, pitch, RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
        }
    }

    private static boolean hasCounter(Player player) {
        String id = BunkerConfig.SPEC.isLoaded() ? BunkerConfig.RADIATION_GEIGER_ITEM.get() : "wastelands:geiger_counter";
        if (id == null || id.isBlank()) return true;
        if (!id.equals(cachedId)) {
            cachedId = id;
            ResourceLocation rl = ResourceLocation.tryParse(id.trim());
            cachedItem = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
        }
        if (cachedItem == Items.AIR) return false; // мода со счётчиком нет — щелчков тоже нет
        Item item = cachedItem;
        return player.getInventory().hasAnyMatching(s -> s.is(item));
    }
}
