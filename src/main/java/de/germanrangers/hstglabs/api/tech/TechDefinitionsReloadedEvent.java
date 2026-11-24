package de.germanrangers.hstglabs.api.tech;

import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.ApiStatus;

/** Game bus: fired after every technology or lab type reload (docs/design.md §9.4). Server thread. */
@ApiStatus.Experimental
public final class TechDefinitionsReloadedEvent extends Event {

    private final int technologies;
    private final int labTypes;

    @ApiStatus.Internal
    public TechDefinitionsReloadedEvent(int technologies, int labTypes) {
        this.technologies = technologies;
        this.labTypes = labTypes;
    }

    /** Technologies loaded and enabled. */
    public int technologies() {
        return technologies;
    }

    /** Lab types loaded. */
    public int labTypes() {
        return labTypes;
    }
}
