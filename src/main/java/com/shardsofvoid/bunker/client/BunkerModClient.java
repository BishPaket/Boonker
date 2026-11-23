package com.shardsofvoid.bunker.client;

import com.shardsofvoid.bunker.BunkerMod;
import com.shardsofvoid.bunker.item.RadSuitItem;
import com.shardsofvoid.bunker.registry.ModItems;
import com.shardsofvoid.bunker.registry.ModMachines;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Клиентская часть: HUD, счётчик Гейгера, подсветка, цвета уровней костюма. На сервере не загружается. */
@Mod(value = BunkerMod.MODID, dist = Dist.CLIENT)
public final class BunkerModClient {
    public BunkerModClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(RegisterGuiLayersEvent.class, e ->
                e.registerAbove(VanillaGuiLayers.EFFECTS, BunkerMod.id("radiation_hud"), RadiationHud::render));

        modBus.addListener(RegisterColorHandlersEvent.Item.class, e -> e.register(
                (stack, tintIndex) -> tintIndex == 1 ? RadSuitItem.levelColor(RadSuitItem.level(stack)) : 0xFFFFFFFF,
                ModItems.RADSUIT_HELMET.get(), ModItems.RADSUIT_CHESTPLATE.get(),
                ModItems.RADSUIT_LEGGINGS.get(), ModItems.RADSUIT_BOOTS.get()));

        modBus.addListener(RegisterClientExtensionsEvent.class, e -> e.registerItem(new IClientItemExtensions() {
            @Override
            public int getArmorLayerTintColor(ItemStack stack, LivingEntity entity, ArmorMaterial.Layer layer, int layerIdx, int fallbackColor) {
                // слой 1 — накладываемый, тонируется по уровню части (раздел 2.5)
                return layerIdx == 1 ? RadSuitItem.levelColor(RadSuitItem.level(stack)) : 0xFFFFFFFF;
            }
        }, ModItems.RADSUIT_HELMET.get(), ModItems.RADSUIT_CHESTPLATE.get(), ModItems.RADSUIT_LEGGINGS.get(), ModItems.RADSUIT_BOOTS.get()));

        // v3: экраны энергоблоков и манекен станции костюмов
        modBus.addListener(RegisterMenuScreensEvent.class, e -> e.register(ModMachines.MACHINE_MENU.get(), MachineScreen::new));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerBlockEntityRenderer(ModMachines.SUIT_STATION_BE.get(), SuitStationRenderer::new));

        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(ClientTickEvent.Post.class, e -> GeigerClicker.tick());
        game.addListener(RenderLevelStageEvent.class, HighlightRenderer::onRenderLevel);
        game.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> ClientState.reset());
    }
}
