package com.shardsofvoid.bunker.radiation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.shardsofvoid.bunker.BunkerMod;
import com.shardsofvoid.bunker.bunker.BunkerManager;
import com.shardsofvoid.bunker.bunker.BunkerManager.ZoneInfo;
import com.shardsofvoid.bunker.compat.BetterWeatherCompat;
import com.shardsofvoid.bunker.compat.WastelandsCompat;
import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.item.RadSuitItem;
import com.shardsofvoid.bunker.network.RadiationSyncPayload;
import com.shardsofvoid.bunker.registry.ModAttachments;
import com.shardsofvoid.bunker.registry.ModMisc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Серверная радиация (раздел 1). Раз в radiation.checkInterval тиков для каждого игрока:
 * положение → защита → набор дозы → эффекты → синхронизация с клиентом.
 */
public final class RadiationSystem {
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Временное состояние игрока (на диск не пишется). */
    private static final class PlayerState {
        int lastStage = -1;
        double sentDose = -1;
        float sentRate = -1;
        int sentStage = -1;
        int sentSource = -1;
        double sentMax = -1;
        long lastSync = Long.MIN_VALUE / 2;
        long lastStage4Damage;
        long lastNausea;
        int wearTicks;
        int wastelandsMirror = -1;
        boolean forceSync = true;
        int sentAir = Integer.MIN_VALUE;
        int sentArea = Integer.MIN_VALUE;
        String sentAreaName = "";
        boolean sentUnpowered;
        long lastStaleAirDamage;
    }

    /** Результат расчёта облучения в текущей точке. */
    public record Exposure(double rate, double environmentRate, RadiationSource source, ZoneInfo zone) {}

    private static final Map<UUID, PlayerState> STATES = new HashMap<>();

    private record ParsedEffect(Holder<MobEffect> effect, int amplifier) {}

    private static final Map<Integer, List<? extends String>> effectSources = new HashMap<>();
    private static final Map<Integer, List<ParsedEffect>> parsedEffects = new HashMap<>();

    private RadiationSystem() {}

    // ---------------------------------------------------------------- доза

    public static double getDose(ServerPlayer player) {
        return player.getData(ModAttachments.RADIATION_DOSE);
    }

    public static double maxDose() {
        return BunkerConfig.RADIATION_MAX_DOSE.get();
    }

    /** Установить дозу (команды, препараты). Сразу обновляет шкалу Wastelands и клиент. */
    public static void setDose(ServerPlayer player, double dose) {
        double clamped = Math.max(0, Math.min(maxDose(), dose));
        player.setData(ModAttachments.RADIATION_DOSE, clamped);
        PlayerState st = state(player);
        st.wastelandsMirror = WastelandsCompat.push(player, clamped, maxDose());
        st.forceSync = true;
    }

