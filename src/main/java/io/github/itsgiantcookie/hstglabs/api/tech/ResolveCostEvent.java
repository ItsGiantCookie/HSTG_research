package io.github.itsgiantcookie.hstglabs.api.tech;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.ApiStatus;

/**
 * Game bus, mutable: fired per technology when the technology index is rebuilt, so a listener can
 * scale its cost (difficulty, player count). docs/design.md §9.4. Server thread.
 */
@ApiStatus.Experimental
public final class ResolveCostEvent extends Event {

    private final ResourceLocation technology;
    private int units;
    private int unitTime;

    @ApiStatus.Internal
    public ResolveCostEvent(ResourceLocation technology, int units, int unitTime) {
        this.technology = technology;
        this.units = units;
        this.unitTime = unitTime;
    }

    public ResourceLocation technology() {
        return technology;
    }

    /** U, 1 – 1,000,000. */
    public int units() {
        return units;
    }

    public void setUnits(int units) {
        if (units < 1 || units > 1_000_000) {
            throw new IllegalArgumentException("units must be 1–1,000,000, was " + units);
        }
        this.units = units;
    }

    /** T in ticks at speed 1, 1 – 72,000. */
    public int unitTime() {
        return unitTime;
    }

    public void setUnitTime(int unitTime) {
        if (unitTime < 1 || unitTime > 72_000) {
            throw new IllegalArgumentException("unit_time must be 1–72,000, was " + unitTime);
        }
        this.unitTime = unitTime;
    }
}
