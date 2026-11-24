package io.github.itsgiantcookie.hstglabs.core;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One member of a research site as lab requirements see it (docs/design.md §5.6).
 *
 * @param labType lab type id, e.g. {@code hstg_labs:basic_lab}
 * @param tags    lab tags, e.g. {@code hstg_labs:chemistry}
 * @param roles   roles this member fills
 * @param active  loaded, inside capacity, not faulted, powered if its type needs energy; only active
 *                members count (D19)
 */
public record Member(String labType, Set<String> tags, Set<Role> roles, boolean active) {

    public Member {
        Objects.requireNonNull(labType, "labType");
        tags = Set.copyOf(tags);
        roles = roles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(roles));
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }
}
