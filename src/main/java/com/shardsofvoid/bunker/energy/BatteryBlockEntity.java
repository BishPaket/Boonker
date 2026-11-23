package com.shardsofvoid.bunker.energy;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.registry.ModComponents;
import com.shardsofvoid.bunker.registry.ModMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** v3 5.1: аккумуляторная батарея. 200 000 FE, заряд/разряд до 100 FE/t. Заряд сохраняется в предмете. */
public class BatteryBlockEntity extends BunkerMachineBlockEntity {
    private int energy;

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.BATTERY_BE.get(), pos, state, MachineKind.BATTERY);
    }

    public static int capacity() {
        return BunkerConfig.SPEC.isLoaded() ? BunkerConfig.BATTERY_CAPACITY.get() : 200_000;
    }

    public int energy() {
        return energy;
    }

    public void setEnergy(int value) {
        int v = Mth.clamp(value, 0, capacity());
        if (v == energy) return;
        int oldLevel = chargeLevel();
        energy = v;
        setChanged();
        if (oldLevel != chargeLevel()) MachineBlocks.updateIndicator(this);
        if (level != null) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    /** 5 состояний текстуры: 0 — пусто … 4 — полно. */
    public int chargeLevel() {
        int cap = capacity();
        if (energy <= 0) return 0;
        if (energy >= cap) return 4;
        return Mth.clamp(1 + energy * 3 / cap, 1, 3);
    }

    public int comparatorSignal() {
        return (int) Math.floor(15.0 * energy / Math.max(1, capacity()));
    }

    /** Capability для совместимости: показывает заряд; приём FE других модов идёт только через энерговвод (v3 4.4). */
    public IEnergyStorage energyStorage() {
        return readOnly;
    }

    private final IEnergyStorage readOnly = new ReadOnlyEnergy() {
        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity();
        }
    };

    @Override
    public int[] menuData() {
        return new int[]{status.ordinal(), lastRate, energy, capacity()};
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Integer e = input.get(ModComponents.STORED_ENERGY.get());
        if (e != null) energy = e;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (energy > 0) builder.set(ModComponents.STORED_ENERGY.get(), energy);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = tag.getInt("energy");
    }
}
