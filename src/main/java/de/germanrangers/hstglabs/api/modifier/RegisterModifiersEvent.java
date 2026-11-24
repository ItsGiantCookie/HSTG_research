package de.germanrangers.hstglabs.api.modifier;

import de.germanrangers.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/** Registers {@link Modifier}s (docs/design.md §9.2). Mod bus, common setup, frozen after. */
@ApiStatus.Experimental
public final class RegisterModifiersEvent extends RegisterIdEvent<Modifier> {

    @ApiStatus.Internal
    public RegisterModifiersEvent(Registrar<Modifier> registrar) {
        super(registrar);
    }
}
