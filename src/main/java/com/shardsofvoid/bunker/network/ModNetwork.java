package com.shardsofvoid.bunker.network;

import com.shardsofvoid.bunker.bunker.BunkerActions;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Регистрация пакетов (CustomPacketPayload NeoForge 1.21.1).
 * Клиентские обработчики вызываются через лямбды, чтобы клиентские классы не грузились на выделенном сервере.
 */
public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(RadiationSyncPayload.TYPE, RadiationSyncPayload.STREAM_CODEC,
                (payload, ctx) -> com.shardsofvoid.bunker.client.ClientPayloadHandler.handleRadiation(payload));
        registrar.playToClient(BunkerScreenPayload.TYPE, BunkerScreenPayload.STREAM_CODEC,
                (payload, ctx) -> com.shardsofvoid.bunker.client.ClientPayloadHandler.handleScreen(payload));
        registrar.playToClient(MachineDataPayload.TYPE, MachineDataPayload.STREAM_CODEC,
                (payload, ctx) -> com.shardsofvoid.bunker.client.ClientPayloadHandler.handleMachineData(payload));
        registrar.playToClient(HighlightPayload.TYPE, HighlightPayload.STREAM_CODEC,
                (payload, ctx) -> com.shardsofvoid.bunker.client.ClientPayloadHandler.handleHighlight(payload));

        registrar.playToServer(BunkerActionPayload.TYPE, BunkerActionPayload.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                BunkerActions.handle(player, payload);
            }
        });
    }
}
