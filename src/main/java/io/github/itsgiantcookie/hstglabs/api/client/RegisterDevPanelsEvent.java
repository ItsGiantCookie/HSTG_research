package io.github.itsgiantcookie.hstglabs.api.client;

import io.github.itsgiantcookie.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/** Registers {@link DevPanel}s (docs/design.md §9.2). Mod bus, client setup, frozen after. */
@ApiStatus.Experimental
public final class RegisterDevPanelsEvent extends RegisterIdEvent<DevPanel> {

    @ApiStatus.Internal
    public RegisterDevPanelsEvent(Registrar<DevPanel> registrar) {
        super(registrar);
    }
}
