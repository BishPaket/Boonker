package com.shardsofvoid.bunker.registry;

import java.util.List;
import java.util.function.Supplier;

import com.shardsofvoid.bunker.BunkerMod;
import com.shardsofvoid.bunker.block.BatteryBlock;
import com.shardsofvoid.bunker.block.BunkerDoorBlock;
import com.shardsofvoid.bunker.block.BunkerHatchBlock;
import com.shardsofvoid.bunker.block.MachineBlock;
import com.shardsofvoid.bunker.block.PowerCableBlock;
import com.shardsofvoid.bunker.energy.AirFilterUnitBlockEntity;
import com.shardsofvoid.bunker.energy.BatteryBlockEntity;
import com.shardsofvoid.bunker.energy.BunkerMachineBlockEntity;
import com.shardsofvoid.bunker.energy.CellRackBlockEntity;
import com.shardsofvoid.bunker.energy.FurnaceGeneratorBlockEntity;
import com.shardsofvoid.bunker.energy.MachineKind;
import com.shardsofvoid.bunker.energy.PowerInletBlockEntity;
import com.shardsofvoid.bunker.energy.SolarPanelBlockEntity;
import com.shardsofvoid.bunker.energy.SuitStationBlockEntity;
import com.shardsofvoid.bunker.energy.WaterPurifierBlockEntity;
import com.shardsofvoid.bunker.item.CleanWaterBottleItem;
import com.shardsofvoid.bunker.item.EnergyCellItem;
import com.shardsofvoid.bunker.item.FilterItem;
import com.shardsofvoid.bunker.menu.MachineMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Блоки, предметы, блок-сущности и меню ТЗ v3: гермодвери, энергосистема, станции, жизнеобеспечение. */
public final class ModMachines {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, BunkerMod.MODID);

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 6.0F)
                .requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    // ---------------------------------------------------------------- гермодверь и гермолюк (раздел 2)
    public static final DeferredBlock<BunkerDoorBlock> BUNKER_DOOR = ModBlocks.BLOCKS.registerBlock("bunker_door",
            BunkerDoorBlock::new, ModBlocks.bunkerProperties().noOcclusion());
    public static final DeferredBlock<BunkerHatchBlock> BUNKER_HATCH = ModBlocks.BLOCKS.registerBlock("bunker_hatch",
            BunkerHatchBlock::new, ModBlocks.bunkerProperties().noOcclusion());

    // ---------------------------------------------------------------- энергия (разделы 3–5)
    public static final DeferredBlock<PowerCableBlock> POWER_CABLE = ModBlocks.BLOCKS.registerBlock("power_cable",
            PowerCableBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5F)
                    .sound(SoundType.WOOL).noOcclusion());
    public static final DeferredBlock<MachineBlock> FURNACE_GENERATOR = machine("furnace_generator", MachineKind.FURNACE_GENERATOR, machine());
    public static final DeferredBlock<MachineBlock> SOLAR_PANEL = machine("solar_panel", MachineKind.SOLAR_PANEL, machine().noOcclusion());
    public static final DeferredBlock<BatteryBlock> BATTERY = ModBlocks.BLOCKS.registerBlock("bunker_battery", BatteryBlock::new, machine());
    public static final DeferredBlock<MachineBlock> CELL_RACK = machine("cell_rack", MachineKind.CELL_RACK, machine());
    public static final DeferredBlock<MachineBlock> POWER_INLET = machine("bunker_power_inlet", MachineKind.POWER_INLET,
            ModBlocks.bunkerProperties());
    // ---------------------------------------------------------------- станции (разделы 7–8)
    public static final DeferredBlock<MachineBlock> SUIT_STATION = machine("suit_station", MachineKind.SUIT_STATION, machine().noOcclusion());
    public static final DeferredBlock<MachineBlock> AIR_FILTER_UNIT = machine("air_filter_unit", MachineKind.AIR_FILTER_UNIT,
            ModBlocks.bunkerProperties());
    public static final DeferredBlock<MachineBlock> WATER_PURIFIER = machine("water_purifier", MachineKind.WATER_PURIFIER, machine());

    public static final DeferredBlock<MachineBlock> METAL_RECYCLER = machine("metal_recycler", MachineKind.METAL_RECYCLER, machine());

    // ---------------------------------------------------------------- пульт проектирования
    public static final DeferredBlock<com.shardsofvoid.bunker.block.BunkerPlannerBlock> BUNKER_PLANNER = ModBlocks.BLOCKS.registerBlock(
            "bunker_planner", com.shardsofvoid.bunker.block.BunkerPlannerBlock::new, machine());

    private static DeferredBlock<MachineBlock> machine(String name, MachineKind kind, BlockBehaviour.Properties props) {
        return ModBlocks.BLOCKS.registerBlock(name, p -> new MachineBlock(kind, p), props);
    }

    // ---------------------------------------------------------------- предметы
    public static final DeferredItem<DoubleHighBlockItem> BUNKER_DOOR_ITEM = ModItems.ITEMS.registerItem("bunker_door",
            p -> new DoubleHighBlockItem(BUNKER_DOOR.get(), p));
    public static final DeferredItem<BlockItem> BUNKER_HATCH_ITEM = ModItems.ITEMS.registerSimpleBlockItem("bunker_hatch", BUNKER_HATCH);
    public static final DeferredItem<BlockItem> POWER_CABLE_ITEM = ModItems.ITEMS.registerSimpleBlockItem("power_cable", POWER_CABLE);
    public static final DeferredItem<BlockItem> FURNACE_GENERATOR_ITEM = ModItems.ITEMS.registerSimpleBlockItem("furnace_generator", FURNACE_GENERATOR);
    public static final DeferredItem<BlockItem> SOLAR_PANEL_ITEM = ModItems.ITEMS.registerSimpleBlockItem("solar_panel", SOLAR_PANEL);
    public static final DeferredItem<BlockItem> BATTERY_ITEM = ModItems.ITEMS.registerItem("bunker_battery",
            p -> new BlockItem(BATTERY.get(), p.stacksTo(1)));
    public static final DeferredItem<BlockItem> CELL_RACK_ITEM = ModItems.ITEMS.registerSimpleBlockItem("cell_rack", CELL_RACK);
    public static final DeferredItem<BlockItem> POWER_INLET_ITEM = ModItems.ITEMS.registerSimpleBlockItem("bunker_power_inlet", POWER_INLET);
    public static final DeferredItem<BlockItem> SUIT_STATION_ITEM = ModItems.ITEMS.registerSimpleBlockItem("suit_station", SUIT_STATION);
    public static final DeferredItem<BlockItem> AIR_FILTER_UNIT_ITEM = ModItems.ITEMS.registerSimpleBlockItem("air_filter_unit", AIR_FILTER_UNIT);
    public static final DeferredItem<BlockItem> WATER_PURIFIER_ITEM = ModItems.ITEMS.registerSimpleBlockItem("water_purifier", WATER_PURIFIER);
    public static final DeferredItem<BlockItem> METAL_RECYCLER_ITEM = ModItems.ITEMS.registerSimpleBlockItem("metal_recycler", METAL_RECYCLER);
    public static final DeferredItem<com.shardsofvoid.bunker.item.BunkerPlannerItem> BUNKER_PLANNER_ITEM = ModItems.ITEMS.registerItem(
            "bunker_planner", p -> new com.shardsofvoid.bunker.item.BunkerPlannerItem(BUNKER_PLANNER.get(), p));

    public static final DeferredItem<EnergyCellItem> ENERGY_CELL = ModItems.ITEMS.registerItem("energy_cell",
            p -> new EnergyCellItem(p.stacksTo(1)));
    public static final DeferredItem<Item> ACTIVATED_CARBON = ModItems.ITEMS.registerSimpleItem("activated_carbon");
    public static final DeferredItem<FilterItem> AIR_FILTER_CARTRIDGE = ModItems.ITEMS.registerItem("air_filter_cartridge",
            p -> new FilterItem(true, p.stacksTo(1)));
    public static final DeferredItem<FilterItem> WATER_FILTER_CARTRIDGE = ModItems.ITEMS.registerItem("water_filter_cartridge",
            p -> new FilterItem(false, p.stacksTo(1)));
    public static final DeferredItem<CleanWaterBottleItem> CLEAN_WATER_BOTTLE = ModItems.ITEMS.registerItem("clean_water_bottle",
            p -> new CleanWaterBottleItem(p.stacksTo(16)));

    // ---------------------------------------------------------------- блок-сущности
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FurnaceGeneratorBlockEntity>> FURNACE_GENERATOR_BE =
            be("furnace_generator", FurnaceGeneratorBlockEntity::new, FURNACE_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL_BE =
            be("solar_panel", SolarPanelBlockEntity::new, SOLAR_PANEL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBlockEntity>> BATTERY_BE =
            be("bunker_battery", BatteryBlockEntity::new, BATTERY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CellRackBlockEntity>> CELL_RACK_BE =
            be("cell_rack", CellRackBlockEntity::new, CELL_RACK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerInletBlockEntity>> POWER_INLET_BE =
            be("bunker_power_inlet", PowerInletBlockEntity::new, POWER_INLET);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuitStationBlockEntity>> SUIT_STATION_BE =
            be("suit_station", SuitStationBlockEntity::new, SUIT_STATION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirFilterUnitBlockEntity>> AIR_FILTER_UNIT_BE =
            be("air_filter_unit", AirFilterUnitBlockEntity::new, AIR_FILTER_UNIT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterPurifierBlockEntity>> WATER_PURIFIER_BE =
            be("water_purifier", WaterPurifierBlockEntity::new, WATER_PURIFIER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.shardsofvoid.bunker.energy.MetalRecyclerBlockEntity>> METAL_RECYCLER_BE =
            be("metal_recycler", com.shardsofvoid.bunker.energy.MetalRecyclerBlockEntity::new, METAL_RECYCLER);

    @SuppressWarnings("DataFlowIssue")
    private static <T extends BunkerMachineBlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> be(
            String name, BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return ModBlocks.BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }

    public static BunkerMachineBlockEntity create(MachineKind kind, BlockPos pos, BlockState state) {
        return switch (kind) {
            case FURNACE_GENERATOR -> new FurnaceGeneratorBlockEntity(pos, state);
            case SOLAR_PANEL -> new SolarPanelBlockEntity(pos, state);
            case BATTERY -> new BatteryBlockEntity(pos, state);
            case CELL_RACK -> new CellRackBlockEntity(pos, state);
            case POWER_INLET -> new PowerInletBlockEntity(pos, state);
            case SUIT_STATION -> new SuitStationBlockEntity(pos, state);
            case AIR_FILTER_UNIT -> new AirFilterUnitBlockEntity(pos, state);
            case WATER_PURIFIER -> new WaterPurifierBlockEntity(pos, state);
            case METAL_RECYCLER -> new com.shardsofvoid.bunker.energy.MetalRecyclerBlockEntity(pos, state);
        };
    }

    // ---------------------------------------------------------------- меню
    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> MACHINE_MENU = MENUS.register("machine",
            () -> IMenuTypeExtension.create(MachineMenu::new));

    private ModMachines() {}

    /** Вызов, чтобы класс загрузился и зарегистрировал DeferredBlock/DeferredItem до событий регистрации. */
    public static void init() {}

    public static void creativeItems(net.minecraft.world.item.CreativeModeTab.Output out) {
        for (DeferredItem<? extends Item> item : List.of(BUNKER_DOOR_ITEM, BUNKER_HATCH_ITEM, POWER_CABLE_ITEM, FURNACE_GENERATOR_ITEM,
                SOLAR_PANEL_ITEM, BATTERY_ITEM, CELL_RACK_ITEM, POWER_INLET_ITEM, SUIT_STATION_ITEM, AIR_FILTER_UNIT_ITEM,
                WATER_PURIFIER_ITEM, METAL_RECYCLER_ITEM, BUNKER_PLANNER_ITEM, ENERGY_CELL, ACTIVATED_CARBON, AIR_FILTER_CARTRIDGE, WATER_FILTER_CARTRIDGE, CLEAN_WATER_BOTTLE)) {
            out.accept(item.get());
        }
        ItemStack full = new ItemStack(ENERGY_CELL.get());
        EnergyCellItem.setEnergy(full, EnergyCellItem.capacity());
        out.accept(full);
    }
}
