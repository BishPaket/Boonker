package com.shardsofvoid.bunker.energy;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.bunker.BunkerManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Энергоблок мода (v3 3.1). Хранит владельца: к чужому бункеру блок не подключается.
 * Энергия считается виртуально менеджером бункера раз в интервал, сам блок не тикает.
 */
public abstract class BunkerMachineBlockEntity extends BlockEntity {
    public final MachineKind kind;
    @Nullable
    protected UUID owner;
    protected String ownerName = "";
    protected MachineStatus status = MachineStatus.NO_LINK;
    /** Выработка (+) или потребление за прошлый интервал, FE/t — для интерфейса. */
    protected int lastRate;
    /** Бункер, к сети которого блок подключён (временное). */
    @Nullable
    public UUID bunkerId;

    protected BunkerMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineKind kind) {
        super(type, pos, state);
        this.kind = kind;
    }

    public void setOwner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name == null ? "" : name;
        setChanged();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public MachineStatus status() {
        return status;
    }

    public int lastRate() {
        return lastRate;
    }

    public void setLastRate(int rate) {
        this.lastRate = rate;
    }

    /** Сменить состояние; наследники обновляют индикатор блока. */
    public void setStatus(MachineStatus status) {
        if (this.status == status) return;
        this.status = status;
        onStatusChanged();
    }

    protected void onStatusChanged() {
        MachineBlocks.updateIndicator(this);
    }

    /** Инвентарь блока или null. */
    @Nullable
    public ItemStackHandler items() {
        return null;
    }

    /** Числа для экрана блока (синхронизируются через MachineMenu). */
    public int[] menuData() {
        return new int[]{status.ordinal(), lastRate};
    }

    /** Действие из экрана блока (например, режим стойки). */
    public void handleMenuAction(int action) {}

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) BunkerManager.get(sl).registerMachine(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        unregister();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        unregister();
    }

    private void unregister() {
        if (level instanceof ServerLevel sl) {
            BunkerManager m = BunkerManager.getIfLoaded(sl);
            if (m != null) m.unregisterMachine(this);
        }
    }

    /** Выбросить содержимое при разрушении блока. */
    public void dropContents(Level level, BlockPos pos) {
        ItemStackHandler handler = items();
        if (handler == null) return;
        for (int i = 0; i < handler.getSlots(); i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), handler.getStackInSlot(i));
            handler.setStackInSlot(i, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        ItemStackHandler handler = items();
        if (handler != null) tag.put("items", handler.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        ItemStackHandler handler = items();
        if (handler != null && tag.contains("items")) handler.deserializeNBT(registries, tag.getCompound("items"));
    }

    /** Обработчик инвентаря, помечающий блок изменённым. */
    protected ItemStackHandler handler(int size, java.util.function.BiPredicate<Integer, net.minecraft.world.item.ItemStack> valid, int limit) {
        return new ItemStackHandler(size) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
                return valid.test(slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return limit;
            }
        };
    }
}
