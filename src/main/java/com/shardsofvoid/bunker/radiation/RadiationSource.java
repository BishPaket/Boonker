package com.shardsofvoid.bunker.radiation;

/** Источник защиты для HUD. */
public enum RadiationSource {
    NONE, SUIT, BUNKER, SAFE_ZONE;

    private static final RadiationSource[] VALUES = values();

    public static RadiationSource byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
