package com.shardsofvoid.bunker.client;

import java.util.List;

import com.shardsofvoid.bunker.radiation.RadiationSource;

import net.minecraft.core.BlockPos;

/** Клиентское состояние: только то, что прислал сервер. Клиент ничего не решает сам. */
public final class ClientState {
    public static double dose;
    public static double maxDose = 100;
    public static int stage;
    public static float rate;
    public static RadiationSource source = RadiationSource.NONE;
    public static boolean hasData;

    /** v3 8.7 / 10.2: жизнеобеспечение бункера (−1 — вне зоны или выключено), текущая область. */
    public static int air = -1;
    public static int areaIndex = -1;
    public static String areaName = "";
    public static boolean unpowered;
    /** Когда показывать имя области над хотбаром (v3 6.3: 2 секунды при переходе). */
    public static long areaTitleUntil;

    public static List<BlockPos> boxes = List.of();
    public static List<BlockPos> missing = List.of();
    public static List<BlockPos> allowed = List.of();
    public static long highlightUntil;

    private ClientState() {}

    public static void reset() {
        dose = 0;
        maxDose = 100;
        stage = 0;
        rate = 0;
        source = RadiationSource.NONE;
        hasData = false;
        air = -1;
        areaIndex = -1;
        areaName = "";
        unpowered = false;
        areaTitleUntil = 0;
        boxes = List.of();
        missing = List.of();
        allowed = List.of();
        highlightUntil = 0;
    }
}
