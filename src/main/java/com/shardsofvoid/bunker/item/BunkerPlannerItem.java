package com.shardsofvoid.bunker.item;

import com.shardsofvoid.bunker.bunker.BunkerPlanner;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Пульт проектирования ставится только вплотную к ядру бункера (любой из 6 сторон). */
public class BunkerPlannerItem extends BlockItem {
    public BunkerPlannerItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!BunkerPlanner.hasCoreNeighbour(context.getLevel(), context.getClickedPos())) {
            if (context.getPlayer() != null && !context.getLevel().isClientSide) {
                context.getPlayer().displayClientMessage(Component.translatable("message.bunker.planner.place").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }
}
