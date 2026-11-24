package de.germanrangers.hstglabs.core;

import java.util.List;
import java.util.Objects;

/**
 * What the scheduler needs to know about one technology (docs/design.md §5.6, §5.9, §8.1).
 *
 * @param cost     U, T and the ingredient set
 * @param minTier  {@code labs.min_tier}, checked before any other requirement
 * @param requires {@code labs.requires}; all must hold, empty = any site
 * @param blocked  HS refused the unlock (§4.1); labs idle with {@code BLOCKED}
 * @param <I>      ingredient key
 */
public record TechSpec<I>(TechCost<I> cost, int minTier, List<LabRequirement> requires, boolean blocked) {

    public TechSpec {
        Objects.requireNonNull(cost, "cost");
        if (minTier < 1) {
            throw new IllegalArgumentException("min tier must be at least 1, was " + minTier);
        }
        requires = List.copyOf(requires);
    }
}
