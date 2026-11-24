package io.github.itsgiantcookie.hstglabs.api.owner;

import io.github.itsgiantcookie.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/** Registers {@link OwnerType}s (docs/design.md §9.2). Mod bus, common setup, frozen after. */
@ApiStatus.Experimental
public final class RegisterOwnerTypesEvent extends RegisterIdEvent<OwnerType> {

    @ApiStatus.Internal
    public RegisterOwnerTypesEvent(Registrar<OwnerType> registrar) {
        super(registrar);
    }
}
