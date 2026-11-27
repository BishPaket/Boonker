package com.shardsofvoid.bunker.safezone;

import com.shardsofvoid.bunker.config.BunkerConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Блок безопасной зоны (для администраторов): внутри параллелепипеда вокруг блока радиация не действует.
 * Ставят и ломают только операторы; размер меняется в его интерфейсе.
 */
public class SafeZoneBlock extends Block {
    public SafeZoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel sl) {
            int r = BunkerConfig.SAFE_ZONE_DEFAULT_RADIUS.get();
            SafeZoneData.get(sl).put(pos, r, r, r);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) SafeZoneData.get(sl).remove(pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) SafeZones.open(sp, pos);
        return InteractionResult.CONSUME;
    }

    /** Ломать могут только операторы (в творческом режиме — как обычно). */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!player.hasPermissions(2)) return 0.0F;
        return super.getDestroyProgress(state, player, level, pos);
    }
}
