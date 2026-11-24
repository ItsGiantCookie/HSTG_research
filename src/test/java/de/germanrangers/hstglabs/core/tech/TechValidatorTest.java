package de.germanrangers.hstglabs.core.tech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.germanrangers.hstglabs.core.Role;
import de.germanrangers.hstglabs.core.tech.Problem.Severity;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** docs/design.md §8.3: errors disable a technology, warnings do not; tier ladder checks. */
class TechValidatorTest {

    private static final Set<Role> ALL_ROLES = EnumSet.of(Role.CONTROLLER, Role.CONSUMER, Role.WORKER);
    private static final Set<Role> CORE = EnumSet.of(Role.CONTROLLER, Role.NODE);
    private static final Set<Role> SERVER = EnumSet.of(Role.WORKER, Role.NODE);

    private static final LabTypeModel BASIC = new LabTypeModel("hstg_labs:basic_lab", 1, Set.of(), ALL_ROLES, Set.of());

    private static Function<String, Optional<StageModel>> stages(String... ids) {
        Map<String, StageModel> map = new java.util.HashMap<>();
        for (String id : ids) {
            map.put(id, new StageModel(id, true));
        }
        return id -> Optional.ofNullable(map.get(id));
    }

    private static TechModel tech(String id, String stage, int minTier) {
        return new TechModel(id, stage, minTier, Set.of(), Set.of(), false);
    }

    private static List<String> codes(TechValidator.Result result) {
        return result.problems().stream().map(Problem::code).toList();
    }

    @Test
    void cleanSetHasNoProblems() {
        var result = TechValidator.validate(List.of(tech("t:a", "a", 1)), List.of(BASIC), stages("a"));
        assertEquals(List.of(), result.problems());
        assertEquals(Set.of(), result.disabled());
    }

    @Test
    void unknownStageIsAnErrorAndDisables() {
        var result = TechValidator.validate(List.of(tech("t:a", "missing", 1)), List.of(BASIC), stages());
        assertEquals(List.of(TechValidator.UNKNOWN_STAGE), codes(result));
        assertEquals(Severity.ERROR, result.problems().get(0).severity());
        assertEquals(Set.of("t:a"), result.disabled());
        assertTrue(result.problems().get(0).message().contains("missing"));
    }

    @Test
    void twoTechnologiesOnOneStageAreBothDisabled() {
        var result = TechValidator.validate(List.of(tech("t:a", "s", 1), tech("t:b", "s", 1)), List.of(BASIC), stages("s"));
        assertEquals(List.of(TechValidator.DUPLICATE_STAGE, TechValidator.DUPLICATE_STAGE), codes(result));
        assertEquals(Set.of("t:a", "t:b"), result.disabled());
    }

    @Test
    void stageNotExternalIsAWarningD10() {
        Function<String, Optional<StageModel>> catalog = id -> Optional.of(new StageModel(id, false));
        var result = TechValidator.validate(List.of(tech("t:a", "a", 1)), List.of(BASIC), catalog);
        assertEquals(List.of(TechValidator.STAGE_NOT_EXTERNAL), codes(result));
        assertEquals(Severity.WARNING, result.problems().get(0).severity());
        assertEquals(Set.of(), result.disabled(), "warnings never disable");
    }

    @Test
    void unknownLabTagIsReported() {
        var t = new TechModel("t:chem", "a", 1, Set.of(), Set.of("hstg_labs:chemistry"), false);
        var result = TechValidator.validate(List.of(t), List.of(BASIC), stages("a"));
        assertEquals(List.of(TechValidator.UNKNOWN_LAB_TAG), codes(result));
        assertTrue(result.problems().get(0).message().contains("hstg_labs:chemistry"));
        assertEquals(Set.of("t:chem"), result.disabled());
    }

