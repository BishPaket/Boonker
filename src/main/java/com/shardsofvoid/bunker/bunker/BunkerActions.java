package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.bunker.Bunker.State;
import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.network.BunkerActionPayload;
import com.shardsofvoid.bunker.network.BunkerScreenPayload;
import com.shardsofvoid.bunker.registry.ModBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Интерфейс ядра и комнаты: открытие, кнопки, улучшения. Всё перепроверяется на сервере (разделы 4.5, 7, 8.2). */
public final class BunkerActions {
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private BunkerActions() {}

    /** Контекст блока: менеджер, бункер, комната ли это. */
    private record Target(BunkerManager manager, @Nullable Bunker bunker, boolean room, BlockPos pos) {}

    @Nullable
    private static Target resolve(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        BunkerManager m = BunkerManager.get(level);
        if (state.is(ModBlocks.BUNKER_CORE.get())) return new Target(m, m.byCore(pos), false, pos);
        if (state.is(ModBlocks.BUNKER_ROOM.get())) return new Target(m, m.byRoom(pos), true, pos);
        return null;
    }

    private static boolean isOp(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    private static boolean inReach(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= MAX_DISTANCE_SQR;
    }

    public static void openScreen(ServerPlayer player, BlockPos pos) {
        Target t = resolve(player.serverLevel(), pos);
        if (t == null) return;
        if (t.bunker() == null) {
            // блок комнаты, отвязанный от ядра, или ядро без данных
            player.displayClientMessage(Component.translatable(t.room() ? "message.bunker.room.inactive" : "message.bunker.core.orphan")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Bunker b = t.bunker();
        if (!b.owner.equals(player.getUUID()) && !isOp(player)) {
            player.displayClientMessage(Component.translatable("message.bunker.foreign", b.ownerName).withStyle(ChatFormatting.RED), true);
            return;
        }
        t.manager().validateNow(b); // раздел 5.2: проверка при открытии интерфейса
        if (b.state != State.NOT_READY) t.manager().sendHighlight(player, b, false); // подсветка на 60 секунд
        send(player, t, true);
    }

    public static void handle(ServerPlayer player, BunkerActionPayload payload) {
        ServerLevel level = player.serverLevel();
        Target t = resolve(level, payload.pos());
        if (t == null || t.bunker() == null) return;
        Bunker b = t.bunker();
        if (!b.owner.equals(player.getUUID()) && !isOp(player)) return;
        if (!inReach(player, payload.pos())) return;

        switch (payload.action()) {
            case BunkerActionPayload.SHOW_BOUNDS -> {
                t.manager().validateNow(b);
                t.manager().sendHighlight(player, b, true);
            }
            case BunkerActionPayload.UPGRADE -> upgrade(player, t);
            case BunkerActionPayload.UNLINK_ROOM -> {
                if (!t.room() && b.rooms.containsKey(payload.arg())) {
                    t.manager().unlinkRoom(b, payload.arg());
                    player.displayClientMessage(Component.translatable("message.bunker.room.unlinked_done",
                            payload.arg().getX(), payload.arg().getY(), payload.arg().getZ()).withStyle(ChatFormatting.YELLOW), true);
                }
            }
            case BunkerActionPayload.REFRESH -> t.manager().validateNow(b);
            default -> {
                return;
            }
        }
        if (t.bunker() != null && t.manager().byId(b.id) != null) send(player, t, false);
    }

    private static int currentLevel(Target t) {
        Bunker b = t.bunker();
        if (!t.room()) return b.level;
        Integer lvl = b.rooms.get(t.pos());
        return lvl == null ? 1 : lvl;
    }

    private static BunkerUpgrades.LevelDef nextDef(Target t) {
        int next = currentLevel(t) + 1;
        return t.room() ? BunkerUpgrades.room(next) : BunkerUpgrades.core(next);
    }

    /** null — можно улучшать, иначе причина. */
    @Nullable
    private static Component upgradeBlocker(ServerPlayer player, Target t) {
        Bunker b = t.bunker();
        if (currentLevel(t) >= 3) return Component.translatable("gui.bunker.upgrade.max");
        if (BunkerConfig.UPGRADE_REQUIRES_ACTIVE.get() && b.state != State.ACTIVE) {
            return Component.translatable("gui.bunker.upgrade.need_active");
        }
        if (!player.hasInfiniteMaterials()) {
            for (BunkerUpgrades.Cost c : nextDef(t).cost()) {
                if (c.countIn(player) < c.count()) return Component.translatable("gui.bunker.upgrade.not_enough");
            }
        }
        return null;
    }

    private static void upgrade(ServerPlayer player, Target t) {
        if (upgradeBlocker(player, t) != null) return;
        Bunker b = t.bunker();
        BunkerUpgrades.LevelDef def = nextDef(t);
        if (!player.hasInfiniteMaterials()) {
            for (BunkerUpgrades.Cost c : def.cost()) take(player, c);
        }
        if (t.room()) {
            t.manager().setRoomLevel(b, t.pos(), def.level());
        } else {
            t.manager().setCoreLevel(b, def.level());
        }
        player.serverLevel().playSound(null, t.pos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.bunker.upgraded", romanOf(def.level())).withStyle(ChatFormatting.AQUA), true);
    }

    private static void take(ServerPlayer player, BunkerUpgrades.Cost cost) {
        int left = cost.count();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && cost.test(s)) {
                int n = Math.min(left, s.getCount());
                s.shrink(n);
                left -= n;
            }
        }
        inv.setChanged();
    }

    private static String romanOf(int level) {
        return com.shardsofvoid.bunker.radiation.RadiationSystem.roman(level);
    }

    /** Собрать и отправить данные интерфейса. open = true — открыть экран, иначе только обновить открытый. */
    private static void send(ServerPlayer player, Target t, boolean open) {
        Bunker b = t.bunker();
        BunkerManager m = t.manager();

        List<BunkerScreenPayload.Condition> conditions = new ArrayList<>();
        for (ActivationConditions.Result r : b.lastConditions) {
            conditions.add(new BunkerScreenPayload.Condition(r.label(), r.value(), r.ok()));
        }

        Component sealInfo = switch (b.lastOutcome) {
            case SEALED -> Component.translatable("gui.bunker.seal.sealed");
            case LEAK -> Component.translatable("gui.bunker.seal.leak");
            case TOO_BIG -> Component.translatable("gui.bunker.seal.too_big");
            case UNLOADED -> Component.translatable("gui.bunker.seal.unloaded");
        };

        List<BunkerScreenPayload.RoomEntry> rooms = new ArrayList<>();
        for (Map.Entry<BlockPos, Integer> e : b.rooms.entrySet()) rooms.add(new BunkerScreenPayload.RoomEntry(e.getKey(), e.getValue()));

        int level = currentLevel(t);
        List<BunkerScreenPayload.Cost> cost = new ArrayList<>();
        int nextLevel = 0;
        if (level < 3) {
            BunkerUpgrades.LevelDef def = nextDef(t);
            nextLevel = def.level();
            for (BunkerUpgrades.Cost c : def.cost()) {
                cost.add(new BunkerScreenPayload.Cost(c.displayStack(), player.hasInfiniteMaterials() ? c.count() : c.countIn(player)));
            }
        }
        Component blocker = upgradeBlocker(player, t);
        Component upgradeInfo;
        if (blocker != null) {
            upgradeInfo = blocker;
        } else if (t.room()) {
            upgradeInfo = Component.translatable("gui.bunker.upgrade.room_result", romanOf(nextLevel));
        } else {
            upgradeInfo = Component.translatable("gui.bunker.upgrade.core_result", romanOf(nextLevel),
                    BunkerUpgrades.maxRooms(nextLevel));
        }

        List<BlockPos> allowed = !t.room() && b.state == State.ACTIVE ? m.allowedRoomCenters(b) : List.of();

        BunkerScreenPayload payload = new BunkerScreenPayload(
                t.pos(), t.room(), open, b.state.ordinal(), b.ownerName,
                b.owner.equals(player.getUUID()) || isOp(player),
                conditions, b.shellPresent, b.shellExpected, b.sealed, sealInfo,
                level, b.rooms.size(), BunkerUpgrades.maxRooms(b.level), rooms, b.core,
                nextLevel, cost, blocker == null, upgradeInfo, allowed);
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** Для команды /bunker info. */
    public static Component describe(Bunker b) {
        return Component.translatable("command.bunker.info.line", b.ownerName, b.core.getX(), b.core.getY(), b.core.getZ(),
                Component.translatable("gui.bunker.state." + b.state.name().toLowerCase()), romanOf(b.level), b.rooms.size(),
                BunkerUpgrades.maxRooms(b.level), b.shellPresent, b.shellExpected);
    }
}
