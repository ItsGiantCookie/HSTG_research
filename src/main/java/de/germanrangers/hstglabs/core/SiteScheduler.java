package de.germanrangers.hstglabs.core;

import de.germanrangers.hstglabs.core.ConsumedSet.Portion;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * One tick of one research site (docs/design.md §5.3, §5.7).
 *
 * <ol>
 *   <li>Running units advance; an unpowered slot pauses. Units finish even if the site no longer
 *       meets the technology's requirements (§5.6).</li>
 *   <li>Every free slot tries to start a unit of the <b>queue head</b> only (D18). It never falls
 *       through to the next entry; if it cannot run the head it idles with the first reason, in this
 *       order: {@code NO_RESEARCH}, {@code BLOCKED}, {@code LAB_TIER} (checked before any other
 *       requirement, §5.9), {@code LAB_REQUIREMENTS}, {@code NO_POWER}, {@code CAP_REACHED},
 *       {@code MISSING_INGREDIENTS}.</li>
 * </ol>
 *
 * <p>Pure function of its arguments plus the mutable slots, progress, consumers and arbiter it is
 * given; the caller (research manager, WP-08) owns all of them.
 */
public final class SiteScheduler {

    private SiteScheduler() {
    }

    /**
     * @param site     the site as lab requirements see it
     * @param queue    the owner's queue; only its head is considered
     * @param specs    technology id to spec, null if no longer defined
     * @param progress technology id to the owner's progress (must exist for every spec)
     * @param slots    the site's worker slots, in a stable order
     * @param arbiter  the site's arbiter
     * @param sources  the site's active consumers
     */
    public static <K, I> SiteTick<K, I> tick(SiteModel site, ResearchQueue<K> queue, Function<K, TechSpec<I>> specs,
          Function<K, ResearchProgress> progress, List<WorkerSlot<K, I>> slots, Arbiter<I> arbiter,
          List<? extends PackSource<I>> sources) {
        List<SlotStatus> statuses = new ArrayList<>(slots.size());
        List<K> credited = new ArrayList<>();
        List<Integer> creditedSlots = new ArrayList<>();
        List<Portion<I>> spilled = new ArrayList<>();

        for (int i = 0; i < slots.size(); i++) {
            WorkerSlot<K, I> slot = slots.get(i);
            LabCycle<K, I> cycle = slot.cycle();
            if (!cycle.isRunning()) {
                statuses.add(null);
                continue;
            }
            if (!slot.powered()) {
                statuses.add(new SlotStatus(SlotState.PAUSED, IdleReason.NO_POWER));
                continue;
            }
            K tech = cycle.tech().orElseThrow();
            LabCycle.Tick<I> tick = cycle.tick(progress.apply(tech), arbiter, sources);
            spilled.addAll(tick.spilled());
            if (tick.status() == LabCycle.TickStatus.CREDITED) {
                credited.add(tech);
                creditedSlots.add(i);
            }
            statuses.add(cycle.isRunning() ? new SlotStatus(SlotState.RUNNING, null) : null);
        }

        Optional<K> head = queue.head();
        for (int i = 0; i < slots.size(); i++) {
            if (statuses.get(i) == null) {
                statuses.set(i, startOrIdle(site, head, specs, progress, slots.get(i), arbiter, sources, spilled));
            }
        }
        return new SiteTick<>(statuses, credited, creditedSlots, spilled);
    }

    private static <K, I> SlotStatus startOrIdle(SiteModel site, Optional<K> head, Function<K, TechSpec<I>> specs,
          Function<K, ResearchProgress> progress, WorkerSlot<K, I> slot, Arbiter<I> arbiter,
          List<? extends PackSource<I>> sources, List<Portion<I>> spilled) {
        TechSpec<I> spec = head.map(specs).orElse(null);
        if (spec == null) {
            return idle(IdleReason.NO_RESEARCH);
        }
        if (spec.blocked()) {
            return idle(IdleReason.BLOCKED);
        }
        int effectiveTier = site.effectiveTier(slot.tier());
        if (effectiveTier < spec.minTier()) {
            return idle(IdleReason.labTier(spec.minTier()));
        }
        Optional<LabRequirement> missing = LabRequirement.firstMissing(spec.requires(),
              new LabRequirement.Context(site, effectiveTier));
        if (missing.isPresent()) {
            return idle(IdleReason.labRequirements(missing.get()));
        }
        if (!slot.powered()) {
            return idle(IdleReason.NO_POWER);
        }
        LabCycle.Start<I> start = slot.cycle().start(head.get(), spec.cost(), slot.speed(), progress.apply(head.get()),
              arbiter, sources);
        spilled.addAll(start.spilled());
        return start.idle().map(SiteScheduler::idle).orElse(new SlotStatus(SlotState.STARTED, null));
    }

    private static SlotStatus idle(IdleReason reason) {
        return new SlotStatus(SlotState.IDLE, reason);
    }

    public enum SlotState {
        /** Continues a unit. */
        RUNNING,
        /** Started a new unit this tick. */
        STARTED,
        /** Has a unit but no power; the unit waits. */
        PAUSED,
        /** No unit; {@code reason} says why. */
        IDLE
    }

    /** One slot after the tick; {@code reason} is set for {@code PAUSED} and {@code IDLE}. */
    public record SlotStatus(SlotState state, IdleReason reason) {
    }

    /**
     * Result of one site tick.
     *
     * @param slots         per slot, in slot order
     * @param credited      technology of every unit credited this tick
     * @param creditedSlots index of the slot each credited unit ran in, parallel to {@code credited}
     * @param spilled       ingredients a refund could not give back; the caller drops them
     */
    public record SiteTick<K, I>(List<SlotStatus> slots, List<K> credited, List<Integer> creditedSlots,
          List<Portion<I>> spilled) {

        public SiteTick {
            slots = List.copyOf(slots);
            credited = List.copyOf(credited);
            creditedSlots = List.copyOf(creditedSlots);
            spilled = List.copyOf(spilled);
        }

        /** Slots idle or paused for the reason with this id (for "worker counts by idle reason", §6.3). */
        public long count(String reasonId) {
            return slots.stream().filter(s -> s.reason() != null && s.reason().id().equals(reasonId)).count();
        }
    }
}
