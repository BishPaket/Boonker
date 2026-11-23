package com.shardsofvoid.bunker.registry;

import java.util.EnumMap;
import java.util.List;

import com.shardsofvoid.bunker.BunkerMod;
import com.shardsofvoid.bunker.config.BunkerStartupConfig;
import com.shardsofvoid.bunker.item.BunkerCoreItem;
import com.shardsofvoid.bunker.item.BunkerRoomItem;
import com.shardsofvoid.bunker.item.RadSuitItem;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BunkerMod.MODID);
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, BunkerMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BunkerMod.MODID);

    /**
     * Материал костюма. Слой 0 — основа, слой 1 ("_overlay") — накладываемый слой,
     * который клиент тонирует по уровню части (раздел 2.5).
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> RADSUIT_MATERIAL = ARMOR_MATERIALS.register("radsuit",
            () -> new ArmorMaterial(
                    Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                        map.put(ArmorItem.Type.BOOTS, BunkerStartupConfig.ARMOR_BOOTS.get());
                        map.put(ArmorItem.Type.LEGGINGS, BunkerStartupConfig.ARMOR_LEGS.get());
                        map.put(ArmorItem.Type.CHESTPLATE, BunkerStartupConfig.ARMOR_CHEST.get());
                        map.put(ArmorItem.Type.HELMET, BunkerStartupConfig.ARMOR_HELMET.get());
                        map.put(ArmorItem.Type.BODY, BunkerStartupConfig.ARMOR_CHEST.get());
                    }),
                    BunkerStartupConfig.ENCHANTABILITY.get(),
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.of(Items.IRON_INGOT),
                    List.of(
                            new ArmorMaterial.Layer(BunkerMod.id("radsuit"), "", false),
                            new ArmorMaterial.Layer(BunkerMod.id("radsuit"), "_overlay", true)),
                    BunkerStartupConfig.ARMOR_TOUGHNESS.get().floatValue(),
                    BunkerStartupConfig.ARMOR_KNOCKBACK_RESISTANCE.get().floatValue()));

    public static final DeferredItem<RadSuitItem> RADSUIT_HELMET = suit("radsuit_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<RadSuitItem> RADSUIT_CHESTPLATE = suit("radsuit_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<RadSuitItem> RADSUIT_LEGGINGS = suit("radsuit_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<RadSuitItem> RADSUIT_BOOTS = suit("radsuit_boots", ArmorItem.Type.BOOTS);

    /** «Радиационный шаблон улучшения» для кузнечного стола. */
    public static final DeferredItem<SmithingTemplateItem> RADSUIT_UPGRADE_TEMPLATE = ITEMS.registerItem("radsuit_upgrade_template",
            p -> new SmithingTemplateItem(
                    Component.translatable("item.bunker.radsuit_upgrade_template.applies_to").withStyle(ChatFormatting.BLUE),
                    Component.translatable("item.bunker.radsuit_upgrade_template.ingredients").withStyle(ChatFormatting.BLUE),
                    Component.translatable("item.bunker.radsuit_upgrade_template.title").withStyle(ChatFormatting.GRAY),
                    Component.translatable("item.bunker.radsuit_upgrade_template.base_slot"),
                    Component.translatable("item.bunker.radsuit_upgrade_template.additions_slot"),
                    List.of(
                            ResourceLocation.withDefaultNamespace("item/empty_armor_slot_helmet"),
                            ResourceLocation.withDefaultNamespace("item/empty_armor_slot_chestplate"),
                            ResourceLocation.withDefaultNamespace("item/empty_armor_slot_leggings"),
                            ResourceLocation.withDefaultNamespace("item/empty_armor_slot_boots")),
                    List.of(
                            ResourceLocation.withDefaultNamespace("item/empty_slot_diamond"),
                            ResourceLocation.withDefaultNamespace("item/empty_slot_ingot"))));

    public static final DeferredItem<BlockItem> BUNKER_METAL = ITEMS.registerSimpleBlockItem("bunker_metal", ModBlocks.BUNKER_METAL);

    public static final DeferredItem<BunkerCoreItem> BUNKER_CORE = ITEMS.registerItem("bunker_core",
            p -> new BunkerCoreItem(ModBlocks.BUNKER_CORE.get(), p.stacksTo(1).rarity(Rarity.RARE).fireResistant()));

    public static final DeferredItem<BunkerRoomItem> BUNKER_ROOM_BLOCK = ITEMS.registerItem("bunker_room_block",
            p -> new BunkerRoomItem(ModBlocks.BUNKER_ROOM.get(), p.stacksTo(16).rarity(Rarity.UNCOMMON)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.bunker"))
            .icon(() -> new ItemStack(BUNKER_CORE.get()))
            .displayItems((params, out) -> {
                out.accept(BUNKER_METAL.get());
                out.accept(BUNKER_CORE.get());
                out.accept(BUNKER_ROOM_BLOCK.get());
                for (DeferredItem<RadSuitItem> part : List.of(RADSUIT_HELMET, RADSUIT_CHESTPLATE, RADSUIT_LEGGINGS, RADSUIT_BOOTS)) {
                    for (int level = 1; level <= 3; level++) {
                        ItemStack stack = new ItemStack(part.get());
                        stack.set(ModComponents.RADSUIT_LEVEL.get(), level);
                        out.accept(stack);
                    }
                }
                out.accept(RADSUIT_UPGRADE_TEMPLATE.get());
            })
            .build());

    private static DeferredItem<RadSuitItem> suit(String name, ArmorItem.Type type) {
        return ITEMS.registerItem(name, p -> new RadSuitItem(RADSUIT_MATERIAL, type,
                p.durability(type.getDurability(BunkerStartupConfig.DURABILITY_MULTIPLIER.get()))));
    }

    private ModItems() {}
}
