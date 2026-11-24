package de.germanrangers.hstglabs.api.lab;

import com.google.gson.JsonElement;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * Supplies lab types from code on every reload, merged after data files and before config overrides
 * (docs/design.md §5.5, §9.2). Same JSON shape as {@code data/<ns>/hstg_labs/lab_type/<id>.json};
 * registered through {@link RegisterLabTypeSourcesEvent}.
 */
@ApiStatus.Experimental
public interface LabTypeSource {

    /** Hands each lab type to {@code sink} as (lab type id, JSON). Server thread. */
    void supply(BiConsumer<ResourceLocation, JsonElement> sink);
}
