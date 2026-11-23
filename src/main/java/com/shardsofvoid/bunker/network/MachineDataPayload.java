package com.shardsofvoid.bunker.network;

import com.shardsofvoid.bunker.BunkerMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер → клиент: числа для открытого экрана энергоблока (заряд, баки, ресурс фильтра и т. п.). */
public record MachineDataPayload(int containerId, int[] values) implements CustomPacketPayload {
    public static final Type<MachineDataPayload> TYPE = new Type<>(BunkerMod.id("machine_data"));

    public static final StreamCodec<ByteBuf, MachineDataPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.containerId);
                ByteBufCodecs.VAR_INT.encode(buf, p.values.length);
                for (int v : p.values) ByteBufCodecs.VAR_INT.encode(buf, v);
            },
            buf -> {
                int id = ByteBufCodecs.VAR_INT.decode(buf);
                int n = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 32);
                int[] values = new int[n];
                for (int i = 0; i < n; i++) values[i] = ByteBufCodecs.VAR_INT.decode(buf);
                return new MachineDataPayload(id, values);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
