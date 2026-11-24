package de.germanrangers.hstglabs.api.lab;

import de.germanrangers.hstglabs.api.owner.OwnerId;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * The {@link LabRole#CONTROLLER} role (docs/design.md §5.1, §9.3), exposed as
 * {@link ResearchCapabilities#CONTROLLER}: basic lab, Research Core, multiblock controller.
 */
@ApiStatus.Experimental
public interface ResearchController {

    OwnerId owner();

    /** The site this controller runs. */
    ResearchSite site();

    /** Units running right now across the site's worker slots. */
    int runningUnits();

    /** Idle worker slots by idle reason id (see {@link IdleReasonType}). */
    Map<ResourceLocation, Integer> idleSlots();
}
