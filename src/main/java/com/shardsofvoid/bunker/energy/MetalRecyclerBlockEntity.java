package com.shardsofvoid.bunker.energy;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.registry.ModItems;
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
 * Станция очистки металла. Сырьё:
 * <ul>
 * <li>металлолом Wastelands — шанс незеритового обломка, иначе железные самородки;</li>
 * <li>куча металлолома — как 6 штук металлолома (6 бросков, в 6 раз дольше);</li>
 * <li>сломанный бункерный металл — всегда бункерный металл.</li>
 * </ul>
 * Работает от сети бункера, стоя внутри него или в его стене. Слот 0 — сырьё, 1–4 — продукт.
 */
public class MetalRecyclerBlockEntity extends BunkerMachineBlockEntity {
    private static final int MODE_NONE = 0;
    private static final int MODE_SCRAP = 1;
    private static final int MODE_RESTORE = 2;

    private final ItemStackHandler slots = handler(5, (slot, stack) -> slot == 0 && modeOf(stack) != MODE_NONE, 64);
    private int progress;
    private int total;
    private int mode;
    private int rolls;

    public MetalRecyclerBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.METAL_RECYCLER_BE.get(), pos, state, MachineKind.METAL_RECYCLER);
    }

    private static boolean is(ItemStack stack, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || stack.isEmpty()) return false;
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item != Items.AIR && stack.is(item);
    }

    private static int modeOf(ItemStack stack) {
        if (stack.isEmpty()) return MODE_NONE;
        if (stack.is(ModItems.BROKEN_BUNKER_METAL.get())) return MODE_RESTORE;
        if (is(stack, BunkerConfig.RECYCLER_INPUT.get()) || is(stack, BunkerConfig.RECYCLER_PILE_INPUT.get())) return MODE_SCRAP;
        return MODE_NONE;
    }

    private static int rollsOf(ItemStack stack) {
        return is(stack, BunkerConfig.RECYCLER_PILE_INPUT.get()) ? BunkerConfig.RECYCLER_PILE_UNITS.get() : 1;
    }

    @Override
    public ItemStackHandler items() {
        return slots;
    }

    /** Почему переработка не может идти (без учёта энергии), или null. */
    public MachineStatus blocker() {
        int m = mode;
        int r = rolls;
        if (progress == 0 || m == MODE_NONE) {
            ItemStack in = slots.getStackInSlot(0);
            m = modeOf(in);
            r = rollsOf(in);
            if (m == MODE_NONE) return MachineStatus.NO_INPUT;
        }
        if (m == MODE_RESTORE) {
            if (!hasRoom(new ItemStack(ModItems.BUNKER_METAL.get()))) return MachineStatus.OUTPUT_FULL;
        } else if (!hasRoom(new ItemStack(Items.NETHERITE_SCRAP, r))
                || !hasRoom(new ItemStack(Items.IRON_NUGGET, Math.min(64, r * BunkerConfig.RECYCLER_FAIL_MAX.get())))) {
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
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) {
                int n = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                slots.setStackInSlot(i, s.copyWithCount(s.getCount() + n));
                stack.shrink(n);
            }
        }
        for (int i = 1; i < slots.getSlots() && !stack.isEmpty(); i++) {
            if (slots.getStackInSlot(i).isEmpty()) {
                int n = Math.min(stack.getCount(), stack.getMaxStackSize());
                slots.setStackInSlot(i, stack.copyWithCount(n));
                stack.shrink(n);
            }
        }
    }

    /** Отработать интервал; вызывается энергосистемой, только когда станция запитана. */
    public void process(int interval) {
        if (level == null || blocker() != null) return;
        if (progress == 0 || mode == MODE_NONE) {
            ItemStack in = slots.getStackInSlot(0);
            mode = modeOf(in);
            rolls = rollsOf(in);
            total = mode == MODE_RESTORE ? BunkerConfig.RECYCLER_BROKEN_SECONDS.get() * 20 : BunkerConfig.RECYCLER_SECONDS.get() * 20 * rolls;
            slots.setStackInSlot(0, in.copyWithCount(in.getCount() - 1));
            progress = 0;
        }
        progress += interval;
        if (progress >= total) {
            finish();
            progress = 0;
            mode = MODE_NONE;
            rolls = 0;
        }
        setChanged();
    }

    private void finish() {
        if (mode == MODE_RESTORE) {
            output(new ItemStack(ModItems.BUNKER_METAL.get()));
            level.playSound(null, worldPosition, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4F, 1.6F);
            return;
        }
        int scrap = 0;
        int nuggets = 0;
        int lo = BunkerConfig.RECYCLER_FAIL_MIN.get();
        int hi = Math.max(lo, BunkerConfig.RECYCLER_FAIL_MAX.get());
        for (int i = 0; i < Math.max(1, rolls); i++) {
            if (level.random.nextDouble() < BunkerConfig.RECYCLER_CHANCE.get()) scrap++;
            else nuggets += lo + level.random.nextInt(hi - lo + 1);
        }
        if (scrap > 0) {
            output(new ItemStack(Items.NETHERITE_SCRAP, scrap));
            level.playSound(null, worldPosition, SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.4F);
        }
        if (nuggets > 0) output(new ItemStack(Items.IRON_NUGGET, nuggets));
        level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    @Override
    public int[] menuData() {
        return new int[]{status.ordinal(), lastRate, progress, Math.max(1, total)};
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("progress", progress);
        tag.putInt("total", total);
        tag.putInt("mode", mode);
        tag.putInt("rolls", rolls);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt("progress");
        total = tag.getInt("total");
        mode = tag.getInt("mode");
        rolls = tag.getInt("rolls");
    }
}
