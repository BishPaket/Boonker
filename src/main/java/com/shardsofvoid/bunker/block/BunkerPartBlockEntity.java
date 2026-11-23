package com.shardsofvoid.bunker.block;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Блок-сущность ядра и блока комнат: идентификатор бункера, владелец и уровень (раздел 7.1). */
public class BunkerPartBlockEntity extends BlockEntity {
    @Nullable
    private UUID bunkerId;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private int level = 1;

    public BunkerPartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.PART_BE.get(), pos, state);
        if (state.hasProperty(BunkerPartBlock.LEVEL)) level = state.getValue(BunkerPartBlock.LEVEL);
    }

    public void bind(UUID bunkerId, UUID owner, String ownerName) {
        this.bunkerId = bunkerId;
        this.owner = owner;
        this.ownerName = ownerName == null ? "" : ownerName;
        setChanged();
    }

    /** Отвязать (бункер удалён или комната отвязана). Владелец сохраняется — снять блок может только он. */
    public void unbind() {
        this.bunkerId = null;
        setChanged();
    }

    @Nullable
    public UUID bunkerId() {
        return bunkerId;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (bunkerId != null) tag.putUUID("bunker", bunkerId);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        tag.putInt("level", level);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bunkerId = tag.hasUUID("bunker") ? tag.getUUID("bunker") : null;
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        level = Math.max(1, Math.min(3, tag.contains("level") ? tag.getInt("level") : 1));
    }
}
