package com.hstg.research.cable;

import com.hstg.research.api.IDataHandler;
import com.hstg.research.block.DataCableBlock;
import com.hstg.research.block.DataCableBlockEntity;
import com.hstg.research.capability.ModCapabilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import mekanism.api.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A connected group of data cables sharing one buffer (Mekanism-style transmitter network).
 * Capacity is the sum of the cables' tier capacities. Every tick the whole buffer is offered,
 * split evenly, to all non-cable acceptors the cables connect to.
 */
public final class DataNetwork {

    private final Set<DataCableBlockEntity> cables = new LinkedHashSet<>();
    private final List<Target> targets = new ArrayList<>();
    private long buffer;
    private long capacity;
    private boolean acceptorsDirty = true;
    private boolean needsSplitCheck;

    record Target(BlockPos pos, Direction side) {
    }

    Set<DataCableBlockEntity> cables() {
        return Collections.unmodifiableSet(cables);
    }

    public long getBuffer() {
        return buffer;
    }

    public long getCapacity() {
        return capacity;
    }

    int size() {
        return cables.size();
    }

    boolean isEmpty() {
        return cables.isEmpty();
    }

    void add(DataCableBlockEntity cable, long share) {
        if (cables.add(cable)) {
            cable.setNetwork(this);
            capacity += cable.getTier().getCapacity();
            buffer = Math.min(capacity, buffer + share);
            acceptorsDirty = true;
        }
    }

    /** Removes a cable and takes its share of the buffer with it (saved on that cable). */
    long remove(DataCableBlockEntity cable) {
        long share = shareOf(cable);
        if (cables.remove(cable)) {
            cable.setNetwork(null);
            capacity -= cable.getTier().getCapacity();
            buffer = Math.max(0, Math.min(capacity, buffer - share));
            acceptorsDirty = true;
            needsSplitCheck = true;
        }
        return share;
    }

    /** Moves all cables and the buffer of {@code other} into this network. */
    void absorb(DataNetwork other) {
        long otherBuffer = other.buffer;
        for (DataCableBlockEntity cable : other.cables) {
            cables.add(cable);
            cable.setNetwork(this);
            capacity += cable.getTier().getCapacity();
        }
        other.cables.clear();
        other.buffer = 0;
        other.capacity = 0;
        buffer = Math.min(capacity, buffer + otherBuffer);
        acceptorsDirty = true;
    }

    /** Even share of the buffer, saved per cable like Mekanism does. */
    public long shareOf(DataCableBlockEntity cable) {
        if (!cables.contains(cable) || cables.isEmpty()) {
            return 0;
        }
        return buffer / cables.size();
    }

    void setBuffer(long buffer) {
        this.buffer = Math.max(0, Math.min(capacity, buffer));
    }

    boolean needsSplitCheck() {
        return needsSplitCheck;
    }

    void clearSplitCheck() {
        needsSplitCheck = false;
    }

    public void markAcceptorsDirty() {
        acceptorsDirty = true;
    }

    public long insert(long amount, Action action) {
        if (amount <= 0) {
            return amount;
        }
        long accepted = Math.min(amount, capacity - buffer);
        if (accepted > 0 && action.execute()) {
            buffer += accepted;
        }
        return amount - accepted;
    }

    void tick(Level level) {
        if (acceptorsDirty) {
            rebuildTargets(level);
        }
        if (buffer <= 0 || targets.isEmpty()) {
            return;
        }
        List<IDataHandler> acceptors = new ArrayList<>(targets.size());
        for (Target target : targets) {
            if (!level.isLoaded(target.pos())) {
                continue;
            }
            IDataHandler handler = level.getCapability(ModCapabilities.DATA, target.pos(), target.side());
            if (handler != null && handler.insertData(buffer, Action.SIMULATE) < buffer) {
                acceptors.add(handler);
            }
        }
        // even split; whatever one acceptor refuses is offered to the next ones
        for (int i = 0; i < acceptors.size() && buffer > 0; i++) {
            long share = Math.ceilDiv(buffer, acceptors.size() - i);
            long accepted = share - acceptors.get(i).insertData(share, Action.EXECUTE);
            buffer -= Math.max(0, accepted);
        }
    }

    private void rebuildTargets(Level level) {
        targets.clear();
        for (DataCableBlockEntity cable : cables) {
            BlockState state = cable.getBlockState();
            if (!(state.getBlock() instanceof DataCableBlock)) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                if (!state.getValue(DataCableBlock.CONNECTIONS.get(dir))) {
                    continue;
                }
                BlockPos pos = cable.getBlockPos().relative(dir);
                if (level.isLoaded(pos) && !(level.getBlockEntity(pos) instanceof DataCableBlockEntity)) {
                    targets.add(new Target(pos, dir.getOpposite()));
                }
            }
        }
        acceptorsDirty = false;
    }
}
