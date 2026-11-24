package com.shardsofvoid.bunker.client;

import com.shardsofvoid.bunker.network.BunkerScreenPayload;
import com.shardsofvoid.bunker.network.HighlightPayload;
import com.shardsofvoid.bunker.network.RadiationSyncPayload;
import com.shardsofvoid.bunker.radiation.RadiationSource;

import com.shardsofvoid.bunker.menu.MachineMenu;
import com.shardsofvoid.bunker.network.MachineDataPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
        ClientState.air = p.air();
        ClientState.unpowered = p.unpowered();
        if (p.areaIndex() != ClientState.areaIndex || !p.areaName().equals(ClientState.areaName)) {
            boolean moved = p.areaIndex() != ClientState.areaIndex;
            ClientState.areaIndex = p.areaIndex();
            ClientState.areaName = p.areaName();
            Minecraft mc = Minecraft.getInstance();
            if (moved && p.areaIndex() >= 0 && mc.player != null) {
                mc.player.displayClientMessage(areaTitle(p.areaIndex(), p.areaName(), p.unpowered()), true);
            }
        }
    }

    /** Имя области: своё или по умолчанию («Центральная», «Комната N»). */
    public static Component areaTitle(int index, String name, boolean unpowered) {
        MutableComponent c = name.isEmpty()
                ? (index == 0 ? Component.translatable("gui.bunker.area.core") : Component.translatable("gui.bunker.area.room", index))
                : Component.literal(name);
        c = c.withStyle(ChatFormatting.AQUA);
        if (unpowered) c.append(Component.literal(" — ").append(Component.translatable("hud.bunker.unpowered")).withStyle(ChatFormatting.RED));
        return c;
    }

    public static void handleMachineData(MachineDataPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof MachineMenu menu && menu.containerId == p.containerId()) {
            menu.data = p.values();
        }
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
        if (p.data().getBoolean("planner")) {
            if (mc.screen instanceof PlannerScreen screen && screen.pos().equals(p.pos())) screen.update(p.data());
            else if (p.open()) mc.setScreen(new PlannerScreen(p.pos(), p.data()));
            return;
        }
        if (mc.screen instanceof BunkerScreen screen && screen.pos().equals(p.pos())) {
            screen.update(p);
        } else if (p.open()) {
            mc.setScreen(new BunkerScreen(p));
        }
    }
}
