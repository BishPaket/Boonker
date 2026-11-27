package com.shardsofvoid.bunker.block;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.shardsofvoid.bunker.bunker.BunkerManager;
import com.shardsofvoid.bunker.energy.BatteryBlockEntity;
import com.shardsofvoid.bunker.energy.BunkerMachineBlockEntity;
import com.shardsofvoid.bunker.energy.MachineKind;
import com.shardsofvoid.bunker.energy.MachineStatus;
import com.shardsofvoid.bunker.energy.SuitStationBlockEntity;
import com.shardsofvoid.bunker.energy.WaterPurifierBlockEntity;
import com.shardsofvoid.bunker.menu.MachineMenu;
import com.shardsofvoid.bunker.registry.ModItems;
import com.shardsofvoid.bunker.registry.ModMachines;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Энергоблок мода: генераторы, хранилища, энерговвод, станции, ФВУ. FACING — лицевая сторона к игроку,
 * INDICATOR — лампа состояния (горит / выключена / красная «Лишний»).
 */
public class MachineBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Indicator> INDICATOR = EnumProperty.create("indicator", Indicator.class);

    private static final VoxelShape SOLAR_SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    public enum Indicator implements StringRepresentable {
        IDLE, ON, EXCESS;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public final MachineKind kind;

    public MachineBlock(MachineKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(INDICATOR, Indicator.IDLE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new MachineBlock(kind, p));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, INDICATOR);
    }

    /** Блоки в стене (ФВУ, энерговвод) смотрят в любую сторону, остальные — только по горизонтали. */
    public boolean wallMounted() {
        return kind == MachineKind.AIR_FILTER_UNIT || kind == MachineKind.POWER_INLET;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = wallMounted() ? context.getNearestLookingDirection().getOpposite()
                : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind == MachineKind.SOLAR_PANEL ? SOLAR_SHAPE : Shapes.block();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModMachines.create(kind, pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof BunkerMachineBlockEntity be) {
            be.setOwner(p.getUUID(), p.getGameProfile().getName());
        }
        if (level instanceof ServerLevel sl) BunkerManager.get(sl).cablesDirty = true;
    }

    public boolean hasMenu() {
        return kind != MachineKind.SOLAR_PANEL && kind != MachineKind.BATTERY && kind != MachineKind.POWER_INLET;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (kind != MachineKind.WATER_PURIFIER || !(level.getBlockEntity(pos) instanceof WaterPurifierBlockEntity purifier)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // v3 8.4: вёдра и бутылки воды — в бак сырой воды; пустая бутылка — «Бутылка чистой воды» (250 mB)
        if (stack.is(Items.WATER_BUCKET)) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (purifier.addRaw(1000) < 1000) return ItemInteractionResult.FAIL;
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            return ItemInteractionResult.SUCCESS;
        }
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (stack.is(Items.POTION) && potion != null && potion.is(Potions.WATER)) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (purifier.addRaw(250) < 250) return ItemInteractionResult.FAIL;
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (purifier.clean() < 250) return ItemInteractionResult.FAIL;
            purifier.takeClean(250);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, cleanBottle()));
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.is(Items.BUCKET) && player.isShiftKeyDown()) {
            // Shift + пустое ведро — ведро чистой воды из бака (1000 mB); без Shift ведро не трогаем
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (purifier.clean() < 1000) return ItemInteractionResult.FAIL;
            purifier.takeClean(1000);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Бутылка чистой воды: предмет из конфига water.bottleItem, по умолчанию «Очищенная вода» Wastelands; нет его — обычная вода. */
    private static ItemStack cleanBottle() {
        String id = com.shardsofvoid.bunker.config.BunkerConfig.WATER_BOTTLE_ITEM.get();
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl != null && rl.equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("potion"))) {
            return PotionContents.createItemStack(Items.POTION, Potions.WATER);
        }
        if (rl != null) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(rl);
            if (item.isPresent() && item.get() != Items.AIR) return new ItemStack(item.get());
        }
        return PotionContents.createItemStack(Items.POTION, Potions.WATER);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof BunkerMachineBlockEntity be) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        if (kind == MachineKind.SUIT_STATION && player.isShiftKeyDown() && be instanceof SuitStationBlockEntity station) {
            station.swapWith(player);
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }
        if (hasMenu()) {
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new MachineMenu(id, inv, be),
                    Component.translatable(getDescriptionId())), buf -> {
                buf.writeBlockPos(pos);
                buf.writeVarInt(kind.ordinal());
            });
        } else {
            sp.displayClientMessage(statusLine(be), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Строка состояния для блоков без экрана (панель, аккумулятор, энерговвод). */
    private static Component statusLine(BunkerMachineBlockEntity be) {
        Component status = Component.translatable("machine.bunker.status." + be.status().key());
        if (be instanceof BatteryBlockEntity battery) {
            return Component.translatable("message.bunker.battery", status, battery.energy(), BatteryBlockEntity.capacity())
                    .withStyle(ChatFormatting.AQUA);
        }
        return Component.translatable("message.bunker.machine", status, be.lastRate())
                .withStyle(be.status() == MachineStatus.WORKING ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof BunkerMachineBlockEntity be && kind != MachineKind.BATTERY) {
                be.dropContents(level, pos);
            }
            if (level instanceof ServerLevel sl) BunkerManager.get(sl).cablesDirty = true;
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return kind == MachineKind.BATTERY;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BatteryBlockEntity battery ? battery.comparatorSignal() : 0;
    }
}
