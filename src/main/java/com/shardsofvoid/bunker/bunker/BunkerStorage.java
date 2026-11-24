package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.shardsofvoid.bunker.energy.BunkerMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Хранилища бункера: всё, что внутри областей (включая оболочку) отдаёт IItemHandler, — сундуки, бочки, хранилища
 * Create и другие. Энергоблоки мода не считаются. Расходы интерфейса бункера (улучшения, стройка) берутся сначала
 * из инвентаря игрока, затем из хранилищ; добытое при стройке кладётся в хранилища.
 */
public final class BunkerStorage {
    private BunkerStorage() {}

    /** Хранилища в загруженных чанках бункера. Двойной сундук и многоблочные хранилища — один раз. */
    public static List<IItemHandler> handlers(ServerLevel level, Bunker b) {
        List<Area> areas = b.areas();
        if (areas.isEmpty()) return List.of();
        int[] box = BunkerGeometry.bounds(areas, 0);
        List<IItemHandler> out = new ArrayList<>();
        Set<IItemHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int cx = SectionPos.blockToSectionCoord(box[0]); cx <= SectionPos.blockToSectionCoord(box[3]); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(box[2]); cz <= SectionPos.blockToSectionCoord(box[5]); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    if (be.isRemoved() || be instanceof BunkerMachineBlockEntity) continue;
                    BlockPos pos = be.getBlockPos();
                    if (!inside(areas, pos)) continue;
                    BlockState state = be.getBlockState();
                    if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) == ChestType.RIGHT
                            && inside(areas, pos.relative(ChestBlock.getConnectedDirection(state)))) {
                        continue; // вторая половина двойного сундука: его содержимое уже даёт первая
                    }
                    IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, be, null);
                    if (h != null && seen.add(h)) out.add(h);
                }
            }
        }
        return out;
    }

    private static boolean inside(List<Area> areas, BlockPos pos) {
        for (Area a : areas) if (a.contains(pos)) return true;
        return false;
    }

    /** Сколько подходящих предметов у игрока (если есть) и в хранилищах. */
    public static int count(@Nullable ServerPlayer player, ServerLevel level, Bunker b, Predicate<ItemStack> test) {
        long total = 0;
        if (player != null) {
            for (ItemStack s : player.getInventory().items) if (!s.isEmpty() && test.test(s)) total += s.getCount();
            for (ItemStack s : player.getInventory().offhand) if (!s.isEmpty() && test.test(s)) total += s.getCount();
        }
        for (IItemHandler h : handlers(level, b)) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty() && test.test(s)) total += s.getCount();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * Забрать ровно amount предметов: сначала из инвентаря игрока, потом из хранилищ. Если не хватило — всё
     * забранное возвращается и результат false. Возвращённые стеки кладутся в out (для единичных предметов с данными).
     */
    public static boolean take(@Nullable ServerPlayer player, ServerLevel level, Bunker b, Predicate<ItemStack> test, int amount,
                               @Nullable List<ItemStack> out) {
        if (amount <= 0) return true;
        List<ItemStack> taken = new ArrayList<>();
        int left = amount;
        if (player != null && !player.hasInfiniteMaterials()) {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty() || !test.test(s)) continue;
                int n = Math.min(left, s.getCount());
                taken.add(s.split(n));
                left -= n;
            }
            inv.setChanged();
        } else if (player != null) {
            // творческий режим: предметы не тратятся, но единичный предмет с данными (блок комнаты) всё равно нужен
            for (ItemStack s : player.getInventory().items) {
                if (!s.isEmpty() && test.test(s) && out != null) {
                    out.add(s.copyWithCount(Math.min(amount, s.getCount())));
                    return true;
                }
            }
            if (out == null) return true;
        }
        for (IItemHandler h : left > 0 ? handlers(level, b) : List.<IItemHandler>of()) {
            for (int i = 0; i < h.getSlots() && left > 0; i++) {
                ItemStack s = h.getStackInSlot(i);
                if (s.isEmpty() || !test.test(s)) continue;
                ItemStack got = h.extractItem(i, Math.min(left, s.getCount()), false);
                if (got.isEmpty() || !test.test(got)) {
                    if (!got.isEmpty()) h.insertItem(i, got, false);
                    continue;
                }
                taken.add(got);
                left -= got.getCount();
            }
        }
        if (left > 0) {
            for (ItemStack s : taken) give(player, level, b, s, player != null ? player.blockPosition() : b.core);
            return false;
        }
        if (out != null) out.addAll(taken);
        return true;
    }

    /** Положить в хранилища бункера; не влезло — игроку; не влезло и туда — выбросить у dropAt. */
    public static void give(@Nullable ServerPlayer player, ServerLevel level, Bunker b, ItemStack stack, BlockPos dropAt) {
        giveAll(player, level, b, List.of(stack), dropAt, handlers(level, b));
    }

    public static void giveAll(@Nullable ServerPlayer player, ServerLevel level, Bunker b, List<ItemStack> stacks, BlockPos dropAt,
                               List<IItemHandler> handlers) {
        for (ItemStack original : stacks) {
            ItemStack rest = original.copy();
            for (IItemHandler h : handlers) {
                if (rest.isEmpty()) break;
                rest = ItemHandlerHelper.insertItemStacked(h, rest, false);
            }
            if (!rest.isEmpty() && player != null) {
                player.getInventory().add(rest);
            }
            if (!rest.isEmpty()) {
                Containers.dropItemStack(level, dropAt.getX() + 0.5, dropAt.getY() + 0.5, dropAt.getZ() + 0.5, rest);
            }
        }
    }
}
