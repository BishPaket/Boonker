package com.shardsofvoid.bunker.client;

import net.neoforged.fml.ModList;

/** Есть ли в сборке мод с миникартой в правом верхнем углу. */
final class MinimapCompat {
    private static Boolean present;

    private MinimapCompat() {}

    static boolean present() {
        if (present == null) {
            ModList mods = ModList.get();
            present = mods.isLoaded("ftbchunks") || mods.isLoaded("xaerominimap") || mods.isLoaded("xaerominimapfair")
                    || mods.isLoaded("journeymap");
        }
        return present;
    }
}
