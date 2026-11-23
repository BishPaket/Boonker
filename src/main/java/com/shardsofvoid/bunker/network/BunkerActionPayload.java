package com.shardsofvoid.bunker.network;

import com.shardsofvoid.bunker.BunkerMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент → сервер: кнопки интерфейса ядра и комнаты. Каждый пакет перепроверяется на сервере. */
public record BunkerActionPayload(BlockPos pos, int action, BlockPos arg) implements CustomPacketPayload {
    public static final int SHOW_BOUNDS = 0;
    public static final int UPGRADE = 1;
    public static final int UNLINK_ROOM = 2;
    public static final int REFRESH = 3;

    public static final Type<BunkerActionPayload> TYPE = new Type<>(BunkerMod.id("bunker_action"));

    public static final StreamCodec<ByteBuf, BunkerActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BunkerActionPayload::pos,
            ByteBufCodecs.VAR_INT, BunkerActionPayload::action,
            BlockPos.STREAM_CODEC, BunkerActionPayload::arg,
            BunkerActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
