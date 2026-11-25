package io.github.itsgiantcookie.hstglabs.core;

import io.github.itsgiantcookie.hstglabs.core.tech.Problem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Lab tiers (D28, docs/design.md §5.5): plain integer levels, each with a name, colour and icon from
 * {@code lab_tier} data. Comparisons stay on the integers; this table only labels them. A level with
 * no data is shown as "Tier N".
 */
public final class LabTierTable {

    /** Neutral gray for levels without data. */
    public static final int FALLBACK_COLOR = 0xA0A0A0;
    public static final LabTierTable EMPTY = of(List.of());

    private final Map<Integer, Tier> tiers;
    private final List<Problem> problems;

    private LabTierTable(Map<Integer, Tier> tiers, List<Problem> problems) {
        this.tiers = tiers;
        this.problems = problems;
    }

    /**
     * @param name  a translation key or plain text
     * @param color 0xRRGGBB (any alpha is dropped)
     * @param icon  item id, empty for none
     */
    public record Tier(int level, String name, int color, String icon) {

        public Tier {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(icon, "icon");
            if (name.isBlank()) {
                throw new IllegalArgumentException("tier " + level + " has a blank name");
            }
            color &= 0xFFFFFF;
        }
    }

    /** Builds the table in load order: a later entry for the same level replaces the earlier one. */
    public static LabTierTable of(Collection<Tier> entries) {
        Map<Integer, Tier> tiers = new TreeMap<>();
        List<Problem> problems = new ArrayList<>();
        for (Tier t : entries) {
            String subject = "lab tier " + t.level();
            if (t.level() < 1) {
                problems.add(Problem.error(subject, "invalid_tier_level", "lab tier levels start at 1, ignored " + t.name()));
                continue;
            }
            Tier old = tiers.put(t.level(), t);
            if (old != null) {
                problems.add(Problem.warning(subject, "duplicate_tier", t.name() + " replaces " + old.name()));
            }
        }
        return new LabTierTable(java.util.Collections.unmodifiableMap(tiers), List.copyOf(problems));
    }

    public Tier tier(int level) {
        Tier t = tiers.get(level);
        return t != null ? t : new Tier(level, "Tier " + level, FALLBACK_COLOR, "");
    }

    public boolean isDefined(int level) {
        return tiers.containsKey(level);
    }

    /** Defined levels, ascending. */
    public List<Integer> levels() {
        return List.copyOf(tiers.keySet());
    }

    public List<Tier> all() {
        return List.copyOf(tiers.values());
    }

    public List<Problem> problems() {
        return problems;
    }
}
