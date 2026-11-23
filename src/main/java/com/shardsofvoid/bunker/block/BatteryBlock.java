package com.shardsofvoid.bunker.block;

import com.shardsofvoid.bunker.energy.MachineKind;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** v3 5.1: аккумулятор — 5 состояний текстуры по заряду, сигнал компаратора 0–15. */
public class BatteryBlock extends MachineBlock {
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);

    public BatteryBlock(Properties properties) {
        super(MachineKind.BATTERY, properties);
        registerDefaultState(defaultBlockState().setValue(CHARGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CHARGE);
    }
}
