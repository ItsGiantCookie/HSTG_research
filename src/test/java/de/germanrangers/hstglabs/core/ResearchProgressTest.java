package de.germanrangers.hstglabs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** docs/design.md §4.2 (claim / credit / cancel, done + inFlight < U) and D7 (cost changed on reload). */
class ResearchProgressTest {

    @Test
    void startsEmpty() {
        var p = new ResearchProgress(3);
        assertEquals(3, p.units());
        assertEquals(0, p.done());
        assertEquals(0, p.inFlight());
        assertFalse(p.isComplete());
        assertEquals(0.0, p.fraction());
        assertEquals(3, p.claimable());
    }

    @Test
    void claimStopsAtTheCap() {
        var p = new ResearchProgress(3);
        assertTrue(p.tryClaim());
        assertTrue(p.tryClaim());
        assertTrue(p.tryClaim());
        assertFalse(p.tryClaim(), "done + inFlight must stay below U");
        assertEquals(3, p.inFlight());
        assertEquals(0, p.claimable());
    }

    @Test
    void finishCreditsAndCompletes() {
        var p = new ResearchProgress(2);
        p.tryClaim();
        p.tryClaim();
        assertTrue(p.finishUnit());
        assertFalse(p.isComplete());
        assertEquals(0.5, p.fraction());
        assertTrue(p.finishUnit());
        assertTrue(p.isComplete());
        assertEquals(2, p.done());
        assertEquals(0, p.inFlight());
        assertEquals(1.0, p.fraction());
    }

    @Test
    void cancelFreesTheClaimWithoutCredit() {
        var p = new ResearchProgress(1);
        p.tryClaim();
        p.cancelUnit();
        assertEquals(0, p.done());
        assertEquals(0, p.inFlight());
        assertTrue(p.tryClaim(), "a cancelled unit can be claimed again");
    }

    @Test
    void finishOrCancelWithoutClaimIsAnError() {
        var p = new ResearchProgress(1);
        assertThrows(IllegalStateException.class, p::finishUnit);
        assertThrows(IllegalStateException.class, p::cancelUnit);
    }

    @Test
    void restoreFromSave() {
        var p = ResearchProgress.restore(10, 4, 2);
        assertEquals(4, p.done());
        assertEquals(2, p.inFlight());
        assertEquals(4, p.claimable());
    }

    @Test
    void restoreRefusesImpossibleCounts() {
        assertThrows(IllegalArgumentException.class, () -> ResearchProgress.restore(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ResearchProgress.restore(5, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> ResearchProgress.restore(5, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> ResearchProgress.restore(5, 6, 0));
    }

    @Test
    void restoreToleratesSavedReservationsAboveTheCap() {
        // a cost lowered while units were reserved in unloaded chunks: keep the reservations (§4.2)
        var p = ResearchProgress.restore(5, 3, 4);
        assertEquals(7, p.done() + p.inFlight());
        assertFalse(p.tryClaim());
        assertEquals(0, p.claimable());
    }

    @Test
    void unitsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchProgress(0));
    }

    @Test
    void costRaisedKeepsDoneUnits() {
        var p = ResearchProgress.restore(5, 3, 1);
        p.setUnits(10);
        assertEquals(10, p.units());
        assertEquals(3, p.done());
        assertEquals(1, p.inFlight());
        assertFalse(p.isComplete());
    }

    @Test
    void costLoweredBelowDoneFinishesD7() {
        var p = ResearchProgress.restore(10, 6, 0);
        p.setUnits(4);
        assertTrue(p.isComplete(), "D7: finish if done >= new U");
        assertEquals(4, p.done(), "done is clamped to the new U");
        assertEquals(1.0, p.fraction());
    }

    @Test
    void costLoweredWhileUnitsInFlightNeverOvershoots() {
        var p = ResearchProgress.restore(10, 2, 3);
        p.setUnits(3);
        assertFalse(p.tryClaim());
        assertTrue(p.finishUnit());
        assertFalse(p.finishUnit(), "a unit finished after the cap was reached is not credited");
        assertFalse(p.finishUnit());
        assertEquals(3, p.done());
        assertEquals(0, p.inFlight());
        assertThrows(IllegalArgumentException.class, () -> p.setUnits(0));
    }

    @Test
    void randomisedSequencesNeverExceedU() {
        Random random = new Random(42);
        for (int run = 0; run < 500; run++) {
            int units = 1 + random.nextInt(20);
            var p = new ResearchProgress(units);
            for (int step = 0; step < 200; step++) {
                switch (random.nextInt(4)) {
                    case 0 -> p.tryClaim();
                    case 1 -> {
                        if (p.inFlight() > 0) {
                            p.finishUnit();
                        }
                    }
                    case 2 -> {
                        if (p.inFlight() > 0) {
                            p.cancelUnit();
                        }
                    }
                    default -> {
                        if (random.nextInt(10) == 0) {
                            p.setUnits(1 + random.nextInt(20));
                        }
                    }
                }
                assertTrue(p.done() <= p.units(), "done never exceeds U");
                assertTrue(p.done() >= 0 && p.inFlight() >= 0);
            }
        }
    }

    @Test
    void readableToString() {
        assertEquals("ResearchProgress[1+2/5]", ResearchProgress.restore(5, 1, 2).toString());
    }
}
