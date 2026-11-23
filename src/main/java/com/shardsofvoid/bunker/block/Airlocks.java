package com.shardsofvoid.bunker.block;

import com.shardsofvoid.bunker.bunker.Bunker;
import com.shardsofvoid.bunker.bunker.BunkerManager;
import com.shardsofvoid.bunker.config.BunkerConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Общие правила гермодвери и гермолюка (v3 2.2–2.3). */
public final class Airlocks {
    /** Как у железной двери, но открывается рукой. */
    public static final BlockSetType TYPE = BlockSetType.register(new BlockSetType("bunker_airlock", true, false, false,
            BlockSetType.PressurePlateSensitivity.MOBS, SoundType.NETHERITE_BLOCK,
            SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN,
            SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
            SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON));

    private Airlocks() {}

    /** Запертую дверь снаружи открывают только владелец и операторы, изнутри — любой игрок. Работает без энергии. */
    public static boolean mayOpen(ServerLevel level, BlockPos pos, Player player) {
        Bunker b = BunkerManager.get(level).watchedAt(pos);
        if (b == null || !b.doorsLocked) return true;
        if (b.owner.equals(player.getUUID()) || player.hasPermissions(2)) return true;
        BlockPos eye = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        if (BunkerManager.get(level).inZone(b, eye)) return true;
        player.displayClientMessage(Component.translatable("message.bunker.door_locked", b.ownerName).withStyle(ChatFormatting.RED), true);
        return false;
    }

    public static int autoCloseTicks() {
        return BunkerConfig.DOOR_AUTO_CLOSE_SECONDS.get() * 20;
    }

    public static void toggled(ServerLevel level, BlockPos pos, boolean open) {
        BunkerManager m = BunkerManager.get(level);
        m.onAirlockToggled(pos, open);
        m.onAirlockToggled(pos.above(), open);
        m.onAirlockToggled(pos.below(), open);
    }
}
