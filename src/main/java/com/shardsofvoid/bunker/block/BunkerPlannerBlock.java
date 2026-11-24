package com.shardsofvoid.bunker.block;

import com.mojang.serialization.MapCodec;
import com.shardsofvoid.bunker.bunker.BunkerPlanner;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** Пульт проектирования бункера: ставится только впритык к ядру, открывает миникарту и стройку комнат. */
public class BunkerPlannerBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<BunkerPlannerBlock> CODEC = simpleCodec(BunkerPlannerBlock::new);

    public BunkerPlannerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return !(level instanceof Level l) || BunkerPlanner.hasCoreNeighbour(l, pos) || l.getBlockState(pos).is(this);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) BunkerPlanner.open(sp, pos);
        return InteractionResult.CONSUME;
    }
}
