package io.github.itsgiantcookie.hstglabs.grid;

import io.github.itsgiantcookie.hstglabs.Registration;
import mekanism.api.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A data cable segment. Holds no logic of its own: it belongs to a {@link DataNetwork} that owns
 * the shared buffer. Like Mekanism transmitters, each cable saves its share of that buffer.
 */
public class DataCableBlockEntity extends BlockEntity {

    @Nullable
    private DataNetwork network;
    /** Share of the network buffer loaded from NBT, handed to the network on join. */
    private long storedShare;
    private final IDataHandler handler = new CableHandler();

    public DataCableBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.DATA_CABLE_BE.get(), pos, state);
    }

    public CableTier getTier() {
        return getBlockState().getBlock() instanceof DataCableBlock cable ? cable.getTier() : CableTier.BASIC;
    }

    @Nullable
    public DataNetwork getNetwork() {
        return network;
    }

    public void setNetwork(@Nullable DataNetwork network) {
        this.network = network;
    }

    public long takeStoredShare() {
        long share = storedShare;
        storedShare = 0;
        return share;
    }

    public void setStoredShare(long share) {
        storedShare = share;
    }

    public void markAcceptorsDirty() {
        if (network != null) {
            network.markAcceptorsDirty();
        }
    }

    /** Cables accept data on every side; insertion goes straight into the network buffer. */
    public IDataHandler getDataHandler(@Nullable Direction side) {
        return handler;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            DataNetworkManager.get(level).add(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            DataNetworkManager.get(level).remove(this);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("share", network != null ? network.shareOf(this) : storedShare);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storedShare = Math.max(0, tag.getLong("share"));
    }

    private final class CableHandler implements IDataHandler {

        @Override
        public long getStoredData() {
            return network != null ? network.getBuffer() : 0;
        }

        @Override
        public long getDataCapacity() {
            return network != null ? network.getCapacity() : getTier().getCapacity();
        }

        @Override
        public long insertData(long amount, Action action) {
            return network != null ? network.insert(amount, action) : amount;
        }

        @Override
        public long extractData(long amount, Action action) {
            return 0; // cables push; nothing pulls from them
        }
    }
}
