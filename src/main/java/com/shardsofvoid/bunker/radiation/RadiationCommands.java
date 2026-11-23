package com.shardsofvoid.bunker.radiation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.shardsofvoid.bunker.config.BunkerConfig;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /radiation — права оператора, уровень 2 (раздел 1.6). */
public final class RadiationCommands {
    private RadiationCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("radiation")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("get")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(RadiationCommands::get)))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                        .executes(ctx -> change(ctx, false)))))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> change(ctx, true)))))
                .then(Commands.literal("rate")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1000))
                                .executes(RadiationCommands::rate)))
                .then(Commands.literal("enable").executes(ctx -> toggle(ctx, true)))
                .then(Commands.literal("disable").executes(ctx -> toggle(ctx, false))));
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        double dose = RadiationSystem.getDose(player);
        double max = RadiationSystem.maxDose();
        int stage = RadiationMath.stageOf(dose, max);
        RadiationSystem.Exposure exposure = RadiationSystem.computeExposure(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.bunker.radiation.get",
                player.getDisplayName(), String.format("%.1f", dose), String.format("%.0f", max),
                RadiationSystem.roman(stage), String.format("%.3f", exposure.rate()),
                Component.translatable("hud.bunker.source." + exposure.source().name().toLowerCase())), false);
        return (int) Math.round(dose);
    }

    private static int change(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        double value = DoubleArgumentType.getDouble(ctx, "value");
        double dose = add ? RadiationSystem.getDose(player) + value : value;
        RadiationSystem.setDose(player, dose);
        double result = RadiationSystem.getDose(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.bunker.radiation.set",
                player.getDisplayName(), String.format("%.1f", result)), true);
        return 1;
    }

    private static int rate(CommandContext<CommandSourceStack> ctx) {
        double value = DoubleArgumentType.getDouble(ctx, "value");
        BunkerConfig.RADIATION_BASE_RATE.set(value);
        BunkerConfig.RADIATION_BASE_RATE.save();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.bunker.radiation.rate", String.format("%.3f", value)), true);
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        BunkerConfig.RADIATION_ENABLED.set(enabled);
        BunkerConfig.RADIATION_ENABLED.save();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                enabled ? "command.bunker.radiation.enabled" : "command.bunker.radiation.disabled"), true);
        return 1;
    }
}
