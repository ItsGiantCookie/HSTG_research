package de.germanrangers.hstglabs.api.lab;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;

/**
 * A condition a site must meet to claim units of a technology (docs/design.md §5.6, §9.2), used in
 * a technology's {@code labs.requires}; registered through {@link RegisterLabRequirementTypesEvent}.
 * Built in (WP-07): {@code hstg_labs:lab}, {@code hstg_labs:network}, {@code hstg_labs:min_tier},
 * {@code hstg_labs:all}, {@code hstg_labs:any}.
 *
 * @param <T> the parsed requirement entry
 */
@ApiStatus.Experimental
public interface LabRequirementType<T> {

    /** Fields of this type inside a requirement entry. */
    MapCodec<T> codec();

    /**
     * Whether {@code site} meets the requirement for a worker slot of {@code effectiveTier}
     * (min(worker, controller), D29). Server thread; called often, keep it cheap.
     */
    boolean test(T requirement, ResearchSite site, int effectiveTier);

    /** What is required, for the idle reason, tooltips and the dev UI. */
    Component describe(T requirement);
}
