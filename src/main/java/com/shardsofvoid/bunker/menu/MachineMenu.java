package com.shardsofvoid.bunker.menu;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.block.MachineBlock;
import com.shardsofvoid.bunker.energy.BunkerMachineBlockEntity;
import com.shardsofvoid.bunker.energy.MachineKind;
import com.shardsofvoid.bunker.network.MachineDataPayload;
import com.shardsofvoid.bunker.registry.ModMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Общее меню энергоблоков с инвентарём (генератор, стойка, станция, ФВУ, водоочистка).
 * Числа для экрана идут отдельным пакетом: ванильная синхронизация данных меню ограничена 16 битами.
 */
public class MachineMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public final MachineKind kind;
    @Nullable
    private final BunkerMachineBlockEntity be;
    private final Player player;
    private final int machineSlots;
    /** Данные с сервера (на клиенте) или последние отправленные (на сервере). */
    public int[] data = new int[0];

    /** Сервер. */
    public MachineMenu(int id, Inventory inv, BunkerMachineBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.kind, be, be.items());
    }

    /** Клиент. */
    public MachineMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), MachineKind.byId(buf.readVarInt()), null, null);
    }

    private MachineMenu(int id, Inventory inv, BlockPos pos, MachineKind kind, @Nullable BunkerMachineBlockEntity be,
                        @Nullable IItemHandler handler) {
        super(ModMachines.MACHINE_MENU.get(), id);
        this.pos = pos;
        this.kind = kind;
        this.be = be;
        this.player = inv.player;
        int[][] layout = layout(kind);
        IItemHandler items = handler != null ? handler : new ItemStackHandler(Math.max(1, layout.length));
        for (int i = 0; i < layout.length; i++) addSlot(new SlotItemHandler(items, i, layout[i][0], layout[i][1]));
        this.machineSlots = layout.length;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 142));
    }

    /** Координаты слотов блока в окне 176×166. */
    public static int[][] layout(MachineKind kind) {
        return switch (kind) {
            case FURNACE_GENERATOR, AIR_FILTER_UNIT -> new int[][]{{26, 35}};
            case WATER_PURIFIER -> new int[][]{{26, 53}};
            case METAL_RECYCLER -> new int[][]{{44, 35}, {116, 26}, {134, 26}, {116, 44}, {134, 44}};
            case CELL_RACK -> new int[][]{{26, 26}, {44, 26}, {26, 44}, {44, 44}};
            case SUIT_STATION -> new int[][]{{26, 8}, {26, 26}, {26, 44}, {26, 62}, {62, 62}};
            default -> new int[0][];
        };
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (be != null && player instanceof ServerPlayer sp) {
            int[] now = be.menuData();
            if (!Arrays.equals(now, data)) {
                data = now;
                PacketDistributor.sendToPlayer(sp, new MachineDataPayload(containerId, now));
            }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be != null) {
            be.handleMenuAction(id);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int total = slots.size();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, total, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof MachineBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    public int value(int index) {
        return index < data.length ? data[index] : 0;
    }
}
