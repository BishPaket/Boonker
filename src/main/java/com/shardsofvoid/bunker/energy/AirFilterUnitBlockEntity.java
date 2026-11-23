package com.shardsofvoid.bunker.energy;

import com.shardsofvoid.bunker.block.MachineBlock;
import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.item.FilterItem;
import com.shardsofvoid.bunker.registry.ModMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * v3 8.2: фильтровентиляционная установка (ФВУ). Встраивается в грань оболочки; наружная сторона (FACING) —
 * воздухозабор, перед ним нужен неполный блок. Слот под фильтр-поглотитель.
 */
public class AirFilterUnitBlockEntity extends BunkerMachineBlockEntity {
    private final ItemStackHandler filter = handler(1, (slot, stack) -> stack.getItem() instanceof FilterItem f && f.isAir(), 1);
    /** Дробный износ, единицы ресурса. */
    private double wearAccumulator;

    public AirFilterUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.AIR_FILTER_UNIT_BE.get(), pos, state, MachineKind.AIR_FILTER_UNIT);
    }

    @Override
    public ItemStackHandler items() {
        return filter;
    }

    public ItemStack filterStack() {
        return filter.getStackInSlot(0);
    }

    public boolean hasFilter() {
        return FilterItem.remaining(filterStack()) > 0;
    }

    /** Перед воздухозабором снаружи неполный блок (воздух, решётка и т. п.). */
    public boolean intakeOpen() {
        if (level == null) return false;
        if (!BunkerConfig.FILTER_REQUIRE_OPEN_INTAKE.get()) return true;
        // лицевая сторона (FACING) смотрит внутрь бункера, воздухозабор — с обратной стороны
        Direction out = getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING).getOpposite() : Direction.UP;
        BlockPos front = worldPosition.relative(out);
        if (!level.isLoaded(front)) return false;
        BlockState state = level.getBlockState(front);
        return !state.isCollisionShapeFullBlock(level, front);
    }

    /** Износ фильтра: units единиц ресурса (дробно). Отработанный фильтр исчезает. */
    public void wear(double units) {
        if (units <= 0 || filterStack().isEmpty()) return;
        wearAccumulator += units;
        int whole = (int) Math.floor(wearAccumulator);
        if (whole <= 0) return;
        wearAccumulator -= whole;
        ItemStack stack = filterStack().copy();
        if (FilterItem.addWear(stack, whole)) stack = ItemStack.EMPTY;
        filter.setStackInSlot(0, stack);
    }

    /** Остаток ресурса в долях 0–1. */
    public double resourceFraction() {
        ItemStack s = filterStack();
        if (!(s.getItem() instanceof FilterItem f)) return 0;
        return FilterItem.remaining(s) / (double) Math.max(1, f.resource());
    }

    @Override
    public int[] menuData() {
        ItemStack s = filterStack();
        int max = s.getItem() instanceof FilterItem f ? f.resource() : 0;
        return new int[]{status.ordinal(), lastRate, FilterItem.remaining(s), max};
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("wear", wearAccumulator);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wearAccumulator = tag.getDouble("wear");
    }
}
