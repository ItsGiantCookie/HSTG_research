package io.github.itsgiantcookie.hstglabs.api.modifier;

import io.github.itsgiantcookie.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/** Registers {@link Modifier}s (docs/design.md §9.2). Mod bus, common setup, frozen after. */
@ApiStatus.Experimental
public final class RegisterModifiersEvent extends RegisterIdEvent<Modifier> {

    @ApiStatus.Internal
    public RegisterModifiersEvent(Registrar<Modifier> registrar) {
        super(registrar);
    }
}
