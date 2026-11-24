package io.github.itsgiantcookie.hstglabs.api.lab;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * The {@link LabRole#NODE} role (docs/design.md §5.3, §9.3), exposed as
 * {@link ResearchCapabilities#NODE}: membership in a research network. Cores, consumers, workers and
 * cable carry it; a basic lab does not (D13).
 */
@ApiStatus.Experimental
public interface GridNode {

    /** Whether the network may connect through {@code side}. */
    boolean connects(Direction side);

    /** The lab type of this block. */
    ResourceLocation labType();
}
