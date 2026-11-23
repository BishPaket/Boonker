package com.shardsofvoid.bunker.bunker;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.shardsofvoid.bunker.block.BunkerPartBlockEntity;
import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.item.BunkerCoreItem;
import com.shardsofvoid.bunker.item.RadSuitItem;
import com.shardsofvoid.bunker.radiation.RadiationSystem;
import com.shardsofvoid.bunker.registry.ModAttachments;
import com.shardsofvoid.bunker.registry.ModBlocks;
import com.shardsofvoid.bunker.registry.ModItems;
import com.shardsofvoid.bunker.registry.ModMisc;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Обработчики игровых событий бункеров: выдача ядра, защита блоков, взрывы, отслеживание оболочки. */
public final class BunkerEvents {
    private BunkerEvents() {}

    // ---------------------------------------------------------------- выдача ядра (раздел 4.1)

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getData(ModAttachments.CORE_GIVEN)) {
            giveCore(player);
            player.setData(ModAttachments.CORE_GIVEN, true);
        }
    }

    /** Выдать ядро; если инвентарь полон — бросить под ноги, подобрать может только владелец. */
    public static void giveCore(ServerPlayer player) {
        ItemStack stack = BunkerCoreItem.createFor(player);
        if (!player.getInventory().add(stack)) {
            BunkerManager.dropForOwner(player.serverLevel(), player.blockPosition(), stack, player.getUUID());
        }
        player.sendSystemMessage(Component.translatable("message.bunker.core.given").withStyle(ChatFormatting.YELLOW));
    }

    /** bunker.core.keepOnDeath: ядро не выпадает, а возвращается после возрождения. */
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !BunkerConfig.BUNKER_CORE_KEEP_ON_DEATH.get()) return;
        List<ItemStack> kept = new ArrayList<>();
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity entity = it.next();
            if (entity.getItem().is(ModItems.BUNKER_CORE.get())) {
                kept.add(entity.getItem().copy());
                it.remove();
            }
        }
        if (!kept.isEmpty()) player.setData(ModAttachments.KEPT_ITEMS, List.copyOf(kept));
    }

    public static void onClone(PlayerEvent.Clone event) {
        RadiationSystem.onClone(event);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        List<ItemStack> kept = event.getOriginal().getData(ModAttachments.KEPT_ITEMS);
        if (kept.isEmpty()) return;
        for (ItemStack stack : kept) {
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy)) player.drop(copy, false);
        }
        player.setData(ModAttachments.KEPT_ITEMS, List.of());
    }

    // ---------------------------------------------------------------- защита блоков

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();
        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        if (state.is(ModMisc.INDESTRUCTIBLE) && player instanceof FakePlayer && BunkerConfig.BUNKER_METAL_BLOCK_FAKE_PLAYERS.get()) {
            event.setCanceled(true); // торнадо Weather2 и машины ломают через «фейкового игрока»
            return;
        }
        boolean op = player.hasPermissions(2);

        if (state.is(ModBlocks.BUNKER_CORE.get()) || state.is(ModBlocks.BUNKER_ROOM.get())) {
            UUID owner = null;
            String ownerName = "";
            if (level.getBlockEntity(pos) instanceof BunkerPartBlockEntity be) {
                owner = be.owner();
                ownerName = be.ownerName();
            }
            Bunker b = state.is(ModBlocks.BUNKER_CORE.get()) ? BunkerManager.get(level).byCore(pos) : BunkerManager.get(level).byRoom(pos);
            if (b != null) {
                owner = b.owner;
                ownerName = b.ownerName;
            }
            if (owner != null && !owner.equals(player.getUUID()) && !op) {
                event.setCanceled(true);
                player.displayClientMessage(Component.translatable("message.bunker.break_denied", ownerName).withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        if (BunkerConfig.BUNKER_METAL_OWNER_ONLY_BREAK.get() && state.is(ModMisc.SHELL_BLOCKS)) {
            Bunker b = BunkerManager.get(level).watchedAt(pos);
            if (b != null && b.isActive() && !b.owner.equals(player.getUUID()) && !b.trusted.contains(player.getUUID()) && !op) {
                event.setCanceled(true);
                player.displayClientMessage(Component.translatable("message.bunker.break_denied", b.ownerName).withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /** Взрывы не разрушают блоки мода, даже если мод взрыва игнорирует сопротивление (TaCZ и др.). */
    public static void onDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        event.getAffectedBlocks().removeIf(pos -> level.getBlockState(pos).is(ModMisc.INDESTRUCTIBLE));
    }

    // ---------------------------------------------------------------- отслеживание оболочки (раздел 5.2)

    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) BunkerManager.get(level).onBlockChanged(event.getPos());
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) BunkerManager.get(level).onBlockChanged(event.getPos());
    }

    /** Двери/люки меняют состояние без обновления соседей — перепроверяем по клику. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BunkerManager m = BunkerManager.get(level);
            Bunker b = m.watchedAt(event.getPos());
            if (b != null) m.schedule(b, 2);
        }
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) BunkerManager.get(level).onChunkLoad(event.getChunk().getPos());
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) BunkerManager.get(level).tick();
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) BunkerManager.unload(level);
    }

    // ---------------------------------------------------------------- костюм

    /** Сломанная часть костюма не даёт и физической защиты. */
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof RadSuitItem && RadSuitItem.isBroken(stack)) {
            event.removeAllModifiersFor(Attributes.ARMOR);
            event.removeAllModifiersFor(Attributes.ARMOR_TOUGHNESS);
            event.removeAllModifiersFor(Attributes.KNOCKBACK_RESISTANCE);
        }
    }
}
