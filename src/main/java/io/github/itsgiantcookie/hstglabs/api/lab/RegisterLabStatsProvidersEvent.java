package io.github.itsgiantcookie.hstglabs.api.lab;

import io.github.itsgiantcookie.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers a {@link LabStatsProvider.Factory} per block entity type; the id is the block entity
 * type's registry id (docs/design.md §9.2). Mod bus, common setup, frozen after.
 */
@ApiStatus.Experimental
public final class RegisterLabStatsProvidersEvent extends RegisterIdEvent<LabStatsProvider.Factory> {

    @ApiStatus.Internal
    public RegisterLabStatsProvidersEvent(Registrar<LabStatsProvider.Factory> registrar) {
        super(registrar);
    }
}
