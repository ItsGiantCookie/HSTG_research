package io.github.itsgiantcookie.hstglabs.core;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Plain view of one research site (docs/design.md §5): a basic lab or a network, its controller's
 * tier and its members. The grid and lab layers build it; core only reads it.
 *
 * @param network        true for a distributed network, false for a basic lab or other single block
 * @param controllerTier tier of the site's controller (a basic lab is its own controller)
 * @param members        every member, active or not
 */
public record SiteModel(boolean network, int controllerTier, List<Member> members) {

    public SiteModel {
        if (controllerTier < 1) {
            throw new IllegalArgumentException("controller tier must be at least 1, was " + controllerTier);
        }
        members = List.copyOf(members);
    }

    /** A basic lab: one active block that is controller, consumer and worker at once (§5.2). */
    public static SiteModel basicLab(String labType, Set<String> tags, int tier) {
        var self = new Member(labType, tags, EnumSet.of(Role.CONTROLLER, Role.CONSUMER, Role.WORKER), true);
        return new SiteModel(false, tier, List.of(self));
    }

    /** Effective tier of a worker slot: min(worker, controller) (§5.9, D29). */
    public int effectiveTier(int workerTier) {
        if (workerTier < 1) {
            throw new IllegalArgumentException("worker tier must be at least 1, was " + workerTier);
        }
        return Math.min(workerTier, controllerTier);
    }

    /** Active members matching {@code filter} (D19: inactive members count for nothing). */
    public long countActive(Predicate<Member> filter) {
        return members.stream().filter(Member::active).filter(filter).count();
    }
}
