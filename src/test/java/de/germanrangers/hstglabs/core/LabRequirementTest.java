package de.germanrangers.hstglabs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.germanrangers.hstglabs.core.LabRequirements.AllOf;
import de.germanrangers.hstglabs.core.LabRequirements.AnyOf;
import de.germanrangers.hstglabs.core.LabRequirements.Lab;
import de.germanrangers.hstglabs.core.LabRequirements.MinTier;
import de.germanrangers.hstglabs.core.LabRequirements.Network;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

/** docs/design.md §5.6 (requirement types), §5.9 + D29 (effective tier), D19 (only active members count). */
class LabRequirementTest {

    private static final String CHEM = "hstg_labs:chemistry";
    private static final String SIM = "mypack:simulation_server";

    private static Member member(String type, Set<String> tags, boolean active, Role... roles) {
        return new Member(type, tags, roles.length == 0 ? EnumSet.of(Role.WORKER) : EnumSet.of(roles[0], roles), active);
    }

    /** Core + one active chemistry consumer + one active and one inactive simulation server. */
    private static final SiteModel NETWORK = new SiteModel(true, 2, List.of(
          member("hstg_labs:research_core", Set.of(), true, Role.CONTROLLER, Role.NODE),
          member("mypack:chemistry_lab", Set.of(CHEM), true, Role.CONSUMER, Role.NODE),
          member(SIM, Set.of("hstg_labs:simulation"), true, Role.WORKER, Role.NODE),
          member(SIM, Set.of("hstg_labs:simulation"), false, Role.WORKER, Role.NODE)));

    private static final SiteModel BASIC = SiteModel.basicLab("hstg_labs:basic_lab", Set.of(), 1);

    private static LabRequirement.Context ctx(SiteModel site, int workerTier) {
        return new LabRequirement.Context(site, site.effectiveTier(workerTier));
    }

    static Stream<Arguments> table() {
        return Stream.of(
              // requirement, site, worker tier, met
              Arguments.of(Lab.tag(CHEM, 1), NETWORK, 2, true),
              Arguments.of(Lab.tag(CHEM, 2), NETWORK, 2, false),
              Arguments.of(Lab.type(SIM, 1), NETWORK, 2, true),
              Arguments.of(Lab.type(SIM, 2), NETWORK, 2, false), // the second server is inactive (D19)
              Arguments.of(Lab.tag(CHEM, 1).as(Role.CONSUMER), NETWORK, 2, true),
              Arguments.of(Lab.tag(CHEM, 1).as(Role.WORKER), NETWORK, 2, false),
              Arguments.of(Lab.tag(CHEM, 1), BASIC, 1, false),
              Arguments.of(Lab.type("hstg_labs:basic_lab", 1), BASIC, 1, true),
              Arguments.of(new Network(), NETWORK, 2, true),
              Arguments.of(new Network(), BASIC, 1, false),
              Arguments.of(new MinTier(2), NETWORK, 2, true),
              Arguments.of(new MinTier(2), NETWORK, 1, false),
              Arguments.of(new MinTier(3), NETWORK, 4, false), // core tier 2 caps the slot (D29)
              Arguments.of(new MinTier(1), BASIC, 1, true),
              Arguments.of(new AllOf(List.of(Lab.tag(CHEM, 1), Lab.type(SIM, 1))), NETWORK, 2, true),
              Arguments.of(new AllOf(List.of(Lab.tag(CHEM, 1), Lab.type(SIM, 2))), NETWORK, 2, false),
              Arguments.of(new AnyOf(List.of(Lab.tag(CHEM, 5), Lab.type(SIM, 1))), NETWORK, 2, true),
              Arguments.of(new AnyOf(List.of(Lab.tag(CHEM, 5), new Network())), BASIC, 1, false),
              Arguments.of(new AnyOf(List.of(new AllOf(List.of(new Network(), new MinTier(2))), Lab.type("hstg_labs:basic_lab", 1))),
                    BASIC, 1, true));
    }

    @ParameterizedTest(name = "[{index}] {0} on worker tier {2} -> {3}")
    @MethodSource("table")
    void requirementTable(LabRequirement requirement, SiteModel site, int workerTier, boolean met) {
        assertEquals(met, requirement.test(ctx(site, workerTier)));
        assertEquals(met, requirement.firstMissing(ctx(site, workerTier)).isEmpty());
    }

    @Test
    void effectiveTierIsMinOfWorkerAndController() {
        assertEquals(2, NETWORK.effectiveTier(3));
        assertEquals(1, NETWORK.effectiveTier(1));
        assertEquals(2, NETWORK.effectiveTier(2));
        assertEquals(1, BASIC.effectiveTier(1));
        assertThrows(IllegalArgumentException.class, () -> NETWORK.effectiveTier(0));
    }

    @Test
    void allOfNamesTheFirstMissingLeaf() {
        var missing = Lab.type(SIM, 2);
        var all = new AllOf(List.of(Lab.tag(CHEM, 1), new AllOf(List.of(new Network(), missing))));
        assertEquals(Optional.of(missing), all.firstMissing(ctx(NETWORK, 2)));
    }

    @Test
    void anyOfNamesItselfWhenNothingHolds() {
        var any = new AnyOf(List.of(Lab.tag(CHEM, 5), new MinTier(4)));
        assertEquals(Optional.of(any), any.firstMissing(ctx(NETWORK, 2)));
    }

    @Test
    void listHelperChecksInOrder() {
        var first = new Network();
        var second = Lab.tag(CHEM, 1);
        assertEquals(Optional.of(first), LabRequirement.firstMissing(List.of(first, second), ctx(BASIC, 1)));
        assertEquals(Optional.empty(), LabRequirement.firstMissing(List.of(), ctx(BASIC, 1)), "empty = any site (§8.1)");
        assertEquals(Optional.empty(), LabRequirement.firstMissing(List.of(second), ctx(NETWORK, 2)));
    }

    @Test
    void describeNamesWhatIsMissing() {
        assertEquals("1× #hstg_labs:chemistry", Lab.tag(CHEM, 1).describe());
        assertEquals("2× mypack:simulation_server as worker", Lab.type(SIM, 2).as(Role.WORKER).describe());
        assertEquals("a research network", new Network().describe());
        assertEquals("lab tier 3", new MinTier(3).describe());
        assertEquals("all of [a research network, lab tier 2]", new AllOf(List.of(new Network(), new MinTier(2))).describe());
        assertEquals("any of [a research network, lab tier 2]", new AnyOf(List.of(new Network(), new MinTier(2))).describe());
    }

    @Test
    void invalidRequirementsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Lab(null, null, 1, null), "needs a type or a tag");
        assertThrows(IllegalArgumentException.class, () -> new Lab(SIM, CHEM, 1, null), "not both");
        assertThrows(IllegalArgumentException.class, () -> Lab.tag(CHEM, 0));
        assertThrows(IllegalArgumentException.class, () -> new MinTier(0));
        assertThrows(IllegalArgumentException.class, () -> new AllOf(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AnyOf(List.of()));
    }

    @Test
    void siteModelValidation() {
        assertThrows(IllegalArgumentException.class, () -> new SiteModel(false, 0, List.of()));
        assertThrows(NullPointerException.class, () -> new Member(null, Set.of(), EnumSet.of(Role.WORKER), true));
        var basic = BASIC.members().get(0);
        assertEquals(EnumSet.of(Role.CONTROLLER, Role.CONSUMER, Role.WORKER), basic.roles(), "a basic lab has all three roles, no node (§5.1)");
        assertEquals(1, BASIC.controllerTier());
        assertEquals(false, BASIC.network());
    }
}
