package com.shardsofvoid.bunker.client;

import com.shardsofvoid.bunker.network.BunkerScreenPayload;
import com.shardsofvoid.bunker.network.HighlightPayload;
import com.shardsofvoid.bunker.network.RadiationSyncPayload;
import com.shardsofvoid.bunker.radiation.RadiationSource;

import net.minecraft.client.Minecraft;

/** Обработка пакетов на клиенте (вызывается на главном потоке клиента). */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}

    public static void handleRadiation(RadiationSyncPayload p) {
        ClientState.dose = p.dose();
        ClientState.maxDose = p.maxDose();
        ClientState.stage = p.stage();
        ClientState.rate = p.rate();
        ClientState.source = RadiationSource.byId(p.source());
        ClientState.hasData = true;
    }

    public static void handleHighlight(HighlightPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ClientState.boxes = p.boxes();
        ClientState.missing = p.missing();
        ClientState.allowed = p.allowed();
        ClientState.highlightUntil = p.durationTicks() <= 0 ? 0 : mc.level.getGameTime() + p.durationTicks();
    }

    public static void handleScreen(BunkerScreenPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BunkerScreen screen && screen.pos().equals(p.pos())) {
            screen.update(p);
        } else if (p.open()) {
            mc.setScreen(new BunkerScreen(p));
        }
    }
}
