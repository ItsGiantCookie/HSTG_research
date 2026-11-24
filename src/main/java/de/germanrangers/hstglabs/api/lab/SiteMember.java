package de.germanrangers.hstglabs.api.lab;

import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * One member of a {@link ResearchSite} (docs/design.md §5.6, §9.3).
 *
 * @param pos     block position
 * @param labType lab type id
 * @param labTags lab tags of that type, e.g. {@code hstg_labs:chemistry}
 * @param roles   roles this member fills
 * @param active  loaded, inside capacity, not faulted, powered if needed; only active members count (D19)
 */
@ApiStatus.Experimental
public record SiteMember(BlockPos pos, ResourceLocation labType, Set<ResourceLocation> labTags, Set<LabRole> roles, boolean active) {

    public SiteMember {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(labType, "labType");
        labTags = Set.copyOf(labTags);
        roles = Set.copyOf(roles);
    }
}
