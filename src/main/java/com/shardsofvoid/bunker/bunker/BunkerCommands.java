package com.shardsofvoid.bunker.bunker;

import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.shardsofvoid.bunker.bunker.BunkerManager.Located;
import com.shardsofvoid.bunker.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * /bunker give|resetcore|setlevel|info|revalidate — операторы (уровень 2).
 * /bunker trust|untrust — владелец, для опции bunkerMetal.ownerOnlyBreak.
 */
public final class BunkerCommands {
    private BunkerCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bunker")
                .then(Commands.literal("give").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(BunkerCommands::give)))
                .then(Commands.literal("resetcore").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(BunkerCommands::resetCore)))
                .then(Commands.literal("setlevel").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("core")
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 3))
                                                .executes(ctx -> setLevel(ctx, false, null))))
                                .then(Commands.literal("room")
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 3))
                                                .executes(ctx -> setLevel(ctx, true, null))
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .executes(ctx -> setLevel(ctx, true, BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))))))
                .then(Commands.literal("info").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(BunkerCommands::info)))
                .then(Commands.literal("revalidate").requires(s -> s.hasPermission(2)).executes(BunkerCommands::revalidate))
                .then(Commands.literal("trust")
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> trust(ctx, true))))
                .then(Commands.literal("untrust")
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> trust(ctx, false)))));
    }

    private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        BunkerEvents.giveCore(player);
        player.setData(ModAttachments.CORE_GIVEN, true);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.bunker.give", player.getDisplayName()), true);
        return 1;
    }

    private static int resetCore(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        player.setData(ModAttachments.CORE_GIVEN, false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.bunker.resetcore", player.getDisplayName()), true);
        return 1;
    }

    private static int setLevel(CommandContext<CommandSourceStack> ctx, boolean room, BlockPos pos) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        int level = IntegerArgumentType.getInteger(ctx, "level");
        List<Located> list = BunkerManager.bunkersOf(ctx.getSource().getServer(), player.getUUID());
        if (list.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.bunker.no_bunker", player.getDisplayName()));
            return 0;
        }
        Located loc = list.get(0);
        BunkerManager m = BunkerManager.get(loc.level());
        Bunker b = loc.bunker();
        // v3 9.3: понижение отклоняется, если область, комнаты или аккумуляторы не влезают в лимиты нового уровня
        List<Component> problems = new java.util.ArrayList<>();
        if (!room) {
            if (level < b.level) {
                BunkerUpgrades.LevelDef def = BunkerUpgrades.core(level);
                if (b.coreArea.expansionUsed() > def.expansion()) {
                    problems.add(Component.translatable("command.bunker.setlevel.area", Component.translatable("gui.bunker.area.core"),
                            b.coreArea.sizeText(), b.coreArea.expansionUsed(), def.expansion()));
                }
                if (b.rooms.size() > def.maxRooms()) {
                    problems.add(Component.translatable("command.bunker.setlevel.rooms", b.rooms.size(), def.maxRooms()));
                }
                int batteries = 0;
                for (com.shardsofvoid.bunker.energy.BunkerMachineBlockEntity be : m.machines()) {
                    if (be.kind == com.shardsofvoid.bunker.energy.MachineKind.BATTERY && b.id.equals(be.bunkerId)) batteries++;
                }
                if (batteries > def.maxBatteries()) {
                    problems.add(Component.translatable("command.bunker.setlevel.batteries", batteries, def.maxBatteries()));
                }
            }
        } else {
            for (java.util.Map.Entry<BlockPos, Area> e : b.rooms.entrySet()) {
                if (pos != null && !pos.equals(e.getKey())) continue;
                Area a = e.getValue();
                int budget = BunkerUpgrades.room(level).expansion();
                if (level < a.level && a.expansionUsed() > budget) {
                    problems.add(Component.translatable("command.bunker.setlevel.area",
                            com.shardsofvoid.bunker.energy.EnergySystem.roomName(b, a), a.sizeText(), a.expansionUsed(), budget));
                }
            }
        }
        if (!problems.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.bunker.setlevel.denied"));
            for (Component c : problems) ctx.getSource().sendFailure(Component.literal(" • ").append(c));
            return 0;
        }

        int changed = 0;
        if (!room) {
            m.setCoreLevel(b, level);
            changed = 1;
        } else {
            for (BlockPos r : List.copyOf(b.rooms.keySet())) {
                if (pos == null || pos.equals(r)) {
                    m.setRoomLevel(b, r, level);
                    changed++;
                }
            }
        }
        int count = changed;
        ctx.getSource().sendSuccess(() -> Component.translatable("command.bunker.setlevel", player.getDisplayName(),
                room ? "room" : "core", level, count), true);
        return count;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        List<Located> list = BunkerManager.bunkersOf(ctx.getSource().getServer(), player.getUUID());
        if (list.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.bunker.no_bunker", player.getDisplayName()));
            return 0;
        }
        for (Located l : list) {
            Component line = Component.literal("[" + l.level().dimension().location() + "] ").withStyle(ChatFormatting.GRAY)
                    .append(BunkerActions.describe(l.bunker()));
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return list.size();
    }

    private static int revalidate(CommandContext<CommandSourceStack> ctx) {
        int n = 0;
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            BunkerManager m = BunkerManager.get(level);
            for (Bunker b : m.all()) {
                m.schedule(b);
                n++;
            }
        }
        int total = n;
        ctx.getSource().sendSuccess(() -> Component.translatable("command.bunker.revalidate", total), true);
        return total;
    }

    private static int trust(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        List<Located> list = BunkerManager.bunkersOf(ctx.getSource().getServer(), self.getUUID());
        if (list.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.bunker.no_own_bunker"));
            return 0;
        }
        for (Located l : list) {
            if (add) l.bunker().trusted.add(target.getUUID());
            else l.bunker().trusted.remove(target.getUUID());
            BunkerManager.get(l.level()).markDirty();
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(add ? "command.bunker.trusted" : "command.bunker.untrusted",
                target.getDisplayName()), false);
        return 1;
    }
}