    @Test
    void knownLabTagAndTypePass() {
        var chem = new LabTypeModel("mypack:chemistry_lab", 1, Set.of("hstg_labs:chemistry"), EnumSet.of(Role.CONSUMER), Set.of());
        var t = new TechModel("t:chem", "a", 1, Set.of("mypack:chemistry_lab"), Set.of("hstg_labs:chemistry"), false);
        assertEquals(List.of(), TechValidator.validate(List.of(t), List.of(BASIC, chem), stages("a")).problems());
    }

    @Test
    void unknownLabTypeIsAnError() {
        var t = new TechModel("t:x", "a", 1, Set.of("mypack:nope"), Set.of(), false);
        var result = TechValidator.validate(List.of(t), List.of(BASIC), stages("a"));
        assertEquals(List.of(TechValidator.UNKNOWN_LAB_TYPE), codes(result));
    }

    @Test
    void networkRequirementWithoutAnyNetworkControllerWarns() {
        var t = new TechModel("t:n", "a", 1, Set.of(), Set.of(), true);
        assertEquals(List.of(TechValidator.NETWORK_WITHOUT_CONTROLLER), codes(TechValidator.validate(List.of(t), List.of(BASIC), stages("a"))));
        var core = new LabTypeModel("hstg_labs:research_core", 1, Set.of(), CORE, Set.of());
        assertEquals(List.of(), TechValidator.validate(List.of(t), List.of(BASIC, core), stages("a")).problems());
    }

    @Test
    void noLabTypesAtAllIsOneWarning() {
        var result = TechValidator.validate(List.of(tech("t:a", "a", 1), tech("t:b", "b", 2)), List.of(), stages("a", "b"));
        assertEquals(List.of(TechValidator.NO_LAB_TYPES), codes(result));
    }

    @Test
    void techAboveEveryLabTierIsUnreachable() {
        var result = TechValidator.validate(List.of(tech("t:a", "a", 1), tech("t:high", "h", 3)), List.of(BASIC), stages("a", "h"));
        assertTrue(codes(result).contains(TechValidator.UNREACHABLE), codes(result).toString());
        assertEquals(Set.of(), result.disabled());
    }

    /** WP-07 acceptance: tier-2 labs only unlockable by tier-2 research. */
    @Test
    void tierTwoLabsBehindTierTwoResearchAreASoftlock() {
        var advancedCore = new LabTypeModel("hstg_labs:advanced_core", 2, Set.of(), CORE, Set.of("networking"));
        var server = new LabTypeModel("hstg_labs:research_server", 2, Set.of(), SERVER, Set.of("networking"));
        var techs = List.of(tech("t:basics", "basics", 1), tech("t:networking", "networking", 2));
        var result = TechValidator.validate(techs, List.of(BASIC, advancedCore, server), stages("basics", "networking"));
        assertEquals(List.of(TechValidator.SOFTLOCK), codes(result));
        var problem = result.problems().get(0);
        assertEquals(Severity.WARNING, problem.severity());
        assertEquals("lab tier 2", problem.subject());
        assertTrue(problem.message().contains("hstg_labs:advanced_core"), problem.message());
        assertTrue(problem.message().contains("networking"), problem.message());
    }

    @Test
    void tierTwoLabsBehindTierOneResearchAreFine() {
        var advancedCore = new LabTypeModel("hstg_labs:advanced_core", 2, Set.of(), CORE, Set.of("networking"));
        var techs = List.of(tech("t:networking", "networking", 1), tech("t:big", "big", 2));
        assertEquals(List.of(), TechValidator.validate(techs, List.of(BASIC, advancedCore), stages("networking", "big")).problems());
    }

    @Test
    void oneObtainableLabPerTierIsEnough() {
        var lockedHigh = new LabTypeModel("x:locked", 2, Set.of(), SERVER, Set.of("big"));
        var free = new LabTypeModel("x:free", 2, Set.of(), CORE, Set.of());
        var techs = List.of(tech("t:a", "a", 1), tech("t:big", "big", 2));
        assertEquals(List.of(), TechValidator.validate(techs, List.of(BASIC, lockedHigh, free), stages("a", "big")).problems());
    }

