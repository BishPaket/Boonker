package com.shardsofvoid.bunker.energy;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.registry.ModMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * v3 4.3–4.4: энерговвод. Стоит в грани оболочки (тег bunker:shell_blocks); кабель подходит снаружи.
 * При bunker.energy.acceptForeignFE принимает FE других модов как отдельный тип «Внешний ввод».
 */
public class PowerInletBlockEntity extends BunkerMachineBlockEntity {
    /** Принятый за текущий интервал внешний FE. */
    private int foreignBuffer;

    private final ForeignEnergyInput input = new ForeignEnergyInput() {
        @Override
        protected int accept(int amount, boolean simulate) {
            int cap = BunkerConfig.ENERGY_FOREIGN_MAX_RATE.get() * BunkerConfig.ENERGY_INTERVAL.get();
            int got = Math.max(0, Math.min(amount, cap - foreignBuffer));
            if (!simulate) foreignBuffer += got;
            return got;
        }

        @Override
        protected boolean connected() {
            return bunkerId != null && status == MachineStatus.WORKING;
        }

        @Override
        protected long gameTime() {
            return level == null ? 0 : level.getGameTime();
        }

        @Override
        public int getEnergyStored() {
            return foreignBuffer;
        }

        @Override
        public int getMaxEnergyStored() {
            return BunkerConfig.ENERGY_FOREIGN_MAX_RATE.get() * BunkerConfig.ENERGY_INTERVAL.get();
        }
    };

    public PowerInletBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.POWER_INLET_BE.get(), pos, state, MachineKind.POWER_INLET);
    }

    public IEnergyStorage energyStorage() {
        return input;
    }

    /** Забрать накопленный за интервал внешний FE. */
    public int drainForeign() {
        int v = foreignBuffer;
        foreignBuffer = 0;
        return v;
    }
}
