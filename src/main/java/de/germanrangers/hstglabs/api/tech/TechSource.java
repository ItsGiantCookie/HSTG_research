package de.germanrangers.hstglabs.api.tech;

import com.google.gson.JsonElement;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * Supplies technology definitions from code on every reload (docs/design.md §8.1, §9.2; D3: merged
 * after the config files, before KubeJS). Definitions use the same JSON shape as
 * {@code config/hstg_labs/technologies/<id>.json}; registered through {@link RegisterTechSourcesEvent}.
 */
@ApiStatus.Experimental
public interface TechSource {

    /** Hands each definition to {@code sink} as (technology id, JSON). Server thread. */
    void supply(BiConsumer<ResourceLocation, JsonElement> sink);
}
