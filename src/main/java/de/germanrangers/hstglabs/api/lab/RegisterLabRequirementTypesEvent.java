package de.germanrangers.hstglabs.api.lab;

import de.germanrangers.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/** Registers {@link LabRequirementType}s (docs/design.md §9.2). Mod bus, common setup, frozen after. */
@ApiStatus.Experimental
public final class RegisterLabRequirementTypesEvent extends RegisterIdEvent<LabRequirementType<?>> {

    @ApiStatus.Internal
    public RegisterLabRequirementTypesEvent(Registrar<LabRequirementType<?>> registrar) {
        super(registrar);
    }
}
