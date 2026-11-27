package com.shardsofvoid.bunker;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.shardsofvoid.bunker.bunker.BunkerActions;
import com.shardsofvoid.bunker.bunker.BunkerCommands;
import com.shardsofvoid.bunker.bunker.BunkerEvents;
import com.shardsofvoid.bunker.bunker.BunkerManager;
import com.shardsofvoid.bunker.bunker.BunkerUpgrades;
import com.shardsofvoid.bunker.config.BunkerClientConfig;
import com.shardsofvoid.bunker.config.BunkerConfig;
import com.shardsofvoid.bunker.config.BunkerStartupConfig;
import com.shardsofvoid.bunker.network.ModNetwork;
import com.shardsofvoid.bunker.radiation.RadiationCommands;
import com.shardsofvoid.bunker.radiation.RadiationSystem;
import com.shardsofvoid.bunker.registry.ModAttachments;
import com.shardsofvoid.bunker.registry.ModBlocks;
import com.shardsofvoid.bunker.item.EnergyCellItem;
import com.shardsofvoid.bunker.registry.ModComponents;
import com.shardsofvoid.bunker.registry.ModMachines;
import com.shardsofvoid.bunker.registry.ModItems;
import com.shardsofvoid.bunker.registry.ModMisc;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Bunker &amp; Radiation — радиация по всей карте, радиационный костюм и система герметичных бункеров
 * для нового сезона Shards of Void (ТЗ v2), плюс энергия, расширение и жизнеобеспечение бункеров (ТЗ v3).
 */
@Mod(BunkerMod.MODID)
public final class BunkerMod {
    public static final String MODID = "bunker";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /** v3 3.2, 5.2, 8.4: энергия — только у энерговвода, аккумулятора, стойки и ячейки; вода — у станции очистки. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModMachines.POWER_INLET_BE.get(), (be, side) -> be.energyStorage());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModMachines.BATTERY_BE.get(), (be, side) -> be.energyStorage());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModMachines.CELL_RACK_BE.get(), (be, side) -> be.energyStorage());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModMachines.WATER_PURIFIER_BE.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModMachines.FURNACE_GENERATOR_BE.get(), (be, side) -> be.items());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModMachines.CELL_RACK_BE.get(), (be, side) -> be.items());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModMachines.AIR_FILTER_UNIT_BE.get(), (be, side) -> be.items());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModMachines.WATER_PURIFIER_BE.get(), (be, side) -> be.items());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModMachines.SUIT_STATION_BE.get(), (be, side) -> be.items());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModMachines.METAL_RECYCLER_BE.get(), (be, side) -> be.items());
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new ComponentEnergyStorage(stack,
                ModComponents.STORED_ENERGY.get(), EnergyCellItem.capacity(), BunkerConfig.RACK_RATE_PER_CELL.get()), ModMachines.ENERGY_CELL.get());
    }

    public BunkerMod(IEventBus modBus, ModContainer container) {
        // STARTUP — до регистрации предметов (параметры ArmorMaterial)
        container.registerConfig(ModConfig.Type.STARTUP, BunkerStartupConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, BunkerConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, BunkerClientConfig.SPEC);

        ModComponents.COMPONENTS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.BLOCK_ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.ARMOR_MATERIALS.register(modBus);
        ModItems.TABS.register(modBus);
        ModMisc.SOUNDS.register(modBus);
        ModMisc.RECIPE_SERIALIZERS.register(modBus);
        // v3: энергия и жизнеобеспечение
        ModMachines.init();
        ModMachines.MENUS.register(modBus);
        ModMisc.LOOT_MODIFIERS.register(modBus);
        ModMisc.LOOT_ENTRIES.register(modBus);
        ModMisc.LOOT_FUNCTIONS.register(modBus);
        ModMisc.FEATURES.register(modBus);
        modBus.addListener(RegisterCapabilitiesEvent.class, BunkerMod::registerCapabilities);

        modBus.addListener(RegisterPayloadHandlersEvent.class, ModNetwork::register);

        IEventBus game = NeoForge.EVENT_BUS;
        // радиация
        game.addListener(PlayerTickEvent.Post.class, RadiationSystem::onPlayerTick);
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, RadiationSystem::onLogout);
        game.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            RadiationSystem.onLogin(e);
            BunkerEvents.onLogin(e);
        });
        game.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) RadiationSystem.onRespawnOrTeleport(p);
        });
        game.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) RadiationSystem.onRespawnOrTeleport(p);
        });
        game.addListener(PlayerEvent.Clone.class, BunkerEvents::onClone);
        game.addListener(LivingDropsEvent.class, BunkerEvents::onDrops);
        game.addListener(ItemAttributeModifierEvent.class, BunkerEvents::onItemAttributes);

        // бункеры
        game.addListener(BlockEvent.BreakEvent.class, BunkerEvents::onBreak);
        game.addListener(BlockEvent.NeighborNotifyEvent.class, BunkerEvents::onNeighborNotify);
        game.addListener(BlockEvent.EntityPlaceEvent.class, BunkerEvents::onPlace);
        game.addListener(PlayerInteractEvent.RightClickBlock.class, BunkerEvents::onRightClickBlock);
        game.addListener(ExplosionEvent.Detonate.class, BunkerEvents::onDetonate);
        game.addListener(ChunkEvent.Load.class, BunkerEvents::onChunkLoad);
        game.addListener(LevelTickEvent.Post.class, BunkerEvents::onLevelTick);
        game.addListener(LevelEvent.Unload.class, BunkerEvents::onLevelUnload);
        game.addListener(net.neoforged.neoforge.event.server.ServerStartedEvent.class, SelfTest::onServerStarted);
        game.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> BunkerActions.forget(e.getEntity().getUUID()));
        game.addListener(AnvilUpdateEvent.class, BunkerEvents::onAnvilUpdate);

        game.addListener(RegisterCommandsEvent.class, e -> {
            RadiationCommands.register(e.getDispatcher());
            BunkerCommands.register(e.getDispatcher());
        });
        game.addListener(AddReloadListenerEvent.class, e -> e.addListener(new BunkerUpgrades()));
        game.addListener(ServerStoppedEvent.class, e -> {
            BunkerManager.clearAll();
            RadiationSystem.clearAll();
        });
    }
}
