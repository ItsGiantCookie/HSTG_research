package io.github.itsgiantcookie.hstglabs.core;

import io.github.itsgiantcookie.hstglabs.core.ConsumedSet.Portion;
import io.github.itsgiantcookie.hstglabs.core.TechCost.Ingredient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Takes one ingredient set for a unit from a site's consumers (docs/design.md §5.3):
 *
 * <ul>
 *   <li>simulate first, then commit all or nothing;</li>
 *   <li>one set may be gathered from several consumers (D20);</li>
 *   <li>round-robin per ingredient: each set starts at the consumer after the one the last set of
 *       that ingredient started at, so one consumer is not emptied while others stay full.</li>
 * </ul>
 *
 * <p>One arbiter per site; it remembers only the round-robin cursors. Not thread-safe.
 *
 * @param <I> ingredient key
 */
public final class Arbiter<I> {

    private final Map<I, String> lastStart = new HashMap<>();

    /** Takes one full set or nothing. */
    public Taken<I> takeSet(List<Ingredient<I>> set, List<? extends PackSource<I>> sources) {
        Map<String, PackSource<I>> byId = index(sources);
        int n = sources.size();
        List<Portion<I>> plan = new ArrayList<>();
        Map<I, String> starts = new HashMap<>();

        for (Ingredient<I> line : set) {
            long need = line.amount();
            int start = startIndex(line.what(), sources);
            for (int k = 0; k < n && need > 0; k++) {
                PackSource<I> source = sources.get((start + k) % n);
                long portion = Math.min(need, source.available(line.what()));
                if (portion <= 0) {
                    continue;
                }
                plan.add(new Portion<>(source.id(), line.what(), portion));
                starts.putIfAbsent(line.what(), source.id());
                need -= portion;
            }
            if (need > 0) {
                return Taken.missing(line.what(), List.of());
            }
        }

        List<Portion<I>> taken = new ArrayList<>();
        for (Portion<I> planned : plan) {
            long got = byId.get(planned.consumer()).take(planned.what(), planned.amount());
            if (got > 0) {
                taken.add(new Portion<>(planned.consumer(), planned.what(), got));
            }
            if (got < planned.amount()) {
                // two lines matched the same stock (an item and a tag holding it): undo
                return Taken.missing(planned.what(), giveBack(taken, byId));
            }
        }
        lastStart.putAll(starts);
        return Taken.of(new ConsumedSet<>(taken));
    }

    /** Gives a consumed set back to the consumers it came from; returns what did not fit or has no home. */
    public List<Portion<I>> refund(ConsumedSet<I> set, List<? extends PackSource<I>> sources) {
        return giveBack(set.portions(), index(sources));
    }

    private List<Portion<I>> giveBack(List<Portion<I>> portions, Map<String, PackSource<I>> byId) {
        List<Portion<I>> spilled = new ArrayList<>();
        for (Portion<I> portion : portions) {
            PackSource<I> home = byId.get(portion.consumer());
            long accepted = home == null ? 0 : home.giveBack(portion.what(), portion.amount());
            if (accepted < portion.amount()) {
                spilled.add(new Portion<>(portion.consumer(), portion.what(), portion.amount() - accepted));
            }
        }
        return spilled;
    }

    private int startIndex(I what, List<? extends PackSource<I>> sources) {
        String last = lastStart.get(what);
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).id().equals(last)) {
                return (i + 1) % sources.size();
            }
        }
        return 0;
    }

    private static <I> Map<String, PackSource<I>> index(List<? extends PackSource<I>> sources) {
        Map<String, PackSource<I>> byId = new LinkedHashMap<>();
        for (PackSource<I> source : sources) {
            if (byId.put(source.id(), source) != null) {
                throw new IllegalArgumentException("two consumers share the id " + source.id());
            }
        }
        return byId;
    }

    /**
     * Outcome of {@link #takeSet}: the set, or the first ingredient that could not be gathered.
     * {@code spilled} is only non-empty if undoing a failed commit could not give everything back;
     * the caller drops it so nothing is lost.
     */
    public record Taken<I>(Optional<ConsumedSet<I>> set, Optional<I> missing, List<Portion<I>> spilled) {

        public Taken {
            spilled = List.copyOf(spilled);
        }

        static <I> Taken<I> of(ConsumedSet<I> set) {
            return new Taken<>(Optional.of(set), Optional.empty(), List.of());
        }

        static <I> Taken<I> missing(I what, List<Portion<I>> spilled) {
            return new Taken<>(Optional.empty(), Optional.of(what), spilled);
        }

        public boolean success() {
            return set.isPresent();
        }
    }
}
