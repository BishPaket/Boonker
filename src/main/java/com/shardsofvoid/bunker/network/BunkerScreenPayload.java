package com.shardsofvoid.bunker.network;

import com.shardsofvoid.bunker.BunkerMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Сервер → клиент: данные открытой вкладки интерфейса ядра или блока комнаты (v3, 6 и 10.2).
 * Содержимое data зависит от вкладки; клиент ничего не считает сам. open = true — открыть экран.
 */
public record BunkerScreenPayload(BlockPos pos, boolean room, boolean open, int tab, CompoundTag data) implements CustomPacketPayload {
    public static final Type<BunkerScreenPayload> TYPE = new Type<>(BunkerMod.id("bunker_screen"));

    public static final StreamCodec<ByteBuf, BunkerScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BunkerScreenPayload::pos,
            ByteBufCodecs.BOOL, BunkerScreenPayload::room,
            ByteBufCodecs.BOOL, BunkerScreenPayload::open,
            ByteBufCodecs.VAR_INT, BunkerScreenPayload::tab,
            ByteBufCodecs.COMPOUND_TAG, BunkerScreenPayload::data,
            BunkerScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
