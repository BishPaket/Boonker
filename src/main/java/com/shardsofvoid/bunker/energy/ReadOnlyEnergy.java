package com.shardsofvoid.bunker.energy;

import net.neoforged.neoforge.energy.IEnergyStorage;

/** Хранилище, которое только показывает заряд: ни приёма, ни отдачи. */
public abstract class ReadOnlyEnergy implements IEnergyStorage {
    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        return 0;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return false;
    }
}
