package io.github.itsgiantcookie.hstglabs.core;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/** The built-in lab requirement types of docs/design.md §5.6. */
public final class LabRequirements {

    private LabRequirements() {
    }

    /**
     * {@code hstg_labs:lab}: at least {@code count} active members with lab type {@code labType} or lab
     * tag {@code labTag} (exactly one of the two), optionally only those filling {@code role}.
     */
    public record Lab(String labType, String labTag, int count, Role role) implements LabRequirement {

        public Lab {
            if ((labType == null) == (labTag == null)) {
                throw new IllegalArgumentException("a lab requirement names exactly one of lab_type or lab_tag");
            }
            if (count < 1) {
                throw new IllegalArgumentException("count must be at least 1, was " + count);
            }
        }

        public static Lab type(String labType, int count) {
            return new Lab(labType, null, count, null);
        }

        public static Lab tag(String labTag, int count) {
            return new Lab(null, labTag, count, null);
        }

        public Lab as(Role newRole) {
            return new Lab(labType, labTag, count, newRole);
        }

        @Override
        public boolean test(Context context) {
            return context.site().countActive(this::matches) >= count;
        }

        private boolean matches(Member member) {
            boolean kind = labType != null ? member.labType().equals(labType) : member.tags().contains(labTag);
            return kind && (role == null || member.has(role));
        }

        @Override
        public String describe() {
            String what = labType != null ? labType : "#" + labTag;
            String as = role == null ? "" : " as " + role.name().toLowerCase(Locale.ROOT);
            return count + "× " + what + as;
        }
    }

    /** {@code hstg_labs:network}: the site is a distributed network, not a basic lab. */
    public record Network() implements LabRequirement {

        @Override
        public boolean test(Context context) {
            return context.site().network();
        }

        @Override
        public String describe() {
            return "a research network";
        }
    }

    /** {@code hstg_labs:min_tier}: the worker slot's effective tier is at least {@code tier}. */
    public record MinTier(int tier) implements LabRequirement {

        public MinTier {
            if (tier < 1) {
                throw new IllegalArgumentException("tier must be at least 1, was " + tier);
            }
        }

        @Override
        public boolean test(Context context) {
            return context.effectiveTier() >= tier;
        }

        @Override
        public String describe() {
            return "lab tier " + tier;
        }
    }

    /** {@code hstg_labs:all}: every part holds; names the first missing part. */
    public record AllOf(List<LabRequirement> of) implements LabRequirement {

        public AllOf {
            of = nonEmpty(of);
        }

        @Override
        public boolean test(Context context) {
            return firstMissing(context).isEmpty();
        }

        @Override
        public Optional<LabRequirement> firstMissing(Context context) {
            return LabRequirement.firstMissing(of, context);
        }

        @Override
        public String describe() {
            return "all of " + list(of);
        }
    }

    /** {@code hstg_labs:any}: at least one part holds (an OR group). */
    public record AnyOf(List<LabRequirement> of) implements LabRequirement {

        public AnyOf {
            of = nonEmpty(of);
        }

        @Override
        public boolean test(Context context) {
            return of.stream().anyMatch(requirement -> requirement.test(context));
        }

        @Override
        public String describe() {
            return "any of " + list(of);
        }
    }

    private static List<LabRequirement> nonEmpty(List<LabRequirement> of) {
        if (of.isEmpty()) {
            throw new IllegalArgumentException("a requirement group needs at least one entry");
        }
        return List.copyOf(of);
    }

    private static String list(List<LabRequirement> of) {
        return of.stream().map(LabRequirement::describe).collect(Collectors.joining(", ", "[", "]"));
    }
}
