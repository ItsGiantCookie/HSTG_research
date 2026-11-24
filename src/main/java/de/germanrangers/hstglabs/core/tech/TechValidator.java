package de.germanrangers.hstglabs.core.tech;

import de.germanrangers.hstglabs.core.Role;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cross-definition checks of docs/design.md §8.3 over parsed technologies, loaded lab types and HS
 * stages. Errors disable the technology; warnings only inform. Problems found while parsing a single
 * file (syntax, ranges, unknown types) come from the tech layer, not from here.
 *
 * <p>Order of the result: per technology (by id) first, then the tier ladder checks.
 */
public final class TechValidator {

    public static final String UNKNOWN_STAGE = "unknown_stage";
    public static final String DUPLICATE_STAGE = "duplicate_stage";
    public static final String STAGE_NOT_EXTERNAL = "stage_not_external";
    public static final String UNKNOWN_LAB_TYPE = "unknown_lab_type";
    public static final String UNKNOWN_LAB_TAG = "unknown_lab_tag";
    public static final String NETWORK_WITHOUT_CONTROLLER = "network_without_controller";
    public static final String NO_LAB_TYPES = "no_lab_types";
    public static final String UNREACHABLE = "unreachable";
    public static final String LOCKED_STARTER = "locked_starter";
    public static final String SOFTLOCK = "softlock";
    public static final String EMPTY_TIER = "empty_tier";

    private TechValidator() {
    }

    /** Outcome: every problem, and the technologies an error disabled. */
    public record Result(List<Problem> problems, Set<String> disabled) {

        public Result {
            problems = List.copyOf(problems);
            disabled = Set.copyOf(disabled);
        }

        public boolean isEnabled(String techId) {
            return !disabled.contains(techId);
        }
    }

    public static Result validate(Collection<TechModel> technologies, Collection<LabTypeModel> labTypes,
          Function<String, Optional<StageModel>> stages) {
        List<TechModel> techs = technologies.stream().sorted(Comparator.comparing(TechModel::id)).toList();
        List<LabTypeModel> labs = labTypes.stream().sorted(Comparator.comparing(LabTypeModel::id)).toList();
        List<Problem> problems = new ArrayList<>();
        Set<String> disabled = new HashSet<>();

        Map<String, Long> perStage = techs.stream().collect(Collectors.groupingBy(TechModel::stage, Collectors.counting()));
        Set<String> knownTypes = labs.stream().map(LabTypeModel::id).collect(Collectors.toSet());
        Set<String> knownTags = labs.stream().flatMap(l -> l.tags().stream()).collect(Collectors.toSet());
        boolean networkController = labs.stream().anyMatch(l -> l.roles().contains(Role.CONTROLLER) && l.roles().contains(Role.NODE));

        for (TechModel tech : techs) {
            Optional<StageModel> stage = stages.apply(tech.stage());
            if (stage.isEmpty()) {
                error(problems, disabled, tech, UNKNOWN_STAGE, "stage '" + tech.stage() + "' is not loaded in History Stages");
            } else if (!stage.get().external()) {
                problems.add(Problem.warning(tech.id(), STAGE_NOT_EXTERNAL, "stage '" + tech.stage()
                      + "' is not in mode external, so the HS Research Pedestal can research it too"));
            }
            if (perStage.get(tech.stage()) > 1) {
                error(problems, disabled, tech, DUPLICATE_STAGE, "another technology also uses stage '" + tech.stage() + "'");
            }
            for (String type : new TreeSet<>(tech.labTypes())) {
                if (!knownTypes.contains(type)) {
                    error(problems, disabled, tech, UNKNOWN_LAB_TYPE, "lab requirement names lab type '" + type + "', which is not loaded");
                }
            }
            for (String tag : new TreeSet<>(tech.labTags())) {
                if (!knownTags.contains(tag)) {
                    error(problems, disabled, tech, UNKNOWN_LAB_TAG, "lab requirement names lab tag '" + tag + "', which no loaded lab type carries");
                }
            }
            if (tech.needsNetwork() && !networkController) {
                problems.add(Problem.warning(tech.id(), NETWORK_WITHOUT_CONTROLLER,
                      "needs a research network, but no loaded lab type is a network controller"));
            }
        }

        List<TechModel> enabled = techs.stream().filter(t -> !disabled.contains(t.id())).toList();
        ladder(enabled, labs, problems);
        return new Result(problems, disabled);
    }

