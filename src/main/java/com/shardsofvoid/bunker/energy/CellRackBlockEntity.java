package com.shardsofvoid.bunker.energy;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.item.EnergyCellItem;
import com.shardsofvoid.bunker.registry.ModMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * v3 5.3: стойка энергоячеек — 4 слота, до 50 FE/t на ячейку. Режимы «Авто», «Только заряд», «Только разряд».
 * Ячейки вставляются и вынимаются на ходу; вне сети бункера стойка просто хранит их.
 */
public class CellRackBlockEntity extends BunkerMachineBlockEntity {
    public enum Mode { AUTO, CHARGE_ONLY, DISCHARGE_ONLY }

    private final ItemStackHandler cells = handler(4, (slot, stack) -> stack.getItem() instanceof EnergyCellItem, 1);
    private Mode mode = Mode.AUTO;

    public CellRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.CELL_RACK_BE.get(), pos, state, MachineKind.CELL_RACK);
    }

    @Override
    public ItemStackHandler items() {
        return cells;
    }

    public Mode mode() {
        return mode;
    }

    public boolean canCharge() {
        return mode != Mode.DISCHARGE_ONLY;
    }

    public boolean canDischarge() {
        return mode != Mode.CHARGE_ONLY;
    }

    public int stored() {
        int total = 0;
        for (int i = 0; i < cells.getSlots(); i++) total += EnergyCellItem.energy(cells.getStackInSlot(i));
        return total;
    }

    public int capacity() {
        int n = 0;
        for (int i = 0; i < cells.getSlots(); i++) if (!cells.getStackInSlot(i).isEmpty()) n++;
        return n * EnergyCellItem.capacity();
    }

    /** Зарядить ячейки на amount FE (не больше rackRatePerCell × интервал на ячейку). Возвращает принятое. */
    public int charge(int amount, int interval) {
        int perCell = BunkerConfig.RACK_RATE_PER_CELL.get() * interval;
        int left = amount;
        for (int i = 0; i < cells.getSlots() && left > 0; i++) {
            ItemStack cell = cells.getStackInSlot(i);
            if (cell.isEmpty()) continue;
            int e = EnergyCellItem.energy(cell);
            int add = Math.min(left, Math.min(perCell, EnergyCellItem.capacity() - e));
            if (add <= 0) continue;
            EnergyCellItem.setEnergy(cell, e + add);
            left -= add;
        }
        if (left != amount) setChanged();
        return amount - left;
    }

    /** Разрядить ячейки на amount FE. Возвращает отданное. */
    public int discharge(int amount, int interval) {
        int perCell = BunkerConfig.RACK_RATE_PER_CELL.get() * interval;
        int left = amount;
        for (int i = 0; i < cells.getSlots() && left > 0; i++) {
            ItemStack cell = cells.getStackInSlot(i);
            if (cell.isEmpty()) continue;
            int e = EnergyCellItem.energy(cell);
            int take = Math.min(left, Math.min(perCell, e));
            if (take <= 0) continue;
            EnergyCellItem.setEnergy(cell, e - take);
            left -= take;
        }
        if (left != amount) setChanged();
        return amount - left;
    }

    public IEnergyStorage energyStorage() {
        return readOnly;
    }

    private final IEnergyStorage readOnly = new ReadOnlyEnergy() {
        @Override
        public int getEnergyStored() {
            return stored();
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity();
        }
    };

    @Override
    public void handleMenuAction(int action) {
        if (action == 0) {
            mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
            setChanged();
        }
    }

    @Override
    public int[] menuData() {
        return new int[]{status.ordinal(), lastRate, stored(), capacity(), mode.ordinal()};
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("mode", mode.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            mode = Mode.valueOf(tag.getString("mode"));
        } catch (IllegalArgumentException e) {
            mode = Mode.AUTO;
        }
    }
}
