package de.germanrangers.hstglabs.api.client;

import de.germanrangers.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers a {@link RequirementEditor} per lab requirement type id (docs/design.md §9.2). Mod bus,
 * client setup, frozen after.
 */
@ApiStatus.Experimental
public final class RegisterRequirementEditorsEvent extends RegisterIdEvent<RequirementEditor<?>> {

    @ApiStatus.Internal
    public RegisterRequirementEditorsEvent(Registrar<RequirementEditor<?>> registrar) {
        super(registrar);
    }
}
