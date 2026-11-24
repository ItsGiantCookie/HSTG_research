package de.germanrangers.hstglabs.api.lab;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * Stats of one lab member (docs/design.md §5.4, §5.5), from its lab type or computed by a
 * {@link LabStatsProvider}.
 *
 * @param speed         unit speed multiplier, &gt; 0
 * @param parallel      worker slots, &ge; 0 (0 for a pure consumer or node)
 * @param tier          lab tier, &ge; 1 (§5.9)
 * @param labTags       lab tags carried
 * @param energyPerTick Joules per running slot per tick, &ge; 0 (D6, D23)
 */
@ApiStatus.Experimental
public record LabStats(double speed, int parallel, int tier, Set<ResourceLocation> labTags, long energyPerTick) {

    public LabStats {
        if (!(speed > 0.0) || Double.isInfinite(speed)) {
            throw new IllegalArgumentException("speed must be positive and finite: " + speed);
        }
        if (parallel < 0) {
            throw new IllegalArgumentException("parallel must not be negative: " + parallel);
        }
        if (tier < 1) {
            throw new IllegalArgumentException("tier must be at least 1: " + tier);
        }
        if (energyPerTick < 0) {
            throw new IllegalArgumentException("energy_per_tick must not be negative: " + energyPerTick);
        }
        labTags = Set.copyOf(labTags);
    }
}
