package de.germanrangers.hstglabs.core;

import de.germanrangers.hstglabs.core.ConsumedSet.Portion;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One worker slot's unit (docs/design.md §4.2): claim a unit, consume one set at the start, count
 * down T / speed ticks, then credit it, or cancel and give the set back.
 *
 * <p>Progress is passed in per call and keyed by the technology the unit was claimed for, so a
 * unit is credited to that technology even if the queue head changed meanwhile. Not thread-safe.
 *
 * @param <K> technology id
 * @param <I> ingredient key
 */
public final class LabCycle<K, I> {

    private K tech;
    private long ticksLeft;
    private ConsumedSet<I> consumed;

    /** Rebuilds a unit saved mid-run (§6.2); the progress's inFlight already counts it. */
    public static <K, I> LabCycle<K, I> restore(K tech, long ticksLeft, ConsumedSet<I> consumed) {
        if (ticksLeft < 1) {
            throw new IllegalArgumentException("a saved unit has at least one tick left, was " + ticksLeft);
        }
        var cycle = new LabCycle<K, I>();
        cycle.tech = Objects.requireNonNull(tech, "tech");
        cycle.ticksLeft = ticksLeft;
        cycle.consumed = Objects.requireNonNull(consumed, "consumed");
        return cycle;
    }

    /**
     * Tries to start a unit of {@code tech}: needs a claimable unit ({@code CAP_REACHED} otherwise)
     * and one full set ({@code MISSING_INGREDIENTS} otherwise). Nothing changes unless it starts.
     */
    public Start<I> start(K tech, TechCost<I> cost, double speed, ResearchProgress progress, Arbiter<I> arbiter,
          List<? extends PackSource<I>> sources) {
        if (isRunning()) {
            throw new IllegalStateException("slot already runs a unit of " + this.tech);
        }
        long ticks = cost.unitTicks(speed);
        if (progress.claimable() == 0) {
            return new Start<>(Optional.of(IdleReason.CAP_REACHED), List.of());
        }
        Arbiter.Taken<I> taken = arbiter.takeSet(cost.ingredients(), sources);
        if (!taken.success()) {
            return new Start<>(Optional.of(IdleReason.missingIngredients(taken.missing().orElseThrow())), taken.spilled());
        }
        progress.tryClaim();
        this.tech = Objects.requireNonNull(tech, "tech");
        this.ticksLeft = ticks;
        this.consumed = taken.set().orElseThrow();
        return new Start<>(Optional.empty(), List.of());
    }

    /**
     * Advances the unit by one tick. On the last tick it is credited; if the cost was lowered below
     * what is done while it ran (D7) it is not, and its set goes back to the consumers instead.
     */
    public Tick<I> tick(ResearchProgress progress, Arbiter<I> arbiter, List<? extends PackSource<I>> sources) {
        requireRunning();
        if (--ticksLeft > 0) {
            return new Tick<>(TickStatus.RUNNING, List.of());
        }
        ConsumedSet<I> set = consumed;
        clear();
        if (progress.finishUnit()) {
            return new Tick<>(TickStatus.CREDITED, List.of());
        }
        return new Tick<>(TickStatus.FINISHED_UNCREDITED, arbiter.refund(set, sources));
    }

    /** Abandons the unit (worker removed, admin release): nothing is credited, the set goes back. */
    public List<Portion<I>> cancel(ResearchProgress progress, Arbiter<I> arbiter, List<? extends PackSource<I>> sources) {
        requireRunning();
        ConsumedSet<I> set = consumed;
        clear();
        progress.cancelUnit();
        return arbiter.refund(set, sources);
    }

    public boolean isRunning() {
        return tech != null;
    }

    public Optional<K> tech() {
        return Optional.ofNullable(tech);
    }

    public long ticksLeft() {
        return ticksLeft;
    }

    public Optional<ConsumedSet<I>> consumed() {
        return Optional.ofNullable(consumed);
    }

    private void requireRunning() {
        if (!isRunning()) {
            throw new IllegalStateException("no unit running");
        }
    }

    private void clear() {
        tech = null;
        ticksLeft = 0;
        consumed = null;
    }

    /** Result of {@link #start}: empty {@code idle} means it started; {@code spilled} must be dropped. */
    public record Start<I>(Optional<IdleReason> idle, List<Portion<I>> spilled) {

        public boolean started() {
            return idle.isEmpty();
        }
    }

    public enum TickStatus {
        RUNNING,
        CREDITED,
        FINISHED_UNCREDITED
    }

    /** Result of {@link #tick}; {@code spilled} is what a refund could not give back, to be dropped. */
    public record Tick<I>(TickStatus status, List<Portion<I>> spilled) {
    }
}
