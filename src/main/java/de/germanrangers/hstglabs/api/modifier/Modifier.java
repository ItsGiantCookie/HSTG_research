package de.germanrangers.hstglabs.api.modifier;

import org.jetbrains.annotations.ApiStatus;

/**
 * Changes speed, cost, energy or parallel slots (docs/design.md §5.8, §9.2), e.g. a "+10 % research
 * speed" reward or an accelerator node; Mekanism upgrade cards are applied the same way. Registered
 * through {@link RegisterModifiersEvent}; modifiers of one target apply in registration order.
 */
@ApiStatus.Experimental
public interface Modifier {

    ModifierTarget target();

    ModifierScope scope();

    /** Returns the new value; return {@code value} unchanged when this modifier does not apply. Server thread. */
    double modify(double value, ModifierContext context);
}
