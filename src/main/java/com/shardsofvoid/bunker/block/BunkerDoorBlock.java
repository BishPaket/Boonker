package com.shardsofvoid.bunker.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * v3 2: гермодверь — дверь 2×1 с прочностью бункерного металла. Открывается ПКМ и редстоуном,
 * закрывается сама через door.autoCloseSeconds, если её не держит сигнал редстоуна.
 */
public class BunkerDoorBlock extends DoorBlock {
    public BunkerDoorBlock(Properties properties) {
        super(Airlocks.TYPE, properties);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && !Airlocks.mayOpen(sl, pos, player)) return InteractionResult.FAIL;
        InteractionResult result = super.useWithoutItem(state, level, pos, player, hit);
        if (level instanceof ServerLevel sl) {
            BlockState now = level.getBlockState(pos);
            boolean open = now.is(this) && isOpen(now);
            Airlocks.toggled(sl, pos, open);
            if (open) sl.scheduleTick(pos, this, Airlocks.autoCloseTicks());
        }
        return result;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (level instanceof ServerLevel sl) {
            BlockState now = level.getBlockState(pos);
            if (now.is(this)) {
                Airlocks.toggled(sl, pos, isOpen(now));
                if (isOpen(now) && !now.getValue(POWERED)) sl.scheduleTick(pos, this, Airlocks.autoCloseTicks());
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.is(this) && isOpen(state) && !state.getValue(POWERED)) {
            setOpen(null, level, state, pos, false);
            Airlocks.toggled(level, pos, false);
        }
    }
}
