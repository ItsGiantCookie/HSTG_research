package de.germanrangers.hstglabs.grid;

import mekanism.api.tier.BaseTier;
import net.minecraft.util.StringRepresentable;

/**
 * Data cable tiers, named and colored after Mekanism's base tiers. Capacity is per cable:
 * a network buffers (and moves per tick) the sum of its cables' capacities, like Mekanism's
 * universal cables.
 */
public enum CableTier implements StringRepresentable {
    BASIC(BaseTier.BASIC, 50),
    ADVANCED(BaseTier.ADVANCED, 200),
    ELITE(BaseTier.ELITE, 800),
    ULTIMATE(BaseTier.ULTIMATE, 3_200);

    private final BaseTier baseTier;
    private final long capacity;

    CableTier(BaseTier baseTier, long capacity) {
        this.baseTier = baseTier;
        this.capacity = capacity;
    }

    public BaseTier getBaseTier() {
        return baseTier;
    }

    /** Data per tick this cable adds to its network's buffer. */
    public long getCapacity() {
        return capacity;
    }

    @Override
    public String getSerializedName() {
        return baseTier.getLowerName();
    }
}
