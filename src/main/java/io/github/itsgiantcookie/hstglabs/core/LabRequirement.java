package io.github.itsgiantcookie.hstglabs.core;

import java.util.List;
import java.util.Optional;

/**
 * A condition a site must meet to claim units of a technology (docs/design.md §5.6). Open for
 * addons: the API's requirement registry (WP-06) maps ids to implementations of this interface; the
 * built-in types are in {@link LabRequirements}.
 */
public interface LabRequirement {

    boolean test(Context context);

    /** Short English description for logs and the idle reason detail; screens use their own lang keys. */
    String describe();

    /** The requirement that is not met, or empty. Composites name their first missing part. */
    default Optional<LabRequirement> firstMissing(Context context) {
        return test(context) ? Optional.empty() : Optional.of(this);
    }

    /** First missing requirement of a technology's {@code labs.requires} list (all must hold; empty = any site). */
    static Optional<LabRequirement> firstMissing(List<? extends LabRequirement> all, Context context) {
        for (LabRequirement requirement : all) {
            Optional<LabRequirement> missing = requirement.firstMissing(context);
            if (missing.isPresent()) {
                return missing;
            }
        }
        return Optional.empty();
    }

    /**
     * What a requirement is tested against: the site and the effective tier of the worker slot that
     * wants to claim (§5.9).
     */
    record Context(SiteModel site, int effectiveTier) {
    }
}
