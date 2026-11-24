package de.germanrangers.hstglabs.api.ingredient;

import de.germanrangers.hstglabs.api.registry.RegisterIdEvent;
import org.jetbrains.annotations.ApiStatus;

/** Registers {@link IngredientType}s (docs/design.md §9.2). Mod bus, common setup, frozen after. */
@ApiStatus.Experimental
public final class RegisterIngredientTypesEvent extends RegisterIdEvent<IngredientType<?, ?>> {

    @ApiStatus.Internal
    public RegisterIngredientTypesEvent(Registrar<IngredientType<?, ?>> registrar) {
        super(registrar);
    }
}
