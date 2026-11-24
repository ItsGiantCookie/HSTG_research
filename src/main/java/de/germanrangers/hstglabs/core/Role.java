package de.germanrangers.hstglabs.core;

/**
 * The four lab roles (docs/design.md §5.1). This is the one fixed set in the lab system, because the
 * scheduler is built on it (§9.2); everything inside a role is open.
 */
public enum Role {
    CONTROLLER,
    CONSUMER,
    WORKER,
    NODE
}
