package de.germanrangers.hstglabs.core;

import java.util.List;
import java.util.Objects;

/**
 * The ingredient set a running unit consumed, by consumer, so a cancelled unit can give it back to
 * where it came from (docs/design.md §4.2, §6.2).
 *
 * @param <I> ingredient key
 */
public record ConsumedSet<I>(List<Portion<I>> portions) {

    public ConsumedSet {
        portions = List.copyOf(portions);
    }

    /** How much of {@code what} the set holds in total. */
    public long total(I what) {
        return portions.stream().filter(p -> p.what().equals(what)).mapToLong(Portion::amount).sum();
    }

    /** Part of a set taken from one consumer. */
    public record Portion<I>(String consumer, I what, long amount) {

        public Portion {
            Objects.requireNonNull(consumer, "consumer");
            Objects.requireNonNull(what, "what");
            if (amount < 1) {
                throw new IllegalArgumentException("amount must be positive, was " + amount);
            }
        }
    }
}
