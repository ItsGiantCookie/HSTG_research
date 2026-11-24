package de.germanrangers.hstglabs.core;

/**
 * An item consumer as the {@link Arbiter} sees it (docs/design.md §5.1, §9.3): it holds ingredients
 * and answers for an ingredient key. Matching (an item, a tag, a pack, a chemical) is the
 * implementation's job; core only counts.
 *
 * @param <I> ingredient key
 */
public interface PackSource<I> {

    /** Stable id within the site (e.g. the block position), saved with consumed sets. */
    String id();

    /** How much matching {@code what} this consumer holds right now. */
    long available(I what);

    /** Removes up to {@code amount} matching {@code what}; returns how much was removed. */
    long take(I what, long amount);

    /** Puts back up to {@code amount} of {@code what}; returns how much was accepted. */
    long giveBack(I what, long amount);
}
