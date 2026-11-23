package com.shardsofvoid.bunker.network;

import com.shardsofvoid.bunker.BunkerMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер → клиент: доза, стадия, текущая скорость набора и источник защиты (для HUD и счётчика Гейгера). */
public record RadiationSyncPayload(double dose, double maxDose, int stage, float rate, int source) implements CustomPacketPayload {
    public static final Type<RadiationSyncPayload> TYPE = new Type<>(BunkerMod.id("radiation_sync"));

    public static final StreamCodec<ByteBuf, RadiationSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, RadiationSyncPayload::dose,
            ByteBufCodecs.DOUBLE, RadiationSyncPayload::maxDose,
            ByteBufCodecs.VAR_INT, RadiationSyncPayload::stage,
            ByteBufCodecs.FLOAT, RadiationSyncPayload::rate,
            ByteBufCodecs.VAR_INT, RadiationSyncPayload::source,
            RadiationSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
