package io.github.itsgiantcookie.hstglabs.api.modifier;

import io.github.itsgiantcookie.hstglabs.api.lab.ResearchSite;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** What a {@link Modifier} is asked about. Fields that do not apply to its scope are empty. */
@ApiStatus.Experimental
public interface ModifierContext {

    OwnerId owner();

    /** The technology being researched, if any. */
    Optional<ResourceLocation> technology();

    /** The site, for worker and site scopes. */
    Optional<ResearchSite> site();

    /** The worker's position, for the worker scope. */
    Optional<BlockPos> worker();
}
