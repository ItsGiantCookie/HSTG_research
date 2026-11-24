package com.hstg.research.block;

import com.hstg.research.cable.CableTier;
import com.hstg.research.capability.ModCapabilities;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Tiered data cable (mekstyle transport class: 6x6 core + arms, multipart). Connects to other
 * data cables of any tier and to every block that exposes the data capability on that side.
 */
public class DataCableBlock extends Block implements EntityBlock {

    public static final Map<Direction, BooleanProperty> CONNECTIONS = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape CORE = box(5, 5, 5, 11, 11, 11);
    private static final VoxelShape[] ARMS = {
          box(5, 0, 5, 11, 5, 11),   // DOWN
          box(5, 11, 5, 11, 16, 11), // UP
          box(5, 5, 0, 11, 11, 5),   // NORTH
          box(5, 5, 11, 11, 11, 16), // SOUTH
          box(0, 5, 5, 5, 11, 11),   // WEST
          box(11, 5, 5, 16, 11, 11), // EAST
    };
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = CORE;
            for (Direction dir : Direction.values()) {
                if ((mask & (1 << dir.ordinal())) != 0) {
                    shape = Shapes.or(shape, ARMS[dir.ordinal()]);
                }
            }
            SHAPES[mask] = shape;
        }
    }

    private final CableTier tier;

    public DataCableBlock(CableTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : CONNECTIONS.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    public CableTier getTier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        // Tiered blocks are only created from code; the codec is not used for data-driven blocks.
        return simpleCodec(properties -> new DataCableBlock(tier, properties));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos,
          BlockPos neighborPos) {
        if (level instanceof Level realLevel) {
            BlockState updated = state.setValue(CONNECTIONS.get(direction), canConnect(realLevel, pos, direction));
            markAcceptorsDirty(realLevel, pos);
            return updated;
        }
        return state;
    }

    /** Capability changes (e.g. the lab switching a face to Data) arrive as neighbor updates. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        BlockState updated = withConnections(state, level, pos);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
        markAcceptorsDirty(level, pos);
    }

    private static void markAcceptorsDirty(Level level, BlockPos pos) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DataCableBlockEntity cable) {
            cable.markAcceptorsDirty();
        }
    }

    private BlockState withConnections(BlockState state, Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(dir), canConnect(level, pos, dir));
        }
        return state;
    }

    private static boolean canConnect(Level level, BlockPos pos, Direction dir) {
        BlockPos neighbor = pos.relative(dir);
        if (!level.isLoaded(neighbor)) {
            return false;
        }
        if (level.getBlockState(neighbor).getBlock() instanceof DataCableBlock) {
            return true;
        }
        return level.getCapability(ModCapabilities.DATA, neighbor, dir.getOpposite()) != null;
    }

    public static int connectionMask(BlockState state) {
        int mask = 0;
        for (Direction dir : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(dir))) {
                mask |= 1 << dir.ordinal();
            }
        }
        return mask;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[connectionMask(state)];
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("hstg_research.cable.capacity", String.format("%,d", tier.getCapacity()))
              .withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DataCableBlockEntity(pos, state);
    }
}
