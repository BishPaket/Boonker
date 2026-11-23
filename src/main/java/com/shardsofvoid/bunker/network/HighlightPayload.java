package com.shardsofvoid.bunker.network;

import java.util.List;

import com.shardsofvoid.bunker.BunkerMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Сервер → клиент: подсветка. boxes — пары (min, max) кубов бункера; missing — до 64 недостающих
 * позиций оболочки; allowed — центры допустимых комнат. durationTicks = 0 — снять подсветку.
 */
public record HighlightPayload(List<BlockPos> boxes, List<BlockPos> missing, List<BlockPos> allowed, int durationTicks)
        implements CustomPacketPayload {
    public static final int MAX_MARKERS = 64;

    public static final Type<HighlightPayload> TYPE = new Type<>(BunkerMod.id("highlight"));

    public static final StreamCodec<ByteBuf, HighlightPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(64)), HighlightPayload::boxes,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MARKERS)), HighlightPayload::missing,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MARKERS)), HighlightPayload::allowed,
            ByteBufCodecs.VAR_INT, HighlightPayload::durationTicks,
            HighlightPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
