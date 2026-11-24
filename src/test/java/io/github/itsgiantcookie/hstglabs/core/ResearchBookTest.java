package io.github.itsgiantcookie.hstglabs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itsgiantcookie.hstglabs.core.ResearchQueue.AddResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * One owner's research state (docs/design.md §4.1, §6.2): queue, progress, pending unlocks and
 * blocked technologies. D5 (relock), D7 (cost change), D32 (a blocked technology leaves the queue).
 */
class ResearchBookTest {

    private static ResearchBook<String> book() {
        return new ResearchBook<>(7);
    }

    @Test
    void progressIsCreatedOnDemandAndFollowsCostChangesD7() {
        var b = book();
        assertEquals(Optional.empty(), b.progress("t"));
        ResearchProgress p = b.progressFor("t", 10);
        assertEquals(10, p.units());
        assertEquals(1, b.addUnits("t", 10, 4) == 4 ? 1 : 0);
        b.progressFor("t", 20);
        assertEquals(20, b.progress("t").orElseThrow().units(), "cost raised");
        assertEquals(4, b.progress("t").orElseThrow().done(), "done units kept");
    }

    @Test
    void costLoweredBelowDoneMakesItReadyD7() {
        var b = book();
        b.queue().add("t");
        b.addUnits("t", 10, 6);
        assertEquals(List.of(), b.readyToUnlock());
        b.progressFor("t", 5);
        assertEquals(List.of("t"), b.readyToUnlock(), "D7: finish if done >= new U");
    }

    @Test
    void creditingTheLastUnitMakesItReady() {
        var b = book();
        b.queue().add("t");
        assertEquals(2, b.addUnits("t", 3, 2));
        assertEquals(List.of(), b.readyToUnlock());
        assertEquals(1, b.addUnits("t", 3, 5), "never more than U");
        assertTrue(b.isComplete("t"));
        assertEquals(List.of("t"), b.readyToUnlock());
        assertEquals(0, b.addUnits("t", 3, 1));
        assertThrows(IllegalArgumentException.class, () -> b.addUnits("t", 3, -1));
    }

    @Test
    void unitFinishedByALabMakesItReady() {
        var b = book();
        b.queue().add("t");
        ResearchProgress p = b.progressFor("t", 1);
        p.tryClaim();
        p.finishUnit();
        b.unitCredited("t");
        assertEquals(List.of("t"), b.readyToUnlock());
        b.unitCredited("other");
        assertEquals(List.of("t"), b.readyToUnlock(), "crediting something without progress changes nothing");
    }

    @Test
    void ourUnlockLeavesQueueKeepsCompletedProgress() {
        var b = book();
        b.queue().add("t");
        b.queue().add("u");
        b.addUnits("t", 2, 2);
        b.unlocked("t");
        assertEquals(List.of("u"), b.queue().entries());
        assertEquals(List.of(), b.readyToUnlock());
        assertTrue(b.isComplete("t"), "kept, so D5 KEEP can re-unlock without paying again");
    }

    @Test
    void unlockedElsewhereDiscardsProgress() {
        var b = book();
        b.queue().add("t");
        b.addUnits("t", 10, 3);
        b.unlockedElsewhere("t");
        assertFalse(b.queue().contains("t"));
        assertEquals(Optional.empty(), b.progress("t"));
    }

    @Test
    void blockedLeavesTheQueueKeepsProgressD32() {
        var b = book();
        b.queue().add("t");
        b.queue().add("u");
        b.addUnits("t", 2, 2);
        b.blocked("t");
        assertEquals(List.of("u"), b.queue().entries(), "the queue moves on");
        assertTrue(b.isBlocked("t"));
        assertTrue(b.isComplete("t"), "progress kept");
        assertEquals(List.of(), b.readyToUnlock(), "no retry until re-added");
        assertEquals(Set.of("t"), b.blockedTechnologies());
    }

    @Test
    void reAddingABlockedCompleteTechnologyRetriesIt() {
        var b = book();
        b.queue().add("t");
        b.addUnits("t", 2, 2);
        b.blocked("t");
        assertEquals(AddResult.ADDED, b.enqueue("t"));
        assertFalse(b.isBlocked("t"));
        assertEquals(List.of("t"), b.readyToUnlock());
    }

    @Test
    void enqueueingAnUnfinishedTechnologyIsNotReady() {
        var b = book();
        assertEquals(AddResult.ADDED, b.enqueue("t"));
        b.addUnits("t", 5, 1);
        assertEquals(List.of(), b.readyToUnlock());
        assertEquals(AddResult.DUPLICATE, b.enqueue("t"));
    }

    @Test
    void relockResetOrKeepD5() {
        var reset = book();
        reset.addUnits("t", 2, 2);
        reset.unlocked("t");
        reset.relocked("t", true);
        assertEquals(Optional.empty(), reset.progress("t"), "RESET: research again from zero");

        var keep = book();
        keep.addUnits("t", 2, 2);
        keep.unlocked("t");
        keep.relocked("t", false);
        assertTrue(keep.isComplete("t"), "KEEP: done units stay");
        assertEquals(List.of(), keep.readyToUnlock(), "not unlocked again by itself");
        keep.enqueue("t");
        assertEquals(List.of("t"), keep.readyToUnlock(), "queueing it again unlocks it without paying again");
    }

    @Test
    void lostForGoodLeavesQueueAndPendingKeepsProgress() {
        var b = book();
        b.queue().add("t");
        b.addUnits("t", 2, 2);
        b.lost("t");
        assertFalse(b.queue().contains("t"));
        assertEquals(List.of(), b.readyToUnlock());
        assertTrue(b.isComplete("t"));
    }

    @Test
    void restoreFromSave() {
        var b = ResearchBook.<String>restore(7, List.of("a", "b"),
              java.util.Map.of("a", ResearchProgress.restore(5, 2, 1), "c", ResearchProgress.restore(3, 3, 0)), Set.of("c"), List.of("c"));
        assertEquals(List.of("a", "b"), b.queue().entries());
        assertEquals(2, b.progress("a").orElseThrow().done());
        assertTrue(b.isBlocked("c"));
        assertEquals(List.of("c"), b.readyToUnlock());
        assertEquals(Set.of("a", "c"), b.progressEntries().keySet());
        assertEquals(List.of("c"), b.pending());
    }

    @Test
    void changingQueueMax() {
        var b = book();
        b.setQueueMax(1);
        assertEquals(AddResult.ADDED, b.enqueue("a"));
        assertEquals(AddResult.FULL, b.enqueue("b"));
    }
}
