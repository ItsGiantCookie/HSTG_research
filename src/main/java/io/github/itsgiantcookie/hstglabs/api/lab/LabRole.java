package io.github.itsgiantcookie.hstglabs.api.lab;

import org.jetbrains.annotations.ApiStatus;

/**
 * The four lab roles (docs/design.md §5.1). The one fixed set in the lab system: the scheduler is
 * built on it (§9.2). Everything inside a role is open (lab types, stats, tags, requirements).
 */
@ApiStatus.Experimental
public enum LabRole {
    /** Binds a site to an owner, claims work, ticks the site. */
    CONTROLLER,
    /** Holds ingredients and uses one set up per unit. */
    CONSUMER,
    /** Runs units: {@code parallel} slots at a speed. */
    WORKER,
    /** Membership in the network graph. */
    NODE
}
