package com.shardsofvoid.bunker.energy;

import com.shardsofvoid.bunker.config.BunkerConfig;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * v3 4.4: приём FE от других модов. Разрешён конфигом bunker.energy.acceptForeignFE и ограничен потолком
 * bunker.energy.foreignMaxRate за тик на блок. Отдача энергии наружу не поддерживается.
 */
public abstract class ForeignEnergyInput implements IEnergyStorage {
    private long tick = Long.MIN_VALUE;
    private int acceptedThisTick;

    protected abstract int accept(int amount, boolean simulate);

    protected abstract boolean connected();

    /** Текущее игровое время. */
    protected abstract long gameTime();

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (!BunkerConfig.SPEC.isLoaded() || !BunkerConfig.ENERGY_ACCEPT_FOREIGN.get() || !connected() || toReceive <= 0) return 0;
        long now = gameTime();
        if (now != tick) {
            tick = now;
            acceptedThisTick = 0;
        }
        int allowed = Math.max(0, BunkerConfig.ENERGY_FOREIGN_MAX_RATE.get() - acceptedThisTick);
        int got = accept(Math.min(toReceive, allowed), simulate);
        if (!simulate) acceptedThisTick += got;
        return got;
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
        return BunkerConfig.SPEC.isLoaded() && BunkerConfig.ENERGY_ACCEPT_FOREIGN.get() && connected();
    }
}
