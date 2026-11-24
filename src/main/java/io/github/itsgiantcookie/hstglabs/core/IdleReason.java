package io.github.itsgiantcookie.hstglabs.core;

import java.util.List;
import java.util.Objects;

/**
 * Why a worker slot is not running a unit (docs/design.md §4.4, §5.7). Id-keyed so addons can report
 * their own reasons through the API's idle reason registry (§9.2); {@code detail} carries what the
 * screen names (the tier needed, the missing requirement, the missing ingredient), empty otherwise.
 */
public record IdleReason(String id, String detail) {

    public static final String NAMESPACE = "hstg_labs:";

    public static final IdleReason NO_RESEARCH = of("no_research");
    public static final IdleReason BLOCKED = of("blocked");
    public static final IdleReason NO_POWER = of("no_power");
    public static final IdleReason CAP_REACHED = of("cap_reached");
    public static final String LAB_TIER = NAMESPACE + "lab_tier";
    public static final String LAB_REQUIREMENTS = NAMESPACE + "lab_requirements";
    public static final String MISSING_INGREDIENTS = NAMESPACE + "missing_ingredients";
    /** Ids of every built-in reason, registered in the API's idle reason registry (§9.2). */
    public static final List<String> BUILT_IN = List.of(NO_RESEARCH.id(), BLOCKED.id(), LAB_TIER, LAB_REQUIREMENTS,
          NO_POWER.id(), CAP_REACHED.id(), MISSING_INGREDIENTS);

    public IdleReason {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(detail, "detail");
    }

    public static IdleReason labTier(int needed) {
        return new IdleReason(LAB_TIER, Integer.toString(needed));
    }

    public static IdleReason labRequirements(LabRequirement missing) {
        return new IdleReason(LAB_REQUIREMENTS, missing.describe());
    }

    public static IdleReason missingIngredients(Object ingredient) {
        return new IdleReason(MISSING_INGREDIENTS, String.valueOf(ingredient));
    }

    private static IdleReason of(String path) {
        return new IdleReason(NAMESPACE + path, "");
    }
}
