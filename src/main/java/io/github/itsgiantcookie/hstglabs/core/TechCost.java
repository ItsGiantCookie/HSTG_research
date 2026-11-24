package io.github.itsgiantcookie.hstglabs.core;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The cost of one technology (docs/design.md §4, §8.1): {@code units} units (U), each taking
 * {@code unitTime} ticks (T) in a speed-1 lab and one full set of {@code ingredients}.
 *
 * <p>{@code I} is whatever the caller uses to name an ingredient; core never looks inside it, it
 * only needs {@code equals}/{@code hashCode}. The tech layer (WP-07) fills it with its resolved
 * ingredient types (item, tag, pack, chemical).
 *
 * @param <I> ingredient key
 */
public record TechCost<I>(int units, int unitTime, List<Ingredient<I>> ingredients) {

    public static final int MIN_UNITS = 1;
    public static final int MAX_UNITS = 1_000_000;
    public static final int MIN_UNIT_TIME = 1;
    public static final int MAX_UNIT_TIME = 72_000;
    public static final int MAX_AMOUNT = 1_000_000;

    public TechCost {
        requireRange("units", units, MIN_UNITS, MAX_UNITS);
        requireRange("unit_time", unitTime, MIN_UNIT_TIME, MAX_UNIT_TIME);
        ingredients = List.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("a technology needs at least one ingredient");
        }
        Set<I> seen = new HashSet<>();
        for (Ingredient<I> ingredient : ingredients) {
            if (!seen.add(ingredient.what())) {
                throw new IllegalArgumentException("ingredient listed twice: " + ingredient.what());
            }
        }
    }

    /** Ticks one unit takes at {@code speed}: T / speed, rounded up, at least 1. */
    public long unitTicks(double speed) {
        if (!(speed > 0.0) || Double.isInfinite(speed)) {
            throw new IllegalArgumentException("speed must be positive and finite: " + speed);
        }
        return Math.max(1L, (long) Math.ceil(unitTime / speed));
    }

    /** Ticks for the whole technology in one speed-1 lab: U × T. */
    public long totalTicks() {
        return (long) units * unitTime;
    }

    /** How many of {@code what} the whole technology consumes (U × amount), 0 if it is not used. */
    public long totalAmount(I what) {
        for (Ingredient<I> ingredient : ingredients) {
            if (ingredient.what().equals(what)) {
                return (long) units * ingredient.amount();
            }
        }
        return 0L;
    }

    /** Same cost with a different U (used when a reload changes the cost, D7). */
    public TechCost<I> withUnits(int newUnits) {
        return new TechCost<>(newUnits, unitTime, ingredients);
    }

    private static void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be " + min + "–" + max + ", was " + value);
        }
    }

    /**
     * One entry of a unit's ingredient set.
     *
     * @param what   ingredient key
     * @param amount how many per unit, 1 – {@link #MAX_AMOUNT}
     * @param <I>    ingredient key
     */
    public record Ingredient<I>(I what, int amount) {

        public Ingredient {
            Objects.requireNonNull(what, "what");
            requireRange("amount", amount, 1, MAX_AMOUNT);
        }
    }
}
