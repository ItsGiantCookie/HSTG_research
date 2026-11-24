package de.germanrangers.hstglabs.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Entry point and version of the HSTG Research Labs API (docs/design.md §9.1).
 *
 * <p>{@code de.germanrangers.hstglabs.api} and {@code api.client} are public; everything else in the
 * mod is internal and may change without notice. A breaking change waits for a new
 * {@link #API_VERSION}; additions raise {@link #API_MINOR}. Until 1.0 every type is
 * {@link ApiStatus.Experimental}. Server API on the server thread, client API on the client thread.
 */
@ApiStatus.Experimental
public final class HSLabsAPI {

    /** Mod id, also the namespace of every built-in id. */
    public static final String MOD_ID = "hstg_labs";
    /** API generation; bumped only on a breaking change. */
    public static final int API_VERSION = 1;
    /** Additions within {@link #API_VERSION}. */
    public static final int API_MINOR = 0;

    private HSLabsAPI() {
    }
}
