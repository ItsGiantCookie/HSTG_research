package de.germanrangers.hstglabs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.germanrangers.hstglabs.core.ResearchQueue.AddResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** docs/design.md §4.3: one queue per owner, max length queue_max (default 7), no duplicates. */
class ResearchQueueTest {

    private static ResearchQueue<String> queueOf(int max, String... ids) {
        var q = new ResearchQueue<String>(max);
        for (String id : ids) {
            assertEquals(AddResult.ADDED, q.add(id));
        }
        return q;
    }

    @Test
    void defaultMaxIsSeven() {
        assertEquals(7, ResearchQueue.DEFAULT_MAX);
        assertEquals(7, new ResearchQueue<String>().max());
    }

    @Test
    void emptyQueueHasNoHead() {
        var q = new ResearchQueue<String>();
        assertTrue(q.isEmpty());
        assertEquals(Optional.empty(), q.head());
        assertEquals(0, q.size());
    }

    @Test
    void addAppendsInOrder() {
        var q = queueOf(7, "a", "b", "c");
        assertEquals(List.of("a", "b", "c"), q.entries());
        assertEquals(Optional.of("a"), q.head());
        assertTrue(q.contains("b"));
        assertEquals(1, q.indexOf("b"));
        assertEquals(-1, q.indexOf("z"));
    }

    @Test
    void respectsMax() {
        var q = queueOf(2, "a", "b");
        assertTrue(q.isFull());
        assertEquals(AddResult.FULL, q.add("c"));
        assertEquals(AddResult.FULL, q.insert(0, "c"));
        assertEquals(List.of("a", "b"), q.entries());
    }

    @Test
    void refusesDuplicates() {
        var q = queueOf(7, "a", "b");
        assertEquals(AddResult.DUPLICATE, q.add("a"));
        assertEquals(AddResult.DUPLICATE, q.insert(0, "b"));
        assertEquals(List.of("a", "b"), q.entries());
    }

    @Test
    void duplicateIsReportedBeforeFull() {
        // the player learns the real reason: it is already queued
        var q = queueOf(1, "a");
        assertEquals(AddResult.DUPLICATE, q.add("a"));
    }

    @Test
    void insertAtIndexClampsToTheEnds() {
        var q = queueOf(7, "a", "b");
        assertEquals(AddResult.ADDED, q.insert(1, "x"));
        assertEquals(AddResult.ADDED, q.insert(-5, "first"));
        assertEquals(AddResult.ADDED, q.insert(99, "last"));
        assertEquals(List.of("first", "a", "x", "b", "last"), q.entries());
    }

    @Test
    void removeById() {
        var q = queueOf(7, "a", "b", "c");
        assertTrue(q.remove("b"));
        assertFalse(q.remove("b"));
        assertEquals(List.of("a", "c"), q.entries());
        assertTrue(q.remove("a"));
        assertEquals(Optional.of("c"), q.head());
    }

    @Test
    void moveReorders() {
        var q = queueOf(7, "a", "b", "c", "d");
        assertTrue(q.move("d", 0));
        assertEquals(List.of("d", "a", "b", "c"), q.entries());
        assertTrue(q.move("d", 2));
        assertEquals(List.of("a", "b", "d", "c"), q.entries());
        assertTrue(q.move("a", 99));
        assertEquals(List.of("b", "d", "c", "a"), q.entries());
        assertTrue(q.move("a", -1));
        assertEquals(List.of("a", "b", "d", "c"), q.entries());
        assertFalse(q.move("zzz", 0));
    }

    @Test
    void lowerMaxKeepsEntriesButRefusesAdds() {
        var q = queueOf(7, "a", "b", "c");
        q.setMax(2);
        assertEquals(List.of("a", "b", "c"), q.entries(), "a config change never drops queued research");
        assertTrue(q.isFull());
        assertEquals(AddResult.FULL, q.add("d"));
        q.remove("c");
        assertEquals(AddResult.FULL, q.add("d"));
        q.remove("b");
        assertEquals(AddResult.ADDED, q.add("d"));
    }

    @Test
    void maxMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchQueue<String>(0));
        assertThrows(IllegalArgumentException.class, () -> new ResearchQueue<String>(7).setMax(0));
    }

    @Test
    void nullIdsAreRefused() {
        var q = new ResearchQueue<String>();
        assertThrows(NullPointerException.class, () -> q.add(null));
        assertThrows(NullPointerException.class, () -> q.insert(0, null));
    }

    @Test
    void entriesIsAnImmutableSnapshot() {
        var q = queueOf(7, "a");
        var snapshot = q.entries();
        q.add("b");
        assertEquals(List.of("a"), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("x"));
    }

    @Test
    void removeIfDropsWhatNoLongerQualifies() {
        // unlocked elsewhere or lost for good (§4.1): drop it from the queue
        var q = queueOf(7, "a", "b", "c", "d");
        List<String> removed = q.removeIf(id -> id.equals("b") || id.equals("d"));
        assertEquals(List.of("b", "d"), removed);
        assertEquals(List.of("a", "c"), q.entries());
        assertEquals(List.of(), q.removeIf(id -> false));
    }

    @Test
    void clearEmptiesTheQueue() {
        var q = queueOf(7, "a", "b");
        q.clear();
        assertTrue(q.isEmpty());
    }

    @Test
    void restoreFromSaveKeepsOrderAndDropsDuplicates() {
        var q = ResearchQueue.restore(3, List.of("a", "b", "a", "c", "d"));
        assertEquals(List.of("a", "b", "c", "d"), q.entries(), "saved entries beyond max survive, duplicates do not");
        assertEquals(3, q.max());
        assertTrue(q.isFull());
    }

    @Test
    void readableToString() {
        assertEquals("ResearchQueue[a, b]/7", queueOf(7, "a", "b").toString());
    }
}
