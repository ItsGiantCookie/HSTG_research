package io.github.itsgiantcookie.hstglabs.api.client;

import io.github.itsgiantcookie.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers an {@link IngredientRenderer} per ingredient type id (docs/design.md §9.2). Mod bus,
 * client setup, frozen after.
 */
@ApiStatus.Experimental
public final class RegisterIngredientRenderersEvent extends RegisterIdEvent<IngredientRenderer<?>> {

    @ApiStatus.Internal
    public RegisterIngredientRenderersEvent(Registrar<IngredientRenderer<?>> registrar) {
        super(registrar);
    }
}
