package com.shardsofvoid.bunker.item;

import java.util.List;

import com.shardsofvoid.bunker.registry.ModComponents;
import com.shardsofvoid.bunker.registry.ModComponents.CoreOwner;
import com.shardsofvoid.bunker.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * Предмет ядра бункера. Привязан к владельцу (UUID в компоненте core_owner): поставить ядро может только он.
 * Установка в остальном не ограничивается — условия активации проверяет само ядро.
 */
public class BunkerCoreItem extends BlockItem {
    public BunkerCoreItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack createFor(Player owner) {
        ItemStack stack = new ItemStack(ModItems.BUNKER_CORE.get());
        stack.set(ModComponents.CORE_OWNER.get(), new CoreOwner(owner.getUUID(), owner.getGameProfile().getName()));
        return stack;
    }

    public static ItemStack createFor(java.util.UUID id, String name) {
        ItemStack stack = new ItemStack(ModItems.BUNKER_CORE.get());
        stack.set(ModComponents.CORE_OWNER.get(), new CoreOwner(id, name));
        return stack;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Player player = context.getPlayer();
        CoreOwner owner = context.getItemInHand().get(ModComponents.CORE_OWNER.get());
        if (player != null && owner != null && !owner.id().equals(player.getUUID())) {
            if (!context.getLevel().isClientSide) {
                player.displayClientMessage(Component.translatable("message.bunker.core.not_owner", owner.name())
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CoreOwner owner = stack.get(ModComponents.CORE_OWNER.get());
        if (owner != null) {
            tooltip.add(Component.translatable("tooltip.bunker.core.owner", owner.name()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.bunker.core.unbound").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.bunker.core.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
