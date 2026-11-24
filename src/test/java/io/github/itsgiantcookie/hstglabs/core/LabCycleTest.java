package io.github.itsgiantcookie.hstglabs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itsgiantcookie.hstglabs.core.ConsumedSet.Portion;
import io.github.itsgiantcookie.hstglabs.core.LabCycle.TickStatus;
import io.github.itsgiantcookie.hstglabs.core.TechCost.Ingredient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** docs/design.md §4.2: claim a unit, consume one set at the start, finish and credit, or cancel and refund. */
class LabCycleTest {

    private static final TechCost<String> COST = new TechCost<>(3, 20, List.of(new Ingredient<>("auto", 2)));

    private final Arbiter<String> arbiter = new Arbiter<>();

    @Test
    void startClaimsAUnitAndConsumesOneSet() {
        var progress = new ResearchProgress(3);
        var source = new FakeSource("a").with("auto", 5);
        var cycle = new LabCycle<String, String>();
        var start = cycle.start("t", COST, 1.0, progress, arbiter, List.of(source));
        assertTrue(start.started());
        assertEquals(Optional.empty(), start.idle());
        assertEquals(1, progress.inFlight());
        assertEquals(3, source.count("auto"));
        assertTrue(cycle.isRunning());
        assertEquals(Optional.of("t"), cycle.tech());
        assertEquals(20, cycle.ticksLeft());
        assertEquals(2, cycle.consumed().orElseThrow().total("auto"));
    }

    @Test
    void speedShortensTheUnit() {
        var cycle = new LabCycle<String, String>();
        cycle.start("t", COST, 2.0, new ResearchProgress(3), arbiter, List.of(new FakeSource("a").with("auto", 2)));
        assertEquals(10, cycle.ticksLeft());
    }

    @Test
    void capReachedTakesNothing() {
        var progress = ResearchProgress.restore(3, 2, 1);
        var source = new FakeSource("a").with("auto", 5);
        var start = new LabCycle<String, String>().start("t", COST, 1.0, progress, arbiter, List.of(source));
        assertFalse(start.started());
        assertEquals(IdleReason.CAP_REACHED, start.idle().orElseThrow());
        assertEquals(5, source.count("auto"));
    }

    @Test
    void missingIngredientsClaimsNothing() {
        var progress = new ResearchProgress(3);
        var source = new FakeSource("a").with("auto", 1);
        var cycle = new LabCycle<String, String>();
        var start = cycle.start("t", COST, 1.0, progress, arbiter, List.of(source));
        assertEquals(IdleReason.missingIngredients("auto"), start.idle().orElseThrow());
        assertEquals(0, progress.inFlight());
        assertEquals(1, source.count("auto"));
        assertFalse(cycle.isRunning());
    }

    @Test
    void tickCountsDownThenCredits() {
        var progress = new ResearchProgress(3);
        var source = new FakeSource("a").with("auto", 2);
        var cycle = new LabCycle<String, String>();
        cycle.start("t", COST, 1.0, progress, arbiter, List.of(source));
        for (int i = 0; i < 19; i++) {
            assertEquals(TickStatus.RUNNING, cycle.tick(progress, arbiter, List.of(source)).status());
        }
        var last = cycle.tick(progress, arbiter, List.of(source));
        assertEquals(TickStatus.CREDITED, last.status());
        assertEquals(List.of(), last.spilled());
        assertEquals(1, progress.done());
        assertEquals(0, progress.inFlight());
        assertFalse(cycle.isRunning());
        assertEquals(Optional.empty(), cycle.consumed());
        assertEquals(0, source.count("auto"), "a credited unit uses its set up");
    }

    @Test
    void uncreditedFinishRefundsTheSet() {
        // cost lowered below done while the unit ran (D7): nothing counts, nothing is wasted
        var progress = ResearchProgress.restore(3, 2, 0);
        var source = new FakeSource("a").with("auto", 2);
        var cycle = new LabCycle<String, String>();
        cycle.start("t", new TechCost<>(3, 1, List.of(new Ingredient<>("auto", 2))), 1.0, progress, arbiter, List.of(source));
        progress.setUnits(2);
        var tick = cycle.tick(progress, arbiter, List.of(source));
        assertEquals(TickStatus.FINISHED_UNCREDITED, tick.status());
        assertEquals(2, progress.done());
        assertEquals(2, source.count("auto"));
    }

    @Test
    void cancelFreesTheClaimAndRefunds() {
        var progress = new ResearchProgress(3);
        var source = new FakeSource("a").with("auto", 2);
        var cycle = new LabCycle<String, String>();
        cycle.start("t", COST, 1.0, progress, arbiter, List.of(source));
        assertEquals(List.of(), cycle.cancel(progress, arbiter, List.of(source)));
        assertEquals(0, progress.inFlight());
        assertEquals(0, progress.done());
        assertEquals(2, source.count("auto"));
        assertFalse(cycle.isRunning());
    }

    @Test
    void cancelSpillsWhenTheConsumerIsGone() {
        var progress = new ResearchProgress(3);
        var cycle = new LabCycle<String, String>();
        cycle.start("t", COST, 1.0, progress, arbiter, List.of(new FakeSource("a").with("auto", 2)));
        assertEquals(List.of(new Portion<>("a", "auto", 2)), cycle.cancel(progress, arbiter, List.of()));
    }

    @Test
    void illegalTransitionsThrow() {
        var progress = new ResearchProgress(3);
        var source = new FakeSource("a").with("auto", 10);
        var cycle = new LabCycle<String, String>();
        assertThrows(IllegalStateException.class, () -> cycle.tick(progress, arbiter, List.of(source)));
        assertThrows(IllegalStateException.class, () -> cycle.cancel(progress, arbiter, List.of(source)));
        cycle.start("t", COST, 1.0, progress, arbiter, List.of(source));
        assertThrows(IllegalStateException.class, () -> cycle.start("t", COST, 1.0, progress, arbiter, List.of(source)));
    }

    @Test
    void restoreResumesMidUnit() {
        var set = new ConsumedSet<>(List.of(new Portion<>("a", "auto", 2)));
        var cycle = LabCycle.<String, String>restore("t", 5, set);
        assertTrue(cycle.isRunning());
        assertEquals(5, cycle.ticksLeft());
        assertEquals(Optional.of(set), cycle.consumed());
        var progress = ResearchProgress.restore(3, 0, 1);
        for (int i = 0; i < 4; i++) {
            cycle.tick(progress, arbiter, List.of());
        }
        assertEquals(TickStatus.CREDITED, cycle.tick(progress, arbiter, List.of()).status());
        assertThrows(IllegalArgumentException.class, () -> LabCycle.restore("t", 0, set));
        assertThrows(NullPointerException.class, () -> LabCycle.<String, String>restore(null, 1, set));
    }

    @Test
    void idleCycleReportsNothing() {
        var cycle = new LabCycle<String, String>();
        assertEquals(Optional.empty(), cycle.tech());
        assertEquals(0, cycle.ticksLeft());
        assertEquals(Optional.empty(), cycle.consumed());
    }
}
