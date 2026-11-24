package de.germanrangers.hstglabs.api.lab;

import de.germanrangers.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers {@link IdleReasonType}s, e.g. a modded worker reporting "no mana" (docs/design.md §9.2).
 * Mod bus, common setup, frozen after.
 */
@ApiStatus.Experimental
public final class RegisterIdleReasonsEvent extends RegisterIdEvent<IdleReasonType> {

    @ApiStatus.Internal
    public RegisterIdleReasonsEvent(Registrar<IdleReasonType> registrar) {
        super(registrar);
    }
}
