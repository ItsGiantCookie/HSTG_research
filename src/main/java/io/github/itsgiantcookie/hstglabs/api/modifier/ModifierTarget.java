package io.github.itsgiantcookie.hstglabs.api.modifier;

import org.jetbrains.annotations.ApiStatus;

/**
 * Which number a {@link Modifier} changes (docs/design.md §9.2). Fixed set: these are the knobs the
 * scheduler reads; new behaviour comes through new modifiers, not new targets.
 */
@ApiStatus.Experimental
public enum ModifierTarget {
    /** Worker slot speed (divides T). */
    SPEED,
    /** Ingredient amounts per unit. */
    COST,
    /** Energy per tick. */
    ENERGY,
    /** Worker slots. */
    PARALLEL
}
