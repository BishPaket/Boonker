package com.shardsofvoid.bunker.energy;

import com.shardsofvoid.bunker.block.BatteryBlock;
import com.shardsofvoid.bunker.block.MachineBlock;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Обновление внешнего вида энергоблока: лампа состояния и уровень заряда аккумулятора. */
public final class MachineBlocks {
    private MachineBlocks() {}

    public static void updateIndicator(BunkerMachineBlockEntity be) {
        Level level = be.getLevel();
        if (level == null || level.isClientSide) return;
        BlockState state = be.getBlockState();
        BlockState next = state;
        if (state.hasProperty(MachineBlock.INDICATOR)) {
            MachineBlock.Indicator ind = switch (be.status()) {
                case WORKING -> MachineBlock.Indicator.ON;
                case EXCESS -> MachineBlock.Indicator.EXCESS;
                default -> MachineBlock.Indicator.IDLE;
            };
            next = next.setValue(MachineBlock.INDICATOR, ind);
        }
        if (be instanceof BatteryBlockEntity battery && next.hasProperty(BatteryBlock.CHARGE)) {
            next = next.setValue(BatteryBlock.CHARGE, battery.chargeLevel());
        }
        if (next != state) level.setBlock(be.getBlockPos(), next, 2);
    }
}
