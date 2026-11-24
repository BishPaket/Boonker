package com.shardsofvoid.bunker.energy;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.registry.ModMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Станция очистки металла: перерабатывает металлолом Wastelands. С шансом 3% выходит незеритовый обломок,
 * иначе 1–5 железных самородков. Работает от сети бункера, стоя внутри него или в его стене.
 * Слот 0 — сырьё, 1–4 — выход (воронки и трубы забирают оттуда).
 */
public class MetalRecyclerBlockEntity extends BunkerMachineBlockEntity {
    private final ItemStackHandler slots = handler(5, (slot, stack) -> slot == 0 && isInput(stack), 64);
    private int progress;

    public MetalRecyclerBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.METAL_RECYCLER_BE.get(), pos, state, MachineKind.METAL_RECYCLER);
    }

    public static boolean isInput(ItemStack stack) {
        ResourceLocation id = ResourceLocation.tryParse(BunkerConfig.RECYCLER_INPUT.get());
        if (id == null || stack.isEmpty()) return false;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item != Items.AIR && stack.is(item);
    }

    @Override
    public ItemStackHandler items() {
        return slots;
    }

    public int totalTicks() {
        return BunkerConfig.RECYCLER_SECONDS.get() * 20;
    }

    /** Почему переработка не может идти (без учёта энергии), или null. */
    public MachineStatus blocker() {
        if (progress == 0 && !isInput(slots.getStackInSlot(0))) return MachineStatus.NO_INPUT;
        if (!hasRoom(new ItemStack(Items.NETHERITE_SCRAP)) || !hasRoom(new ItemStack(Items.IRON_NUGGET, BunkerConfig.RECYCLER_FAIL_MAX.get()))) {
            return MachineStatus.OUTPUT_FULL;
        }
        return null;
    }

    private boolean hasRoom(ItemStack stack) {
        int left = stack.getCount();
        for (int i = 1; i < slots.getSlots() && left > 0; i++) {
            ItemStack s = slots.getStackInSlot(i);
            if (s.isEmpty()) left -= stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(s, stack)) left -= s.getMaxStackSize() - s.getCount();
        }
        return left <= 0;
    }

    private void output(ItemStack stack) {
        for (int i = 1; i < slots.getSlots() && !stack.isEmpty(); i++) {
            ItemStack s = slots.getStackInSlot(i);
            if (s.isEmpty()) {
                slots.setStackInSlot(i, stack.copy());
                return;
            }
            if (ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) {
                int n = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                slots.setStackInSlot(i, s.copyWithCount(s.getCount() + n));
                stack.shrink(n);
            }
        }
    }

    /** Отработать интервал; вызывается энергосистемой, только когда станция запитана. */
    public void process(int interval) {
        if (level == null || blocker() != null) return;
        if (progress == 0) {
            ItemStack in = slots.getStackInSlot(0);
            slots.setStackInSlot(0, in.copyWithCount(in.getCount() - 1));
        }
        progress += interval;
        if (progress >= totalTicks()) {
            progress = 0;
            if (level.random.nextDouble() < BunkerConfig.RECYCLER_CHANCE.get()) {
                output(new ItemStack(Items.NETHERITE_SCRAP));
                level.playSound(null, worldPosition, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.4F);
            } else {
                int lo = BunkerConfig.RECYCLER_FAIL_MIN.get();
                int hi = Math.max(lo, BunkerConfig.RECYCLER_FAIL_MAX.get());
                int n = lo + level.random.nextInt(hi - lo + 1);
                if (n > 0) output(new ItemStack(Items.IRON_NUGGET, n));
                level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5F, 1.0F);
            }
        }
        setChanged();
    }

    @Override
    public int[] menuData() {
        return new int[]{status.ordinal(), lastRate, progress, totalTicks()};
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt("progress");
    }
}
