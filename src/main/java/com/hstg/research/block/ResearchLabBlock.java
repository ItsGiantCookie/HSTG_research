package com.hstg.research.block;

import com.hstg.research.compat.MekanismCompat;
import com.hstg.research.upgrade.UpgradeComponent;
import mekanism.api.Upgrade;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.hstg.research.Registration;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/** Full-cube machine (mekstyle simple_machine); front faces the player who placed it. */
public class ResearchLabBlock extends Block implements EntityBlock {

    public static final MapCodec<ResearchLabBlock> CODEC = simpleCodec(ResearchLabBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public ResearchLabBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Let Mekanism's Configurator act on the block instead of opening the GUI. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
          InteractionHand hand, BlockHitResult hit) {
        return MekanismCompat.isConfigurator(stack) ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                                                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ResearchLabBlockEntity lab) {
            player.openMenu(lab, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResearchLabBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != Registration.RESEARCH_LAB_BE.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<ResearchLabBlockEntity>) ResearchLabBlockEntity::serverTick;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ResearchLabBlockEntity lab) {
            for (int slot = 0; slot < lab.getInventory().getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), lab.getInventory().getStackInSlot(slot));
            }
            // upgrade slots and installed upgrades come back as Mekanism upgrade items
            UpgradeComponent upgrades = lab.getUpgrades();
            for (int slot = 0; slot < upgrades.getSlots().getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), upgrades.getSlots().getStackInSlot(slot));
            }
            for (Upgrade upgrade : UpgradeComponent.SUPPORTED) {
                if (upgrades.get(upgrade) > 0) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), UpgradeComponent.stackOf(upgrade, upgrades.get(upgrade)));
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
