package com.shardsofvoid.bunker.compat;

import java.lang.reflect.Method;

import com.shardsofvoid.bunker.BunkerMod;

import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Определение кислотного дождя Better Weather без compile-зависимости (через рефлексию).
 * Better Weather вешает на ServerLevel интерфейс BetterWeatherWorldData с контекстом погодных событий.
 */
public final class BetterWeatherCompat {
    private static boolean initialized;
    private static boolean available;
    private static Class<?> worldDataInterface;
    private static Class<?> acidRainClass;
    private static Method getContext;
    private static Method getCurrentEvent;

    private BetterWeatherCompat() {}

    private static void init() {
        initialized = true;
        if (!ModList.get().isLoaded("betterweather")) return;
        try {
            worldDataInterface = Class.forName("corgitaco.betterweather.helpers.BetterWeatherWorldData");
            acidRainClass = Class.forName("corgitaco.betterweather.weather.event.AcidRain");
            getContext = worldDataInterface.getMethod("getWeatherEventContext");
            getCurrentEvent = Class.forName("corgitaco.betterweather.weather.BWWeatherEventContext").getMethod("getCurrentEvent");
            available = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            BunkerMod.LOGGER.warn("Better Weather найден, но API кислотного дождя не распознан: {}", e.toString());
        }
    }

    public static boolean isAcidRain(Level level) {
        if (!initialized) init();
        if (!available || !worldDataInterface.isInstance(level)) return false;
        try {
            Object ctx = getContext.invoke(level);
            if (ctx == null) return false;
            Object event = getCurrentEvent.invoke(ctx);
            return event != null && acidRainClass.isInstance(event);
        } catch (ReflectiveOperationException | RuntimeException e) {
            available = false;
            BunkerMod.LOGGER.warn("Ошибка при чтении погоды Better Weather, множитель кислотного дождя отключён: {}", e.toString());
            return false;
        }
    }
}
