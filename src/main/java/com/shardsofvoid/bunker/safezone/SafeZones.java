package com.shardsofvoid.bunker.safezone;

import java.util.List;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.network.BunkerActionPayload;
import com.shardsofvoid.bunker.network.BunkerScreenPayload;
import com.shardsofvoid.bunker.network.HighlightPayload;
import com.shardsofvoid.bunker.registry.ModBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Интерфейс блока безопасной зоны: размер по X/Y/Z и показ границ. Только для операторов. */
public final class SafeZones {
    private SafeZones() {}

    public static boolean isZoneBlock(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.SAFE_ZONE.get());
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.bunker.safe_zone.ops_only").withStyle(ChatFormatting.RED), true);
            return;
        }
        SafeZoneData data = SafeZoneData.get(player.serverLevel());
        if (data.zone(pos) == null) {
            int r = BunkerConfig.SAFE_ZONE_DEFAULT_RADIUS.get();
            data.put(pos, r, r, r);
        }
        send(player, pos, true);
        highlight(player, data.zone(pos));
    }

    public static void handle(ServerPlayer player, BunkerActionPayload payload) {
        if (!player.hasPermissions(2)) return;
        BlockPos pos = payload.pos();
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 16 * 16) return;
        SafeZoneData data = SafeZoneData.get(player.serverLevel());
        SafeZoneData.Zone z = data.zone(pos);
        if (z == null) return;
        CompoundTag args = payload.args();
        switch (args.getString("a")) {
            case "radius" -> {
                int axis = args.getInt("axis");
                int delta = args.getInt("delta");
                int max = BunkerConfig.SAFE_ZONE_MAX_RADIUS.get();
                int rx = z.rx(), ry = z.ry(), rz = z.rz();
                if (axis == 0 || axis == 3) rx = clamp(rx + delta, max);
                if (axis == 1 || axis == 3) ry = clamp(ry + delta, max);
                if (axis == 2 || axis == 3) rz = clamp(rz + delta, max);
                data.put(pos, rx, ry, rz);
                highlight(player, data.zone(pos));
            }
            case "show" -> highlight(player, z);
            case "close" -> {
                return;
            }
            default -> {
                return;
            }
        }
        send(player, pos, false);
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }

    private static void send(ServerPlayer player, BlockPos pos, boolean open) {
        SafeZoneData.Zone z = SafeZoneData.get(player.serverLevel()).zone(pos);
        CompoundTag d = new CompoundTag();
        d.putBoolean("safezone", true);
        if (z != null) d.putIntArray("r", new int[] {z.rx(), z.ry(), z.rz()});
        d.putInt("max", BunkerConfig.SAFE_ZONE_MAX_RADIUS.get());
        PacketDistributor.sendToPlayer(player, new BunkerScreenPayload(pos, false, open, 0, d));
    }

    private static void highlight(ServerPlayer player, SafeZoneData.Zone z) {
        if (z == null) return;
        BlockPos c = z.center();
        List<BlockPos> box = List.of(c.offset(-z.rx(), -z.ry(), -z.rz()), c.offset(z.rx(), z.ry(), z.rz()));
        PacketDistributor.sendToPlayer(player, new HighlightPayload(box, List.of(), List.of(c), 30 * 20));
    }
}
