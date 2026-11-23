package com.shardsofvoid.bunker.network;

import com.shardsofvoid.bunker.BunkerMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Сервер → клиент: доза, стадия, скорость набора, источник защиты, а также для HUD бункера (v3 10.2):
 * воздух и вода (−1 — вне зоны / жизнеобеспечение выключено), имя текущей области и признак «обесточена».
 */
public record RadiationSyncPayload(double dose, double maxDose, int stage, float rate, int source,
                                   int air, int water, int areaIndex, String areaName, boolean unpowered) implements CustomPacketPayload {
    public static final Type<RadiationSyncPayload> TYPE = new Type<>(BunkerMod.id("radiation_sync"));

    public static final StreamCodec<ByteBuf, RadiationSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.dose);
                buf.writeDouble(p.maxDose);
                ByteBufCodecs.VAR_INT.encode(buf, p.stage);
                buf.writeFloat(p.rate);
                ByteBufCodecs.VAR_INT.encode(buf, p.source);
                ByteBufCodecs.VAR_INT.encode(buf, p.air);
                ByteBufCodecs.VAR_INT.encode(buf, p.water);
                ByteBufCodecs.VAR_INT.encode(buf, p.areaIndex);
                ByteBufCodecs.stringUtf8(64).encode(buf, p.areaName);
                buf.writeBoolean(p.unpowered);
            },
            buf -> new RadiationSyncPayload(buf.readDouble(), buf.readDouble(), ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.stringUtf8(64).decode(buf), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
