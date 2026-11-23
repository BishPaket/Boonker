package com.shardsofvoid.bunker.network;

import java.util.ArrayList;
import java.util.List;

import com.shardsofvoid.bunker.BunkerMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Сервер → клиент: всё, что показывает интерфейс ядра или блока комнат. Клиент ничего не считает сам.
 * Если экран этого блока уже открыт, он просто обновляется.
 */
public record BunkerScreenPayload(
        BlockPos pos,
        boolean room,
        boolean open,
        int state,
        String ownerName,
        boolean canEdit,
        List<Condition> conditions,
        int shellPresent,
        int shellExpected,
        boolean sealed,
        Component sealInfo,
        int level,
        int roomCount,
        int roomLimit,
        List<RoomEntry> rooms,
        BlockPos corePos,
        int nextLevel,
        List<Cost> cost,
        boolean canUpgrade,
        Component upgradeInfo,
        List<BlockPos> allowedRooms
) implements CustomPacketPayload {

    public record Condition(Component label, Component value, boolean ok) {}

    public record RoomEntry(BlockPos pos, int level) {}

    public record Cost(ItemStack stack, int have) {}

    public static final Type<BunkerScreenPayload> TYPE = new Type<>(BunkerMod.id("bunker_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BunkerScreenPayload> STREAM_CODEC =
            StreamCodec.of(BunkerScreenPayload::write, BunkerScreenPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, BunkerScreenPayload p) {
        buf.writeBlockPos(p.pos);
        buf.writeBoolean(p.room);
        buf.writeBoolean(p.open);
        buf.writeVarInt(p.state);
        buf.writeUtf(p.ownerName, 64);
        buf.writeBoolean(p.canEdit);
        buf.writeVarInt(p.conditions.size());
        for (Condition c : p.conditions) {
            ComponentSerialization.STREAM_CODEC.encode(buf, c.label);
            ComponentSerialization.STREAM_CODEC.encode(buf, c.value);
            buf.writeBoolean(c.ok);
        }
        buf.writeVarInt(p.shellPresent);
        buf.writeVarInt(p.shellExpected);
        buf.writeBoolean(p.sealed);
        ComponentSerialization.STREAM_CODEC.encode(buf, p.sealInfo);
        buf.writeVarInt(p.level);
        buf.writeVarInt(p.roomCount);
        buf.writeVarInt(p.roomLimit);
        buf.writeVarInt(p.rooms.size());
        for (RoomEntry r : p.rooms) {
            buf.writeBlockPos(r.pos);
            buf.writeVarInt(r.level);
        }
        buf.writeBlockPos(p.corePos);
        buf.writeVarInt(p.nextLevel);
        buf.writeVarInt(p.cost.size());
        for (Cost c : p.cost) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, c.stack);
            buf.writeVarInt(c.have);
        }
        buf.writeBoolean(p.canUpgrade);
        ComponentSerialization.STREAM_CODEC.encode(buf, p.upgradeInfo);
        buf.writeVarInt(p.allowedRooms.size());
        for (BlockPos a : p.allowedRooms) buf.writeBlockPos(a);
    }

    private static BunkerScreenPayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean room = buf.readBoolean();
        boolean open = buf.readBoolean();
        int state = buf.readVarInt();
        String owner = buf.readUtf(64);
        boolean canEdit = buf.readBoolean();
        int n = Math.min(buf.readVarInt(), 32);
        List<Condition> conditions = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Component label = ComponentSerialization.STREAM_CODEC.decode(buf);
            Component value = ComponentSerialization.STREAM_CODEC.decode(buf);
            conditions.add(new Condition(label, value, buf.readBoolean()));
        }
        int shellPresent = buf.readVarInt();
        int shellExpected = buf.readVarInt();
        boolean sealed = buf.readBoolean();
        Component sealInfo = ComponentSerialization.STREAM_CODEC.decode(buf);
        int level = buf.readVarInt();
        int roomCount = buf.readVarInt();
        int roomLimit = buf.readVarInt();
        n = Math.min(buf.readVarInt(), 256);
        List<RoomEntry> rooms = new ArrayList<>(n);
        for (int i = 0; i < n; i++) rooms.add(new RoomEntry(buf.readBlockPos(), buf.readVarInt()));
        BlockPos corePos = buf.readBlockPos();
        int nextLevel = buf.readVarInt();
        n = Math.min(buf.readVarInt(), 32);
        List<Cost> cost = new ArrayList<>(n);
        for (int i = 0; i < n; i++) cost.add(new Cost(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt()));
        boolean canUpgrade = buf.readBoolean();
        Component upgradeInfo = ComponentSerialization.STREAM_CODEC.decode(buf);
        n = Math.min(buf.readVarInt(), 64);
        List<BlockPos> allowed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) allowed.add(buf.readBlockPos());
        return new BunkerScreenPayload(pos, room, open, state, owner, canEdit, conditions, shellPresent, shellExpected,
                sealed, sealInfo, level, roomCount, roomLimit, rooms, corePos, nextLevel, cost, canUpgrade, upgradeInfo, allowed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
