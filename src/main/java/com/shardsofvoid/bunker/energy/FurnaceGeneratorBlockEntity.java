package com.shardsofvoid.bunker.energy;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.registry.ModMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/** v3 4.1: твердотопливный генератор — 8 FE/t, пока горит, любое топливо печи с ванильным временем горения. */
public class FurnaceGeneratorBlockEntity extends BunkerMachineBlockEntity {
    private final ItemStackHandler fuel = handler(1, (slot, stack) -> stack.getBurnTime(RecipeType.SMELTING) > 0, 64);
    private int burnLeft;
    private int burnTotal;

    public FurnaceGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.FURNACE_GENERATOR_BE.get(), pos, state, MachineKind.FURNACE_GENERATOR);
    }

    @Override
    public ItemStackHandler items() {
        return fuel;
    }

    public boolean isBurning() {
        return burnLeft > 0;
    }

    /** Отработать интервал. active = генератор подключён и не «лишний». Возвращает FE/t. */
    public int produce(int interval, boolean active) {
        if (!active) return 0;
        if (burnLeft <= 0) {
            ItemStack stack = fuel.getStackInSlot(0);
            int time = stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
            if (time <= 0) {
                burnTotal = 0;
                setStatus(MachineStatus.NO_FUEL);
                return 0;
            }
            ItemStack remainder = stack.getCraftingRemainingItem();
            stack.shrink(1);
            if (stack.isEmpty() && !remainder.isEmpty()) fuel.setStackInSlot(0, remainder.copy());
            else fuel.setStackInSlot(0, stack);
            burnLeft = time;
            burnTotal = time;
        }
        burnLeft = Math.max(0, burnLeft - interval);
        setChanged();
        setStatus(MachineStatus.WORKING);
        return BunkerConfig.GEN_FURNACE_RATE.get();
    }

    @Override
    protected void onStatusChanged() {
        MachineBlocks.updateIndicator(this);
    }

    @Override
    public int[] menuData() {
        return new int[]{status.ordinal(), lastRate, burnLeft, burnTotal};
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("burnLeft", burnLeft);
        tag.putInt("burnTotal", burnTotal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        burnLeft = tag.getInt("burnLeft");
        burnTotal = tag.getInt("burnTotal");
    }
}
