package de.germanrangers.hstglabs.api.modifier;

import org.jetbrains.annotations.ApiStatus;

/** Where a {@link Modifier} applies (docs/design.md §9.2). Fixed set, like {@link ModifierTarget}. */
@ApiStatus.Experimental
public enum ModifierScope {
    /** One worker (an upgrade card). */
    WORKER,
    /** A whole site (an accelerator node). */
    SITE,
    /** Everything an owner runs (a research reward). */
    OWNER,
    /** One technology. */
    TECHNOLOGY
}
