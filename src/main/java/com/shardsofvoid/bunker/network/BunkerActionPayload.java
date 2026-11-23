package com.shardsofvoid.bunker.network;

import com.shardsofvoid.bunker.BunkerMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Клиент → сервер: действие интерфейса ядра или комнаты. args.a — имя действия, остальное — его параметры.
 * Каждый пакет перепроверяется на сервере, не больше 10 действий в секунду (v3, 10.3).
 */
public record BunkerActionPayload(BlockPos pos, CompoundTag args) implements CustomPacketPayload {
    public static final Type<BunkerActionPayload> TYPE = new Type<>(BunkerMod.id("bunker_action"));

    public static final StreamCodec<ByteBuf, BunkerActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BunkerActionPayload::pos,
            ByteBufCodecs.COMPOUND_TAG, BunkerActionPayload::args,
            BunkerActionPayload::new);

    public static BunkerActionPayload of(BlockPos pos, String action) {
        CompoundTag t = new CompoundTag();
        t.putString("a", action);
        return new BunkerActionPayload(pos, t);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
