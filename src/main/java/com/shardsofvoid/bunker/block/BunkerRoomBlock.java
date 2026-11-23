package com.shardsofvoid.bunker.block;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.shardsofvoid.bunker.bunker.Bunker;
import com.shardsofvoid.bunker.bunker.BunkerActions;
import com.shardsofvoid.bunker.bunker.BunkerManager;
import com.shardsofvoid.bunker.registry.ModComponents;
import com.shardsofvoid.bunker.registry.ModComponents.LinkedCore;
import com.shardsofvoid.bunker.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Блок комнат (раздел 6): стоит в центре куба комнаты, ПКМ — интерфейс комнаты. */
public class BunkerRoomBlock extends BunkerPartBlock {
    public static final MapCodec<BunkerRoomBlock> CODEC = simpleCodec(BunkerRoomBlock::new);

    public BunkerRoomBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BunkerRoomBlock> codec() {
        return CODEC;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) BunkerActions.openScreen(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) BunkerManager.get(serverLevel).onRoomPlaced(pos, placer, stack);
    }

    /** Снятый блок возвращается с привязкой и уровнем — переставить комнату можно без повторной привязки. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && !player.isCreative()) {
            ItemStack drop = new ItemStack(ModItems.BUNKER_ROOM_BLOCK.get());
            int roomLevel = state.getValue(LEVEL);
            if (roomLevel > 1) drop.set(ModComponents.ROOM_LEVEL.get(), roomLevel);
            BunkerManager m = BunkerManager.get(serverLevel);
            Bunker b = m.byRoom(pos);
            if (b == null) b = m.byPending(pos);
            if (b != null) drop.set(ModComponents.LINKED_CORE.get(), new LinkedCore(serverLevel.dimension(), b.core, b.id));
            Block.popResource(level, pos, drop);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            BunkerManager.get(serverLevel).onRoomRemoved(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
