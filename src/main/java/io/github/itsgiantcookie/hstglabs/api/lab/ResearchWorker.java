package io.github.itsgiantcookie.hstglabs.api.lab;

import org.jetbrains.annotations.ApiStatus;

/**
 * The {@link LabRole#WORKER} role (docs/design.md §5.1, §5.3, §9.3), exposed as
 * {@link ResearchCapabilities#WORKER}: basic lab, Research Server, multiblock, another mod's machine.
 * Holds no items; its controller drives its slots.
 */
@ApiStatus.Experimental
public interface ResearchWorker {

    /** Current stats; {@code parallel} is the number of slots. */
    LabStats stats();

    /** Slots running a unit right now. */
    int busySlots();

    /** Whether it has the energy it needs this tick (an unpowered worker is not active, D19). */
    boolean powered();
}
