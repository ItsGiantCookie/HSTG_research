package io.github.itsgiantcookie.hstglabs.core.tech;

import io.github.itsgiantcookie.hstglabs.core.Role;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * What validation needs to know about one lab type (docs/design.md §5.5, §8.3).
 *
 * @param id       lab type id
 * @param tier     lab tier (§5.9)
 * @param tags     lab tags it carries
 * @param roles    roles its blocks fill
 * @param lockedBy HS stages that lock its block item or its recipe; empty = obtainable from the start
 */
public record LabTypeModel(String id, int tier, Set<String> tags, Set<Role> roles, Set<String> lockedBy) {

    public LabTypeModel {
        Objects.requireNonNull(id, "id");
        if (tier < 1) {
            throw new IllegalArgumentException("lab tier must be at least 1, was " + tier);
        }
        tags = Set.copyOf(tags);
        roles = roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
        lockedBy = Set.copyOf(lockedBy);
    }
}
