package io.github.itsgiantcookie.hstglabs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itsgiantcookie.hstglabs.core.LabTierTable.Tier;
import io.github.itsgiantcookie.hstglabs.core.tech.Problem;
import java.util.List;
import org.junit.jupiter.api.Test;

class LabTierTableTest {

    @Test
    void levelsAreSortedAndLookedUp() {
        LabTierTable table = LabTierTable.of(List.of(new Tier(2, "Advanced", 0xFF806A, "x:adv"), new Tier(1, "Basic", 0x5FFFB8, "x:basic")));
        assertEquals(List.of(1, 2), table.levels());
        assertEquals("Basic", table.tier(1).name());
        assertEquals(0xFF806A, table.tier(2).color());
        assertTrue(table.problems().isEmpty());
    }

    @Test
    void anUnknownLevelFallsBackToItsNumber() {
        LabTierTable table = LabTierTable.of(List.of(new Tier(1, "Basic", 0x5FFFB8, "")));
        Tier t = table.tier(7);
        assertEquals(7, t.level());
        assertEquals("Tier 7", t.name());
        assertEquals(LabTierTable.FALLBACK_COLOR, t.color());
        assertEquals("", t.icon());
        assertTrue(table.isDefined(1) && !table.isDefined(7));
    }

    @Test
    void aLaterDuplicateWinsWithAWarning() {
        LabTierTable table = LabTierTable.of(List.of(new Tier(1, "Basic", 1, ""), new Tier(1, "Primitive", 2, "")));
        assertEquals("Primitive", table.tier(1).name());
        assertEquals(1, table.problems().size());
        Problem p = table.problems().get(0);
        assertEquals(Problem.Severity.WARNING, p.severity());
        assertEquals("duplicate_tier", p.code());
        assertEquals("lab tier 1", p.subject());
    }

    @Test
    void levelsBelowOneAreDroppedWithAnError() {
        LabTierTable table = LabTierTable.of(List.of(new Tier(0, "Zero", 1, ""), new Tier(-3, "Neg", 1, ""), new Tier(1, "Basic", 1, "")));
        assertEquals(List.of(1), table.levels());
        assertEquals(2, table.problems().size());
        assertTrue(table.problems().stream().allMatch(p -> p.severity() == Problem.Severity.ERROR && p.code().equals("invalid_tier_level")));
    }

    @Test
    void anInsertedLevelSitsBetweenItsNeighbours() {
        // D28: plain integers let a pack insert levels; comparisons stay numeric
        LabTierTable table = LabTierTable.of(List.of(new Tier(1, "Basic", 1, ""), new Tier(3, "Elite", 1, ""), new Tier(2, "Steam", 1, "")));
        assertEquals(List.of(1, 2, 3), table.levels());
        assertEquals("Steam", table.tier(2).name());
    }

    @Test
    void colorIsMaskedToRgbAndNameMustNotBeBlank() {
        assertEquals(0x123456, new Tier(1, "a", 0xFF123456, "").color());
        assertThrows(IllegalArgumentException.class, () -> new Tier(1, " ", 0, ""));
        assertThrows(NullPointerException.class, () -> new Tier(1, null, 0, ""));
        assertThrows(NullPointerException.class, () -> new Tier(1, "a", 0, null));
    }

    @Test
    void emptyTableHasOnlyFallbacks() {
        assertEquals("Tier 1", LabTierTable.EMPTY.tier(1).name());
        assertTrue(LabTierTable.EMPTY.levels().isEmpty());
    }
}
