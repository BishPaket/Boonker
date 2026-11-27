package com.shardsofvoid.bunker.registry;

import com.shardsofvoid.bunker.BunkerMod;
import com.shardsofvoid.bunker.block.BunkerCoreBlock;
import com.shardsofvoid.bunker.block.BunkerRoomBlock;
import com.shardsofvoid.bunker.block.BunkerPartBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BunkerMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BunkerMod.MODID);

    /** Значения обсидиана: твёрдость 50, сопротивление взрывам 1200. */
    public static final float HARDNESS = 50.0F;
    public static final float BLAST_RESISTANCE = 1200.0F;

    public static BlockBehaviour.Properties bunkerProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(HARDNESS, BLAST_RESISTANCE)
                .requiresCorrectToolForDrops()
                .sound(SoundType.NETHERITE_BLOCK)
                .pushReaction(PushReaction.BLOCK);
    }

    /** Бункерный металл — обычный блок; не горит и не падает по умолчанию, поршни не двигают. */
    public static final DeferredBlock<Block> BUNKER_METAL = BLOCKS.registerSimpleBlock("bunker_metal", bunkerProperties());

    /** Блок безопасной зоны для администраторов: нерушимый, ставят операторы. */
    public static final DeferredBlock<com.shardsofvoid.bunker.safezone.SafeZoneBlock> SAFE_ZONE = BLOCKS.registerBlock("safe_zone",
            com.shardsofvoid.bunker.safezone.SafeZoneBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .strength(-1.0F, 3600000.0F).noLootTable().lightLevel(s -> 10).sound(SoundType.AMETHYST));

    /** Сломанный бункерный металл из заброшенных бункеров: ломается обычной киркой, на станции очистки становится металлом. */
    public static final DeferredBlock<Block> BROKEN_BUNKER_METAL = BLOCKS.registerSimpleBlock("broken_bunker_metal",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(4.0F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK));

    public static final DeferredBlock<BunkerCoreBlock> BUNKER_CORE = BLOCKS.registerBlock("bunker_core",
            BunkerCoreBlock::new, bunkerProperties().lightLevel(s -> 7));

    public static final DeferredBlock<BunkerRoomBlock> BUNKER_ROOM = BLOCKS.registerBlock("bunker_room",
            BunkerRoomBlock::new, bunkerProperties().lightLevel(s -> 4));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BunkerPartBlockEntity>> PART_BE =
            BLOCK_ENTITIES.register("bunker_part", () -> BlockEntityType.Builder
                    .of(BunkerPartBlockEntity::new, BUNKER_CORE.get(), BUNKER_ROOM.get())
                    .build(null));

    private ModBlocks() {}
}
