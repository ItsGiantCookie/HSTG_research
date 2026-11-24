package io.github.itsgiantcookie.hstglabs.api.tech;

import io.github.itsgiantcookie.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/** Registers {@link TechSource}s (docs/design.md §9.2). Mod bus, common setup, frozen after. */
@ApiStatus.Experimental
public final class RegisterTechSourcesEvent extends RegisterIdEvent<TechSource> {

    @ApiStatus.Internal
    public RegisterTechSourcesEvent(Registrar<TechSource> registrar) {
        super(registrar);
    }
}
