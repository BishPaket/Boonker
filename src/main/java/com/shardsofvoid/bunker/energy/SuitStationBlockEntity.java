package com.shardsofvoid.bunker.energy;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.item.RadSuitItem;
import com.shardsofvoid.bunker.registry.ModMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * v3 7: станция обслуживания костюмов. 4 слота (шлем, нагрудник, поножи, ботинки) и 5-й под материал.
 * Чинит 1 ед. прочности в секунду на часть за 10 FE/t, только в активной зоне и включённой комнате.
 */
public class SuitStationBlockEntity extends BunkerMachineBlockEntity {
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final ItemStackHandler items = new ItemStackHandler(5) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == 4) return materialItem() != null && stack.is(materialItem());
            return stack.getItem() instanceof RadSuitItem suit && suit.getType().getSlot() == SLOTS[slot];
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 4 ? 64 : 1;
        }
    };
    /** Оплаченные материалом единицы ремонта.*/
    private int materialCredit;

    public SuitStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModMachines.SUIT_STATION_BE.get(), pos, state, MachineKind.SUIT_STATION);
    }

    @Override
    public ItemStackHandler items() {
        return items;
    }

    /** Предмет и количество прочности на единицу материала (station.materialPerDurability) или null. */
    @Nullable
    public static Item materialItem() {
        String cfg = BunkerConfig.SPEC.isLoaded() ? BunkerConfig.STATION_MATERIAL.get().trim() : "";
        if (cfg.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(cfg.split("\\s+")[0]);
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    public static int materialPer() {
        String[] parts = BunkerConfig.STATION_MATERIAL.get().trim().split("\\s+");
        try {
            return parts.length > 1 ? Math.max(1, Integer.parseInt(parts[1])) : 60;
        } catch (NumberFormatException e) {
            return 60;
        }
    }

    /** Сколько частей сейчас нужно чинить. */
    public int damagedParts() {
        int n = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty() && s.isDamaged()) n++;
        }
        return n;
    }

    /** Починить на units единиц каждую повреждённую часть. Возвращает число отремонтированных частей. */
    public int repair(int units) {
        Item material = materialItem();
        int parts = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (s.isEmpty() || !s.isDamaged()) continue;
            int amount = Math.min(units, s.getDamageValue());
            if (material != null) {
                // 1 предмет материала даёт materialPer единиц прочности (station.materialPerDurability)
                ItemStack mat = items.getStackInSlot(4);
                int allowed = 0;
                for (int k = 0; k < amount; k++) {
                    if (materialCredit <= 0) {
                        if (mat.isEmpty()) break;
                        mat.shrink(1);
                        materialCredit = materialPer();
                    }
                    materialCredit--;
                    allowed++;
                }
                amount = allowed;
                if (amount <= 0) continue;
            }
            s.setDamageValue(s.getDamageValue() - amount);
            parts++;
        }
        if (parts > 0) {
            setChanged();
            syncToClient();
        }
        return parts;
    }

    /** Shift+ПКМ пустой рукой: снять костюм на станцию или надеть обратно (v3 7.1). */
    public void swapWith(net.minecraft.world.entity.player.Player player) {
        boolean wearing = false;
        for (EquipmentSlot slot : SLOTS) {
            if (player.getItemBySlot(slot).getItem() instanceof RadSuitItem) wearing = true;
        }
        for (int i = 0; i < 4; i++) {
            EquipmentSlot slot = SLOTS[i];
            ItemStack worn = player.getItemBySlot(slot);
            ItemStack hung = items.getStackInSlot(i);
            if (wearing) {
                if (worn.getItem() instanceof RadSuitItem && hung.isEmpty()) {
                    items.setStackInSlot(i, worn.copy());
                    player.setItemSlot(slot, ItemStack.EMPTY);
                }
            } else if (!hung.isEmpty() && worn.isEmpty()) {
                player.setItemSlot(slot, hung.copy());
                items.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    public ItemStack displayed(int slot) {
        return items.getStackInSlot(slot);
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public int[] menuData() {
        return new int[]{status.ordinal(), lastRate, damagedParts()};
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("items", items.serializeNBT(registries));
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("materialCredit", materialCredit);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        materialCredit = tag.getInt("materialCredit");
    }
}
