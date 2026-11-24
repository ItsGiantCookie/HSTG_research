package io.github.itsgiantcookie.hstglabs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itsgiantcookie.hstglabs.core.LabRequirements.Lab;
import io.github.itsgiantcookie.hstglabs.core.SiteScheduler.SiteTick;
import io.github.itsgiantcookie.hstglabs.core.SiteScheduler.SlotState;
import io.github.itsgiantcookie.hstglabs.core.SiteScheduler.SlotStatus;
import io.github.itsgiantcookie.hstglabs.core.TechCost.Ingredient;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * docs/design.md §5.7 + D18: every site works on the strict queue head; a head it cannot run idles
 * it with the right reason and is never skipped. §4.2: never exceed U, never lose an uncredited pack.
 */
class SiteSchedulerTest {

    private static final String CHEM = "hstg_labs:chemistry";
    private static final SiteModel BASIC = SiteModel.basicLab("hstg_labs:basic_lab", Set.of(), 1);

    private final Map<String, TechSpec<String>> specs = new HashMap<>();
    private final Map<String, ResearchProgress> progress = new HashMap<>();
    private final ResearchQueue<String> queue = new ResearchQueue<>();
    private final Arbiter<String> arbiter = new Arbiter<>();

    private void tech(String id, int units, int unitTime, int minTier, List<LabRequirement> requires, Object... set) {
        List<Ingredient<String>> ingredients = new ArrayList<>();
        for (int i = 0; i < set.length; i += 2) {
            ingredients.add(new Ingredient<>((String) set[i], (Integer) set[i + 1]));
        }
        specs.put(id, new TechSpec<>(new TechCost<>(units, unitTime, ingredients), minTier, requires, false));
        progress.put(id, new ResearchProgress(units));
    }

    private SiteTick<String, String> tick(SiteModel site, List<WorkerSlot<String, String>> slots, List<FakeSource> sources) {
        return SiteScheduler.tick(site, queue, specs::get, progress::get, slots, arbiter, sources);
    }

