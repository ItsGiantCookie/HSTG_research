package io.github.itsgiantcookie.hstglabs.api.template;

import io.github.itsgiantcookie.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/** Registers {@link TemplateKind}s (docs/design.md §9.2). Mod bus, common setup, frozen after. */
@ApiStatus.Experimental
public final class RegisterTemplateKindsEvent extends RegisterIdEvent<TemplateKind<?>> {

    @ApiStatus.Internal
    public RegisterTemplateKindsEvent(Registrar<TemplateKind<?>> registrar) {
        super(registrar);
    }
}
