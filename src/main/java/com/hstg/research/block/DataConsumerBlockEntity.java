package com.hstg.research.block;

import com.hstg.research.Registration;
import com.hstg.research.api.IDataHandler;
import mekanism.api.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Dummy data sink: accepts data on every side and burns a fixed amount per tick. */
public class DataConsumerBlockEntity extends BlockEntity {

    public static final long CAPACITY = 1_000L;
    public static final long CONSUMPTION_PER_TICK = 20L;

    private final IDataHandler handler = new Input();
    private long stored;
    private long lastConsumed;
    private long totalConsumed;

    public DataConsumerBlockEntity(BlockPos pos, BlockState state) {
        super(Registration.DATA_CONSUMER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DataConsumerBlockEntity consumer) {
        long used = Math.min(consumer.stored, CONSUMPTION_PER_TICK);
        consumer.lastConsumed = used;
        if (used > 0) {
            consumer.stored -= used;
            consumer.totalConsumed += used;
            consumer.setChanged();
        }
    }

    public long getStored() {
        return stored;
    }

    public long getLastConsumed() {
        return lastConsumed;
    }

    public long getTotalConsumed() {
        return totalConsumed;
    }

    public IDataHandler getDataHandler(@Nullable Direction side) {
        return handler;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("stored", stored);
        tag.putLong("total", totalConsumed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = Math.clamp(tag.getLong("stored"), 0L, CAPACITY);
        totalConsumed = Math.max(0, tag.getLong("total"));
    }

    private final class Input implements IDataHandler {

        @Override
        public long getStoredData() {
            return stored;
        }

        @Override
        public long getDataCapacity() {
            return CAPACITY;
        }

        @Override
        public long insertData(long amount, Action action) {
            if (amount <= 0) {
                return amount;
            }
            long accepted = Math.min(amount, CAPACITY - stored);
            if (accepted > 0 && action.execute()) {
                stored += accepted;
                setChanged();
            }
            return amount - accepted;
        }

        @Override
        public long extractData(long amount, Action action) {
            return 0;
        }
    }
}
