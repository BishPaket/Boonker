package com.shardsofvoid.bunker.item;

import java.util.List;

import com.shardsofvoid.bunker.bunker.BunkerManager;
import com.shardsofvoid.bunker.registry.ModComponents;
import com.shardsofvoid.bunker.registry.ModComponents.LinkedCore;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * Блок комнат бункера. Привязка — ПКМ по своему ядру (обрабатывает блок ядра), предмет при этом не тратится.
 * Непривязанный блок поставить нельзя; остальные условия установки проверяет сервер.
 */
public class BunkerRoomItem extends BlockItem {
    public BunkerRoomItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Player player = context.getPlayer();
        LinkedCore link = context.getItemInHand().get(ModComponents.LINKED_CORE.get());
        if (link == null) {
            if (player != null && !context.getLevel().isClientSide) {
                player.displayClientMessage(Component.translatable("message.bunker.room.unlinked").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (!context.getLevel().isClientSide && context.getLevel() instanceof ServerLevel level) {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.FAIL;
            // v3 1.4: нет ни одного варианта области — установка отменяется, предмет остаётся у игрока
            BunkerManager.Placement placement = BunkerManager.checkRoomPlacement(sp, level, context.getClickedPos(), link);
            if (placement.error() != null) {
                sp.displayClientMessage(placement.error(), true);
                sp.sendSystemMessage(placement.error());
                return InteractionResult.FAIL;
            }
        }
        return super.place(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        LinkedCore link = stack.get(ModComponents.LINKED_CORE.get());
        if (link != null) {
            tooltip.add(Component.translatable("tooltip.bunker.room.linked",
                    link.pos().getX(), link.pos().getY(), link.pos().getZ()).withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.bunker.room.unlinked").withStyle(ChatFormatting.RED));
        }
        Integer level = stack.get(ModComponents.ROOM_LEVEL.get());
        if (level != null && level > 1) {
            tooltip.add(Component.translatable("tooltip.bunker.room.level", com.shardsofvoid.bunker.radiation.RadiationSystem.roman(level))
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