    private static List<WorkerSlot<String, String>> slots(int n, int tier) {
        List<WorkerSlot<String, String>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new WorkerSlot<>(tier, 1.0));
        }
        return out;
    }

    private static SlotStatus idle(IdleReason reason) {
        return new SlotStatus(SlotState.IDLE, reason);
    }

    @Test
    void emptyQueueIdlesWithNoResearch() {
        var tick = tick(BASIC, slots(2, 1), List.of());
        assertEquals(List.of(idle(IdleReason.NO_RESEARCH), idle(IdleReason.NO_RESEARCH)), tick.slots());
        assertEquals(2, tick.count(IdleReason.NO_RESEARCH.id()));
    }

    @Test
    void headWithoutDefinitionIdlesWithNoResearch() {
        queue.add("removed");
        assertEquals(List.of(idle(IdleReason.NO_RESEARCH)), tick(BASIC, slots(1, 1), List.of()).slots());
    }

    @Test
    void blockedHeadIdles() {
        tech("t", 3, 20, 1, List.of(), "auto", 1);
        specs.put("t", new TechSpec<>(specs.get("t").cost(), 1, List.of(), true));
        queue.add("t");
        assertEquals(List.of(idle(IdleReason.BLOCKED)), tick(BASIC, slots(1, 1), List.of(new FakeSource("a").with("auto", 9))).slots());
    }

    @Test
    void gatedHeadIdlesTheSiteAndIsNeverSkippedD18() {
        tech("tier2", 3, 20, 2, List.of(), "auto", 1);
        tech("easy", 3, 20, 1, List.of(), "auto", 1);
        queue.add("tier2");
        queue.add("easy");
        var source = new FakeSource("a").with("auto", 100);
        var slots = slots(1, 1);
        for (int i = 0; i < 200; i++) {
            assertEquals(List.of(idle(IdleReason.labTier(2))), tick(BASIC, slots, List.of(source)).slots());
        }
        assertEquals(0, progress.get("easy").done() + progress.get("easy").inFlight(), "the next entry is never run instead");
        assertEquals(100, source.count("auto"));
    }

    @Test
    void effectiveTierIsCappedByTheController() {
        tech("t3", 3, 20, 3, List.of(), "auto", 1);
        queue.add("t3");
        var source = new FakeSource("a").with("auto", 10);
        var advancedCore = new SiteModel(true, 2, List.of());
        assertEquals(List.of(idle(IdleReason.labTier(3))), tick(advancedCore, slots(1, 3), List.of(source)).slots(), "elite server, advanced core");
        var eliteCore = new SiteModel(true, 3, List.of());
        assertEquals(List.of(idle(IdleReason.labTier(3))), tick(eliteCore, slots(1, 2), List.of(source)).slots(), "advanced server, elite core");
        assertEquals(SlotState.STARTED, tick(eliteCore, slots(1, 3), List.of(source)).slots().get(0).state());
    }

    @Test
    void missingLabRequirementIsNamed() {
        var chemistry = Lab.tag(CHEM, 1);
        tech("chem", 3, 20, 1, List.of(chemistry), "auto", 1);
        queue.add("chem");
        var source = new FakeSource("a").with("auto", 10);
        var without = new SiteModel(true, 2, List.of());
        assertEquals(List.of(idle(IdleReason.labRequirements(chemistry))), tick(without, slots(1, 2), List.of(source)).slots());

        var inactive = new SiteModel(true, 2, List.of(new Member("mypack:chemistry_lab", Set.of(CHEM), EnumSet.of(Role.CONSUMER), false)));
        assertEquals(List.of(idle(IdleReason.labRequirements(chemistry))), tick(inactive, slots(1, 2), List.of(source)).slots(), "D19");

        var with = new SiteModel(true, 2, List.of(new Member("mypack:chemistry_lab", Set.of(CHEM), EnumSet.of(Role.CONSUMER), true)));
        assertEquals(SlotState.STARTED, tick(with, slots(1, 2), List.of(source)).slots().get(0).state());
    }

    @Test
    void tierIsCheckedBeforeOtherRequirements() {
        tech("t", 3, 20, 2, List.of(Lab.tag(CHEM, 1)), "auto", 1);
        queue.add("t");
        assertEquals(List.of(idle(IdleReason.labTier(2))), tick(BASIC, slots(1, 1), List.of()).slots());
    }

    @Test
    void unpoweredIdleSlotReportsNoPower() {
        tech("t", 3, 20, 1, List.of(), "auto", 1);
        queue.add("t");
        var slots = slots(1, 1);
        slots.get(0).setPowered(false);
        var source = new FakeSource("a").with("auto", 5);
        assertEquals(List.of(idle(IdleReason.NO_POWER)), tick(BASIC, slots, List.of(source)).slots());
        assertEquals(5, source.count("auto"));
    }

    @Test
    void unpoweredRunningSlotPausesAndResumes() {
        tech("t", 3, 20, 1, List.of(), "auto", 1);
        queue.add("t");
        var slots = slots(1, 1);
        var source = new FakeSource("a").with("auto", 5);
        tick(BASIC, slots, List.of(source));
        tick(BASIC, slots, List.of(source));
        assertEquals(19, slots.get(0).cycle().ticksLeft());
        slots.get(0).setPowered(false);
        for (int i = 0; i < 10; i++) {
            assertEquals(new SlotStatus(SlotState.PAUSED, IdleReason.NO_POWER), tick(BASIC, slots, List.of(source)).slots().get(0));
        }
        assertEquals(19, slots.get(0).cycle().ticksLeft());
        slots.get(0).setPowered(true);
        assertEquals(SlotState.RUNNING, tick(BASIC, slots, List.of(source)).slots().get(0).state());
        assertEquals(18, slots.get(0).cycle().ticksLeft());
    }

    @Test
    void capReachedWhenEveryUnitIsClaimed() {
        tech("t", 1, 20, 1, List.of(), "auto", 1);
        queue.add("t");
        var tick = tick(BASIC, slots(2, 1), List.of(new FakeSource("a").with("auto", 5)));
        assertEquals(List.of(new SlotStatus(SlotState.STARTED, null), idle(IdleReason.CAP_REACHED)), tick.slots());
        assertEquals(1, tick.count(IdleReason.CAP_REACHED.id()), "slots without a reason are not counted");
        assertEquals(0, tick.count(IdleReason.NO_POWER.id()));
    }

    @Test
    void missingIngredientsIsNamed() {
        tech("t", 3, 20, 1, List.of(), "auto", 1, "log", 1);
        queue.add("t");
        var tick = tick(BASIC, slots(1, 1), List.of(new FakeSource("a").with("auto", 5)));
        assertEquals(List.of(idle(IdleReason.missingIngredients("log"))), tick.slots());
    }

    @Test
    void oneSpeedOneSlotFinishesUUnitsInUTimesTTicks() {
        tech("t", 3, 20, 1, List.of(), "auto", 1);
        queue.add("t");
        var slots = slots(1, 1);
        var source = new FakeSource("a").with("auto", 10);
        assertEquals(SlotState.STARTED, tick(BASIC, slots, List.of(source)).slots().get(0).state()); // tick 0
        List<String> credited = new ArrayList<>();
        for (int t = 1; t <= 60; t++) {
            credited.addAll(tick(BASIC, slots, List.of(source)).credited());
            if (t == 59) {
                assertEquals(2, progress.get("t").done());
            }
        }
        assertEquals(List.of("t", "t", "t"), credited);
        assertTrue(progress.get("t").isComplete());
        assertEquals(7, source.count("auto"));
        assertEquals(idle(IdleReason.CAP_REACHED), tick(BASIC, slots, List.of(source)).slots().get(0));
    }

    @Test
    void parallelSlotsRunUnitsAtOnce() {
        tech("t", 4, 10, 1, List.of(), "auto", 1);
        queue.add("t");
        var slots = slots(2, 1);
        var source = new FakeSource("a").with("auto", 10);
        tick(BASIC, slots, List.of(source));
        for (int t = 1; t <= 20; t++) {
            tick(BASIC, slots, List.of(source));
        }
        assertEquals(4, progress.get("t").done());
    }

    @Test
    void runningUnitsFinishWhenARequirementDrops() {
        var chemistry = Lab.tag(CHEM, 1);
        tech("chem", 3, 2, 1, List.of(chemistry), "auto", 1);
        queue.add("chem");
        var source = new FakeSource("a").with("auto", 10);
        var slots = slots(1, 2);
        var with = new SiteModel(true, 2, List.of(new Member("x", Set.of(CHEM), EnumSet.of(Role.CONSUMER), true)));
        var broken = new SiteModel(true, 2, List.of());
        tick(with, slots, List.of(source));
        tick(broken, slots, List.of(source));
        var finish = tick(broken, slots, List.of(source));
        assertEquals(List.of("chem"), finish.credited());
        assertEquals(List.of(idle(IdleReason.labRequirements(chemistry))), finish.slots(), "no new unit starts");
    }

    @Test
    void unitIsCreditedToTheTechnologyItWasClaimedFor() {
        tech("a", 3, 2, 1, List.of(), "auto", 1);
        tech("b", 3, 2, 1, List.of(), "auto", 1);
        queue.add("a");
        queue.add("b");
        var source = new FakeSource("s").with("auto", 10);
        var slots = slots(1, 1);
        tick(BASIC, slots, List.of(source));
        queue.move("b", 0);
        tick(BASIC, slots, List.of(source));
        var finish = tick(BASIC, slots, List.of(source));
        assertEquals(List.of("a"), finish.credited());
        assertEquals(1, progress.get("a").done());
        assertEquals(java.util.Optional.of("b"), slots.get(0).cycle().tech(), "the slot moves on to the new head");
    }

    @Test
    void workerSlotValidation() {
        var slot = new WorkerSlot<String, String>(1, 1.0);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> slot.setTier(0));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> slot.setSpeed(0.0));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> slot.setSpeed(Double.NaN));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> slot.setSpeed(Double.POSITIVE_INFINITY));
        assertEquals(java.util.Set.of(), new Member("x", Set.of(), EnumSet.noneOf(Role.class), true).roles());
        slot.setTier(3);
        slot.setSpeed(2.5);
        assertEquals(3, slot.tier());
        assertEquals(2.5, slot.speed());
        assertTrue(slot.powered());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new TechSpec<>(specsCost(), 0, List.of(), false));
    }

    private static TechCost<String> specsCost() {
        return new TechCost<>(1, 1, List.of(new Ingredient<>("auto", 1)));
    }

    @Test
    void uncreditedFinishSpillsWhenTheConsumerIsFull() {
        tech("t", 3, 1, 1, List.of(), "auto", 1);
        progress.put("t", ResearchProgress.restore(3, 2, 0));
        queue.add("t");
        var source = new FakeSource("a").with("auto", 1).capacity(1);
        var slots = slots(1, 1);
        tick(BASIC, slots, List.of(source));
        progress.get("t").setUnits(2);
        source.with("auto", 1); // refilled to capacity meanwhile
        var finish = tick(BASIC, slots, List.of(source));
        assertEquals(1, finish.spilled().size());
        assertEquals(List.of(), finish.credited());
    }

    /**
     * Random queues, power, cost changes, slot removals and refills. After every tick: done never
     * exceeds U, inFlight equals the slots running that technology, and every pack is accounted for.
     */
    @Test
    void randomisedSequencesKeepTheInvariants() {
        Random random = new Random(20261124);
        for (int run = 0; run < 300; run++) {
            specs.clear();
            progress.clear();
            queue.clear();
            Arbiter<String> runArbiter = new Arbiter<>();
            int techs = 1 + random.nextInt(3);
            for (int i = 0; i < techs; i++) {
                tech("t" + i, 1 + random.nextInt(6), 1 + random.nextInt(5), 1, List.of(), "auto", 1 + random.nextInt(2), "log" + (i % 2), 1);
                queue.add("t" + i);
            }
            List<FakeSource> sources = new ArrayList<>();
            for (int i = 0; i < 1 + random.nextInt(3); i++) {
                sources.add(new FakeSource("c" + i));
            }
            List<WorkerSlot<String, String>> slots = slots(1 + random.nextInt(4), 1);
            Map<String, Long> inserted = new HashMap<>();
            Map<String, Long> usedUp = new HashMap<>();
            Map<String, Long> spilled = new HashMap<>();

            for (int step = 0; step < 300; step++) {
                switch (random.nextInt(10)) {
                    case 0, 1 -> {
                        var s = sources.get(random.nextInt(sources.size()));
                        String what = List.of("auto", "log0", "log1").get(random.nextInt(3));
                        long n = 1 + random.nextInt(3);
                        s.with(what, n);
                        inserted.merge(what, n, Long::sum);
                    }
                    case 2 -> slots.get(random.nextInt(slots.size())).setPowered(random.nextBoolean());
                    case 3 -> queue.move("t" + random.nextInt(techs), random.nextInt(techs));
                    case 4 -> {
                        if (random.nextInt(4) == 0) {
                            progress.get("t" + random.nextInt(techs)).setUnits(1 + random.nextInt(6));
                        }
                    }
                    case 5 -> {
                        var slot = slots.get(random.nextInt(slots.size()));
                        if (slot.cycle().isRunning() && random.nextInt(3) == 0) {
                            String tech = slot.cycle().tech().orElseThrow();
                            for (var p : slot.cycle().cancel(progress.get(tech), runArbiter, sources)) {
                                spilled.merge(p.what(), p.amount(), Long::sum);
                            }
                        }
                    }
                    default -> {
                    }
                }
                // what each running slot will use up if it is credited this tick
                Map<WorkerSlot<String, String>, ConsumedSet<String>> before = new HashMap<>();
                for (var slot : slots) {
                    slot.cycle().consumed().ifPresent(set -> before.put(slot, set));
                }
                SiteTick<String, String> tick = SiteScheduler.tick(BASIC, queue, specs::get, progress::get, slots, runArbiter, sources);
                for (var p : tick.spilled()) {
                    spilled.merge(p.what(), p.amount(), Long::sum);
                }
                assertEquals(tick.credited().size(), tick.creditedSlots().size());
                for (int index : tick.creditedSlots()) {
                    for (var p : before.get(slots.get(index)).portions()) {
                        usedUp.merge(p.what(), p.amount(), Long::sum);
                    }
                }

                for (var id : specs.keySet()) {
                    var p = progress.get(id);
                    assertTrue(p.done() <= p.units(), "done never exceeds U: " + id + " " + p);
                    long running = slots.stream().filter(s -> s.cycle().tech().filter(id::equals).isPresent()).count();
                    assertEquals(running, p.inFlight(), "inFlight matches running slots: " + id);
                }
                for (String what : List.of("auto", "log0", "log1")) {
                    long inSources = sources.stream().mapToLong(s -> s.count(what)).sum();
                    long inFlight = slots.stream().flatMap(s -> s.cycle().consumed().stream()).mapToLong(set -> set.total(what)).sum();
                    assertEquals(inserted.getOrDefault(what, 0L),
                          inSources + inFlight + usedUp.getOrDefault(what, 0L) + spilled.getOrDefault(what, 0L),
                          "pack conservation for " + what + " in run " + run + " step " + step);
                }
            }
        }
    }
}