    private static PlayerState state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), k -> new PlayerState());
    }

    // ---------------------------------------------------------------- события

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int interval = BunkerConfig.RADIATION_CHECK_INTERVAL.get();
        if (player.tickCount % interval != 0) return;
        check(player, interval);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) state(player).forceSync = true;
    }

    public static void onRespawnOrTeleport(ServerPlayer player) {
        PlayerState st = state(player);
        st.forceSync = true;
        st.wastelandsMirror = -1;
    }

    /** Перенос дозы при клонировании игрока (смерть / возврат из Энда). */
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        double old = event.getOriginal().getData(ModAttachments.RADIATION_DOSE);
        double dose = event.isWasDeath() && BunkerConfig.RADIATION_RESET_ON_DEATH.get() ? 0.0 : old;
        player.setData(ModAttachments.RADIATION_DOSE, dose);
        PlayerState st = state(player);
        st.lastStage = -1;
        st.wastelandsMirror = -1;
        st.forceSync = true;
    }

    public static void clearAll() {
        STATES.clear();
    }

    // ---------------------------------------------------------------- расчёт

    /** Скорость набора дозы в текущем положении игрока, ед./с. */
    public static Exposure computeExposure(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ZoneInfo zone = BunkerManager.zoneAt(level, BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()));
        if (!BunkerConfig.RADIATION_ENABLED.get()
                || !BunkerConfig.RADIATION_DIMENSIONS.get().contains(level.dimension().location().toString())) {
            return new Exposure(0, 0, zone != null && zone.protectedHere() ? RadiationSource.BUNKER : RadiationSource.NONE, zone);
        }
        double env = BunkerConfig.RADIATION_BASE_RATE.get()
                * RadiationMath.depthMultiplier(player.getBlockY())
                * weatherMultiplier(player);
        if (zone != null && zone.protectedHere()) {
            // bunkerMult = 0: защита бункера и костюма не суммируются
            return new Exposure(0, env, RadiationSource.BUNKER, zone);
        }
        // v3 3.5: в отключённой или обесточенной комнате игрок набирает дозу как снаружи
        double suit = RadiationMath.suitProtection(player);
        double rate = env * (1.0 - suit);
        return new Exposure(rate, env, suit > 0 ? RadiationSource.SUIT : RadiationSource.NONE, zone);
    }

    private static double weatherMultiplier(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        boolean require = BunkerConfig.WEATHER_REQUIRE_EXPOSURE.get();
        BlockPos head = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        double mult = 1.0;
        if (level.isRaining() && (!require || level.isRainingAt(head))) {
            mult *= level.isThundering() ? BunkerConfig.WEATHER_THUNDER_MULT.get() : BunkerConfig.WEATHER_RAIN_MULT.get();
        }
        double acid = BunkerConfig.WEATHER_ACID_RAIN_MULT.get();
        if (acid != 1.0 && (!require || level.canSeeSky(head)) && BetterWeatherCompat.isAcidRain(level)) {
            mult *= acid;
        }
        return mult;
    }

    private static void check(ServerPlayer player, int intervalTicks) {
        PlayerState st = state(player);
        double max = maxDose();
        double seconds = intervalTicks / 20.0;
        boolean exempt = player.isCreative() || player.isSpectator();

        double dose = getDose(player);
        dose = WastelandsCompat.pullExternalChange(player, st.wastelandsMirror, dose, max);

        Exposure exposure = computeExposure(player);
        if (!exempt) dose += exposure.rate() * seconds;

        if (exposure.zone() != null && exposure.zone().protectedHere()) {
            double decay = BunkerConfig.RADIATION_DECAY_IN_BUNKER.get() ? BunkerConfig.RADIATION_BUNKER_DECAY_RATE.get() : 0.0;
            decay = Math.max(decay, exposure.zone().doseDecay());
            dose -= decay * seconds;
            if (!exempt) {
                for (MobEffectInstance effect : exposure.zone().effects(intervalTicks + 40)) player.addEffect(effect);
            }
        }

        dose = Math.max(0, Math.min(max, dose));
        player.setData(ModAttachments.RADIATION_DOSE, dose);
        st.wastelandsMirror = WastelandsCompat.push(player, dose, max);

        int stage = RadiationMath.stageOf(dose, max);
        if (st.lastStage >= 0 && stage != st.lastStage) {
            Component msg = stage > st.lastStage
                    ? Component.translatable("message.bunker.radiation.stage_up", roman(stage)).withStyle(stageColor(stage))
                    : Component.translatable(stage == 0 ? "message.bunker.radiation.stage_clear" : "message.bunker.radiation.stage_down", roman(stage))
                    .withStyle(ChatFormatting.GREEN);
            player.displayClientMessage(msg, true);
        }
        st.lastStage = stage;

        if (!exempt) {
            applyStage(player, st, stage, intervalTicks);
            if ((exposure.zone() == null || !exposure.zone().protectedHere()) && exposure.environmentRate() > 0) {
                wearSuit(player, st, intervalTicks);
            }
            applyLifeSupport(player, exposure.zone(), intervalTicks, st);
        } else {
            applyLifeSupport(player, null, intervalTicks, st);
        }

        sync(player, st, dose, max, stage, exposure);
    }

    private static void applyStage(ServerPlayer player, PlayerState st, int stage, int intervalTicks) {
        if (stage <= 0) return;
        int duration = intervalTicks + 40;
        for (ParsedEffect e : stageEffects(stage)) {
            player.addEffect(new MobEffectInstance(e.effect(), duration, e.amplifier(), true, false, true));
        }
        long now = player.serverLevel().getGameTime();
        if (stage >= 3) {
            int nauseaInterval = BunkerConfig.STAGE3_NAUSEA_INTERVAL.get();
            if (nauseaInterval > 0 && now - st.lastNausea >= nauseaInterval * 20L) {
                st.lastNausea = now;
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, BunkerConfig.STAGE3_NAUSEA_DURATION.get() * 20, 0, true, false, true));
            }
        }
        if (stage >= 4) {
            float damage = BunkerConfig.STAGE4_DAMAGE.get().floatValue();
            if (damage > 0 && now - st.lastStage4Damage >= BunkerConfig.STAGE4_DAMAGE_INTERVAL.get() * 20L) {
                st.lastStage4Damage = now;
                player.hurt(radiationDamage(player.serverLevel()), damage);
            }
        }
    }

    /**
     * v3 8.3: эффекты нехватки воздуха для игрока в зоне. Выход из зоны — эффекты (короткие, без частиц) просто истекают.
     * Жажды здесь нет — её ведёт отдельный мод.
     */
    private static void applyLifeSupport(ServerPlayer player, ZoneInfo zone, int intervalTicks, PlayerState st) {
        if (zone == null || !BunkerConfig.LIFE_SUPPORT_ENABLED.get()) return;
        int duration = intervalTicks + 20;
        long now = player.serverLevel().getGameTime();
        double air = zone.bunker().air;
        if (air < 50) player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, 0, true, false, true));
        if (air < 25) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 0, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0, true, false, true));
        }
        if (air <= 0 && now - st.lastStaleAirDamage >= 200) {
            st.lastStaleAirDamage = now;
            player.hurt(new DamageSource(player.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(ModMisc.STALE_AIR_DAMAGE)), 1.0f);
        }
    }

    public static DamageSource radiationDamage(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ModMisc.RADIATION_DAMAGE));
    }

    /** Износ костюма: 1 ед. прочности каждой надетой части раз в suit.wearInterval секунд под радиацией. */
    private static void wearSuit(ServerPlayer player, PlayerState st, int intervalTicks) {
        if (!BunkerConfig.SUIT_DURABILITY_LOSS.get()) return;
        boolean wearing = false;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (player.getItemBySlot(slot).getItem() instanceof RadSuitItem) {
                wearing = true;
                break;
            }
        }
        if (!wearing) {
            st.wearTicks = 0;
            return;
        }
        st.wearTicks += intervalTicks;
        int period = BunkerConfig.SUIT_WEAR_INTERVAL.get() * 20;
        if (st.wearTicks < period) return;
        st.wearTicks -= period;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.getItem() instanceof RadSuitItem && !RadSuitItem.isBroken(stack)) {
                stack.hurtAndBreak(1, player, slot);
            }
        }
    }

    private static void sync(ServerPlayer player, PlayerState st, double dose, double max, int stage, Exposure exposure) {
        long now = player.serverLevel().getGameTime();
        float rate = (float) exposure.rate();
        int source = exposure.source().ordinal();
        BunkerManager.ZoneInfo zone = exposure.zone();
        boolean life = zone != null && BunkerConfig.LIFE_SUPPORT_ENABLED.get();
        int air = life ? (int) Math.round(zone.bunker().air) : -1;
        int areaIndex = zone == null ? -1 : zone.area() == zone.bunker().coreArea ? 0 : zone.bunker().roomIndex(zone.area().anchor);
        String areaName = zone == null ? "" : zone.area().name;
        boolean unpowered = zone != null && !zone.protectedHere();
        boolean changed = st.forceSync
                || air != st.sentAir || areaIndex != st.sentArea
                || !areaName.equals(st.sentAreaName) || unpowered != st.sentUnpowered
                || Math.abs(dose - st.sentDose) >= 0.01
                || stage != st.sentStage
                || Math.abs(rate - st.sentRate) >= 0.001f
                || source != st.sentSource
                || max != st.sentMax;
        if (!changed || (!st.forceSync && now - st.lastSync < 20)) return;
        st.forceSync = false;
        st.lastSync = now;
        st.sentDose = dose;
        st.sentStage = stage;
        st.sentRate = rate;
        st.sentSource = source;
        st.sentMax = max;
        st.sentAir = air;
        st.sentArea = areaIndex;
        st.sentAreaName = areaName;
        st.sentUnpowered = unpowered;
        PacketDistributor.sendToPlayer(player, new RadiationSyncPayload(dose, max, stage, rate, source,
                air, areaIndex, areaName, unpowered));
    }

    // ---------------------------------------------------------------- эффекты стадий

    private static List<ParsedEffect> stageEffects(int stage) {
        List<? extends String> src = switch (stage) {
            case 1 -> BunkerConfig.STAGE1_EFFECTS.get();
            case 2 -> BunkerConfig.STAGE2_EFFECTS.get();
            case 3 -> BunkerConfig.STAGE3_EFFECTS.get();
            default -> BunkerConfig.STAGE4_EFFECTS.get();
        };
        if (effectSources.get(stage) != src) {
            effectSources.put(stage, src);
            parsedEffects.put(stage, parseEffects(src));
        }
        return parsedEffects.get(stage);
    }

    private static List<ParsedEffect> parseEffects(List<? extends String> lines) {
        List<ParsedEffect> out = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");
            ResourceLocation id = ResourceLocation.tryParse(parts[0]);
            int amp = 0;
            try {
                if (parts.length > 1) amp = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
            Holder<MobEffect> holder = id == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(id).map(h -> (Holder<MobEffect>) h).orElse(null);
            if (holder == null) {
                BunkerMod.LOGGER.warn("Неизвестный эффект в конфиге стадий радиации: '{}'", line);
                continue;
            }
            out.add(new ParsedEffect(holder, Math.max(0, amp)));
        }
        return List.copyOf(out);
    }

    // ---------------------------------------------------------------- утилиты

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "0";
        };
    }

    public static ChatFormatting stageColor(int stage) {
        return switch (stage) {
            case 1 -> ChatFormatting.YELLOW;
            case 2 -> ChatFormatting.GOLD;
            case 3 -> ChatFormatting.RED;
            case 4 -> ChatFormatting.DARK_RED;
            default -> ChatFormatting.GREEN;
        };
    }
}
