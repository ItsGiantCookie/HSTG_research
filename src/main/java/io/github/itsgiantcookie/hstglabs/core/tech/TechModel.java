package io.github.itsgiantcookie.hstglabs.core.tech;

import java.util.Objects;
import java.util.Set;

/**
 * What validation needs to know about one parsed technology (docs/design.md §8.1, §8.3).
 *
 * @param id           technology id
 * @param stage        HS stage id
 * @param minTier      {@code labs.min_tier}
 * @param labTypes     lab type ids named by its lab requirements (nested ones included)
 * @param labTags      lab tags named by its lab requirements (nested ones included)
 * @param needsNetwork it has a {@code hstg_labs:network} requirement
 */
public record TechModel(String id, String stage, int minTier, Set<String> labTypes, Set<String> labTags, boolean needsNetwork) {

    public TechModel {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(stage, "stage");
        if (minTier < 1) {
            throw new IllegalArgumentException("min tier must be at least 1, was " + minTier);
        }
        labTypes = Set.copyOf(labTypes);
        labTags = Set.copyOf(labTags);
    }
}