    private static void error(List<Problem> problems, Set<String> disabled, TechModel tech, String code, String message) {
        problems.add(Problem.error(tech.id(), code, message));
        disabled.add(tech.id());
    }

    /** Tier ladder checks (§8.3): unreachable, locked starter, softlock, empty tier. */
    private static void ladder(List<TechModel> techs, List<LabTypeModel> labs, List<Problem> problems) {
        if (labs.isEmpty()) {
            if (!techs.isEmpty()) {
                problems.add(Problem.warning("lab types", NO_LAB_TYPES, "no lab types are loaded, so no technology can be researched"));
            }
            return;
        }
        int maxLabTier = labs.stream().mapToInt(LabTypeModel::tier).max().orElseThrow();
        for (TechModel tech : techs) {
            if (tech.minTier() > maxLabTier) {
                problems.add(Problem.warning(tech.id(), UNREACHABLE,
                      "needs lab tier " + tech.minTier() + ", but the highest loaded lab type is tier " + maxLabTier));
            }
        }

        Map<String, TechModel> techByStage = new HashMap<>();
        techs.forEach(t -> techByStage.put(t.stage(), t));
        for (LabTypeModel lab : labs) {
            if (lab.tier() == 1) {
                List<String> behind = lab.lockedBy().stream().filter(techByStage::containsKey).sorted().toList();
                if (!behind.isEmpty()) {
                    problems.add(Problem.warning(lab.id(), LOCKED_STARTER, "starter lab is locked behind research: "
                          + behind.stream().map(s -> techByStage.get(s).id() + " (stage " + s + ")").collect(Collectors.joining(", "))));
                }
            }
        }

        new TreeSet<>(labs.stream().map(LabTypeModel::tier).filter(t -> t > 1).toList()).forEach(tier -> {
            Set<String> unlockableBelow = techs.stream().filter(t -> t.minTier() < tier).map(TechModel::stage).collect(Collectors.toSet());
            List<LabTypeModel> ofTier = labs.stream().filter(l -> l.tier() == tier).toList();
            boolean obtainable = ofTier.stream().anyMatch(l -> unlockableBelow.containsAll(l.lockedBy()));
            if (!obtainable) {
                String detail = ofTier.stream().map(l -> l.id() + " (locked by " + String.join(", ", new TreeSet<>(l.lockedBy())) + ")")
                      .collect(Collectors.joining("; "));
                problems.add(Problem.warning("lab tier " + tier, SOFTLOCK, "no tier-" + tier + " lab can be obtained by researching only "
                      + "technologies below tier " + tier + ": " + detail));
            }
        });

        emptyTiers("technology tier", techs.stream().map(TechModel::minTier).collect(Collectors.toSet()), problems);
        emptyTiers("lab tier", labs.stream().map(LabTypeModel::tier).collect(Collectors.toSet()), problems);
    }

    private static void emptyTiers(String what, Set<Integer> used, List<Problem> problems) {
        if (used.isEmpty()) {
            return;
        }
        int min = used.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = used.stream().mapToInt(Integer::intValue).max().orElseThrow();
        for (int tier = min + 1; tier < max; tier++) {
            if (!used.contains(tier)) {
                problems.add(Problem.warning(what + " " + tier, EMPTY_TIER, what + " " + tier + " is empty, between used tiers " + min + " and " + max));
            }
        }
    }
}
