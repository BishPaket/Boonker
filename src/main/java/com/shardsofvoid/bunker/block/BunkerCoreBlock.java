package com.shardsofvoid.bunker.block;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.shardsofvoid.bunker.bunker.Bunker;
import com.shardsofvoid.bunker.bunker.BunkerActions;
import com.shardsofvoid.bunker.bunker.BunkerManager;
import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.registry.ModComponents;
import com.shardsofvoid.bunker.registry.ModComponents.LinkedCore;
import com.shardsofvoid.bunker.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Ядро бункера (раздел 4). ПКМ — интерфейс; ПКМ блоком комнат — привязка блока к этому ядру (раздел 6.2). */
public class BunkerCoreBlock extends BunkerPartBlock {
    public static final MapCodec<BunkerCoreBlock> CODEC = simpleCodec(BunkerCoreBlock::new);

    public BunkerCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BunkerCoreBlock> codec() {
        return CODEC;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.BUNKER_ROOM_BLOCK.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer sp) {
            bindRoomItem(serverLevel, pos, sp, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void bindRoomItem(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack stack) {
        Bunker b = BunkerManager.get(level).byCore(pos);
        if (b == null) return;
        if (!b.owner.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.bunker.room.bind_foreign").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (BunkerConfig.ROOM_BIND_REQUIRES_ACTIVE.get() && !b.isActive()) {
            player.displayClientMessage(Component.translatable("message.bunker.room.bind_inactive").withStyle(ChatFormatting.RED), true);
            return;
        }
        stack.set(ModComponents.LINKED_CORE.get(), new LinkedCore(level.dimension(), pos.immutable(), b.id));
        player.displayClientMessage(Component.translatable("message.bunker.room.bound", pos.getX(), pos.getY(), pos.getZ())
                .withStyle(ChatFormatting.GREEN), true);
        BunkerManager.get(level).sendHighlight(player, b, true);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) BunkerActions.openScreen(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) BunkerManager.get(serverLevel).onCorePlaced(pos, placer, stack);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel) {
            BunkerManager manager = BunkerManager.get(serverLevel);
            Bunker b = manager.byCore(pos);
            if (b != null) manager.returnCoreItem(b);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            BunkerManager.get(serverLevel).onCoreRemoved(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
