package de.germanrangers.hstglabs.api.client;

import de.germanrangers.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/** Registers {@link TechNodeDecorator}s (docs/design.md §9.2). Mod bus, client setup, frozen after. */
@ApiStatus.Experimental
public final class RegisterTechNodeDecoratorsEvent extends RegisterIdEvent<TechNodeDecorator> {

    @ApiStatus.Internal
    public RegisterTechNodeDecoratorsEvent(Registrar<TechNodeDecorator> registrar) {
        super(registrar);
    }
}
