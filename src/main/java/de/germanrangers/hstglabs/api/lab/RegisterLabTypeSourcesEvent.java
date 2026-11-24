package de.germanrangers.hstglabs.api.lab;

import de.germanrangers.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/** Registers {@link LabTypeSource}s (docs/design.md §9.2). Mod bus, common setup, frozen after. */
@ApiStatus.Experimental
public final class RegisterLabTypeSourcesEvent extends RegisterIdEvent<LabTypeSource> {

    @ApiStatus.Internal
    public RegisterLabTypeSourcesEvent(Registrar<LabTypeSource> registrar) {
        super(registrar);
    }
}
