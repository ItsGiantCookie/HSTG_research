package com.hstg.research.cable;

import com.hstg.research.HstgResearch;
import com.hstg.research.block.DataCableBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Per-level registry of data networks (server only). Cables register on load and leave on
 * removal/unload; joining, merging and splitting happen once per tick after block entities ticked,
 * so chunk loading never touches neighbors mid-load.
 */
@EventBusSubscriber(modid = HstgResearch.MODID)
public final class DataNetworkManager {

    private static final Map<Level, DataNetworkManager> MANAGERS = new WeakHashMap<>();

    private final Set<DataCableBlockEntity> pending = new LinkedHashSet<>();
    private final Set<DataNetwork> networks = Collections.newSetFromMap(new IdentityHashMap<>());

    private DataNetworkManager() {
    }

    public static DataNetworkManager get(Level level) {
        return MANAGERS.computeIfAbsent(level, l -> new DataNetworkManager());
    }

    public void add(DataCableBlockEntity cable) {
        pending.add(cable);
    }

    public void remove(DataCableBlockEntity cable) {
        pending.remove(cable);
        DataNetwork network = cable.getNetwork();
        if (network != null) {
            cable.setStoredShare(network.remove(cable));
            if (network.isEmpty()) {
                networks.remove(network);
            }
        }
    }

    private void tick(Level level) {
        if (!pending.isEmpty()) {
            List<DataCableBlockEntity> joining = new ArrayList<>(pending);
            pending.clear();
            for (DataCableBlockEntity cable : joining) {
                if (!cable.isRemoved() && cable.getNetwork() == null) {
                    join(level, cable);
                }
            }
        }
        for (DataNetwork network : new ArrayList<>(networks)) {
            if (network.needsSplitCheck()) {
                split(network);
            }
        }
        for (DataNetwork network : networks) {
            network.tick(level);
        }
    }

    private void join(Level level, DataCableBlockEntity cable) {
        DataNetwork target = null;
        for (Direction dir : Direction.values()) {
            BlockPos pos = cable.getBlockPos().relative(dir);
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof DataCableBlockEntity other
                && !other.isRemoved() && other.getNetwork() != null) {
                DataNetwork found = other.getNetwork();
                if (target == null) {
                    target = found;
                } else if (found != target) {
                    // merge the smaller network into the larger one
                    DataNetwork large = found.size() > target.size() ? found : target;
                    DataNetwork small = large == found ? target : found;
                    large.absorb(small);
                    networks.remove(small);
                    target = large;
                }
            }
        }
        if (target == null) {
            target = new DataNetwork();
            networks.add(target);
        }
        target.add(cable, cable.takeStoredShare());
    }

    /** After a removal, a network may have fallen apart; rebuild its connected components. */
    private void split(DataNetwork network) {
        network.clearSplitCheck();
        Map<BlockPos, DataCableBlockEntity> byPos = new HashMap<>();
        for (DataCableBlockEntity cable : network.cables()) {
            byPos.put(cable.getBlockPos(), cable);
        }
        List<List<DataCableBlockEntity>> components = new ArrayList<>();
        Set<BlockPos> seen = new LinkedHashSet<>();
        for (BlockPos start : byPos.keySet()) {
            if (!seen.add(start)) {
                continue;
            }
            List<DataCableBlockEntity> component = new ArrayList<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.poll();
                component.add(byPos.get(pos));
                for (Direction dir : Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    if (byPos.containsKey(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            components.add(component);
        }
        if (components.size() <= 1) {
            return;
        }
        long buffer = network.getBuffer();
        int total = byPos.size();
        networks.remove(network);
        for (DataCableBlockEntity cable : byPos.values()) {
            cable.setNetwork(null);
        }
        for (List<DataCableBlockEntity> component : components) {
            DataNetwork part = new DataNetwork();
            for (DataCableBlockEntity cable : component) {
                part.add(cable, 0);
            }
            part.setBuffer(buffer * component.size() / total);
            networks.add(part);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (!level.isClientSide) {
            DataNetworkManager manager = MANAGERS.get(level);
            if (manager != null) {
                manager.tick(level);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            MANAGERS.remove(level);
        }
    }
}