    @Test
    void labLockedByAStageNoTechnologyOwnsIsNotObtainable() {
        // "locked only by stages whose technologies need a lower tier" (§8.3): a quest-only stage does not count
        var questLocked = new LabTypeModel("x:quest", 2, Set.of(), CORE, Set.of("quest_stage"));
        var techs = List.of(tech("t:a", "a", 1), tech("t:big", "big", 2));
        assertEquals(List.of(TechValidator.SOFTLOCK), codes(TechValidator.validate(techs, List.of(BASIC, questLocked), stages("a", "big"))));
    }

    @Test
    void starterLabBehindATechnologyIsFlagged() {
        var lockedBasic = new LabTypeModel("hstg_labs:basic_lab", 1, Set.of(), ALL_ROLES, Set.of("a"));
        var result = TechValidator.validate(List.of(tech("t:a", "a", 1)), List.of(lockedBasic), stages("a"));
        assertEquals(List.of(TechValidator.LOCKED_STARTER), codes(result));
        assertEquals("hstg_labs:basic_lab", result.problems().get(0).subject());
    }

    @Test
    void starterLockedByANonTechnologyStageIsNotFlagged() {
        var questBasic = new LabTypeModel("hstg_labs:basic_lab", 1, Set.of(), ALL_ROLES, Set.of("quest"));
        assertEquals(List.of(), TechValidator.validate(List.of(tech("t:a", "a", 1)), List.of(questBasic), stages("a")).problems());
    }

    @Test
    void gapsInTheLadderAreEmptyTiers() {
        var tier3 = new LabTypeModel("x:elite", 3, Set.of(), CORE, Set.of());
        var techs = List.of(tech("t:a", "a", 1), tech("t:c", "c", 3));
        var result = TechValidator.validate(techs, List.of(BASIC, tier3), stages("a", "c"));
        assertEquals(List.of(TechValidator.EMPTY_TIER, TechValidator.EMPTY_TIER), codes(result));
        assertEquals(List.of("technology tier 2", "lab tier 2"), result.problems().stream().map(Problem::subject).toList());
    }

    @Test
    void disabledTechnologiesDoNotUnlockLaddersOrCountAsUsedTiers() {
        // the only tier-1 tech unlocking the tier-2 lab is broken (unknown stage): the ladder is a softlock
        var advanced = new LabTypeModel("x:adv", 2, Set.of(), CORE, Set.of("net"));
        var techs = List.of(tech("t:net", "net", 1), tech("t:big", "big", 2));
        var result = TechValidator.validate(techs, List.of(BASIC, advanced), stages("big"));
        assertEquals(List.of(TechValidator.UNKNOWN_STAGE, TechValidator.SOFTLOCK), codes(result));
    }

    @Test
    void resultIsSortedAndModelsValidate() {
        var result = TechValidator.validate(List.of(tech("t:b", "x", 1), tech("t:a", "y", 1)), List.of(BASIC), stages());
        assertEquals(List.of("t:a", "t:b"), result.problems().stream().map(Problem::subject).toList());
        assertThrows(IllegalArgumentException.class, () -> tech("t:a", "a", 0));
        assertThrows(IllegalArgumentException.class, () -> new LabTypeModel("x", 0, Set.of(), ALL_ROLES, Set.of()));
        assertThrows(NullPointerException.class, () -> new StageModel(null, true));
        assertThrows(NullPointerException.class, () -> new Problem(Severity.ERROR, "s", null, "m"));
        assertFalse(result.isEnabled("t:a"));
        assertTrue(TechValidator.validate(List.of(tech("t:a", "y", 1)), List.of(BASIC), stages("y")).isEnabled("t:a"));
        assertEquals("ERROR t:a [unknown_stage]: stage 'y' is not loaded in History Stages",
              result.problems().get(0).toString());
    }
}
