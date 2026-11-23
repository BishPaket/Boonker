package com.shardsofvoid.bunker.block;

import java.util.Map;

import com.mojang.serialization.MapCodec;
import com.shardsofvoid.bunker.bunker.BunkerManager;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v3 4.3: силовой кабель — тонкий блок с соединениями по 6 сторонам. Соединяется с кабелями и энергоблоками мода.
 * Потерь нет; граф пересчитывается только при установке и снятии (через флаг менеджера).
 */
public class PowerCableBlock extends Block {
    public static final MapCodec<PowerCableBlock> CODEC = simpleCodec(PowerCableBlock::new);
    public static final Map<Direction, BooleanProperty> PROPS = PipeBlock.PROPERTY_BY_DIRECTION;

    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, VoxelShape> ARMS = Util.make(new java.util.EnumMap<>(Direction.class), m -> {
        m.put(Direction.DOWN, Block.box(6, 0, 6, 10, 6, 10));
        m.put(Direction.UP, Block.box(6, 10, 6, 10, 16, 10));
        m.put(Direction.NORTH, Block.box(6, 6, 0, 10, 10, 6));
        m.put(Direction.SOUTH, Block.box(6, 6, 10, 10, 10, 16));
        m.put(Direction.WEST, Block.box(0, 6, 6, 6, 10, 10));
        m.put(Direction.EAST, Block.box(10, 6, 6, 16, 10, 10));
    });

    public PowerCableBlock(Properties properties) {
        super(properties);
        BlockState def = stateDefinition.any();
        for (BooleanProperty p : PROPS.values()) def = def.setValue(p, false);
        registerDefaultState(def);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PROPS.values().toArray(new BooleanProperty[0]));
    }

    public static boolean connectsTo(BlockState state) {
        return state.getBlock() instanceof PowerCableBlock || state.getBlock() instanceof MachineBlock;
    }

    private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction d : Direction.values()) {
            state = state.setValue(PROPS.get(d), connectsTo(level.getBlockState(pos.relative(d))));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PROPS.get(dir), connectsTo(neighbor));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Direction d : Direction.values()) {
            if (state.getValue(PROPS.get(d))) shape = Shapes.or(shape, ARMS.get(d));
        }
        return shape;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel sl) BunkerManager.get(sl).cablesDirty = true;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) BunkerManager.get(sl).cablesDirty = true;
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
