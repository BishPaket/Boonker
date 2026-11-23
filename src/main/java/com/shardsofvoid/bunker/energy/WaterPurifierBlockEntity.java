package com.shardsofvoid.bunker.energy;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.item.FilterItem;
import com.shardsofvoid.bunker.registry.ModMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * v3 8.4: станция водоочистки. Бак сырой воды 8 000 mB (вёдра, бутылки, трубы через fluid capability),
 * очистка 1 000 mB за 30 с при 3 FE/t, картридж теряет 1 ед. на 1 000 mB. Бак чистой воды 16 000 mB —
 * это и есть запас воды бункера.
 */
public class WaterPurifierBlockEntity extends BunkerMachineBlockEntity {
    public static final int RAW_CAPACITY = 8_000;
    public static final int CLEAN_CAPACITY = 16_000;
    public static final int BATCH = 1_000;

    private final ItemStackHandler cartridge = handler(1, (slot, stack) -> stack.getItem() instanceof FilterItem f && !f.isAir(), 1);
    private int raw;
    private int clean;
    private int progress;

    public WaterPurifierBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.WATER_PURIFIER_BE.get(), pos, state, MachineKind.WATER_PURIFIER);
    }

    @Override
    public ItemStackHandler items() {
        return cartridge;
    }

    public int raw() {
        return raw;
    }

    public int clean() {
        return clean;
    }

    public boolean hasCartridge() {
        return FilterItem.remaining(cartridge.getStackInSlot(0)) > 0;
    }

    /** Почему очистка не может идти (без учёта энергии), или null — может. */
    public MachineStatus blocker() {
        if (!hasCartridge()) return MachineStatus.NO_FILTER;
        if (raw < BATCH && progress == 0) return MachineStatus.NO_WATER;
        if (clean + BATCH > CLEAN_CAPACITY) return MachineStatus.FULL;
        return null;
    }

    /** Отработать интервал очистки. */
    public void process(int interval) {
        if (blocker() != null) return;
        if (progress == 0) raw -= BATCH;
        progress += interval;
        if (progress >= BunkerConfig.WATER_PURIFY_SECONDS.get() * 20) {
            progress = 0;
            clean += BATCH;
            ItemStack c = cartridge.getStackInSlot(0).copy();
            if (FilterItem.addWear(c, 1)) c = ItemStack.EMPTY;
            cartridge.setStackInSlot(0, c);
        }
        setChanged();
    }

    public int addRaw(int amount) {
        int got = Math.max(0, Math.min(amount, RAW_CAPACITY - raw));
        raw += got;
        if (got > 0) setChanged();
        return got;
    }

    public int takeClean(int amount) {
        int got = Math.max(0, Math.min(amount, clean));
        clean -= got;
        if (got > 0) setChanged();
        return got;
    }

    public double cartridgeFraction() {
        ItemStack s = cartridge.getStackInSlot(0);
        if (!(s.getItem() instanceof FilterItem f)) return 0;
        return FilterItem.remaining(s) / (double) Math.max(1, f.resource());
    }

    /** Fluid capability: только приём воды в бак сырой воды. */
    public IFluidHandler fluidHandler() {
        return fluid;
    }

    private final IFluidHandler fluid = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            int amount = tank == 0 ? raw : clean;
            return amount > 0 ? new FluidStack(Fluids.WATER, amount) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? RAW_CAPACITY : CLEAN_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && stack.is(Fluids.WATER);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !resource.is(Fluids.WATER)) return 0;
            int got = Math.max(0, Math.min(resource.getAmount(), RAW_CAPACITY - raw));
            if (action.execute() && got > 0) {
                raw += got;
                setChanged();
            }
            return got;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    @Override
    public int[] menuData() {
        ItemStack s = cartridge.getStackInSlot(0);
        int max = s.getItem() instanceof FilterItem f ? f.resource() : 0;
        return new int[]{status.ordinal(), lastRate, raw, clean, progress, BunkerConfig.WATER_PURIFY_SECONDS.get() * 20,
                FilterItem.remaining(s), max};
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("raw", raw);
        tag.putInt("clean", clean);
        tag.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        raw = tag.getInt("raw");
        clean = tag.getInt("clean");
        progress = tag.getInt("progress");
    }
}
