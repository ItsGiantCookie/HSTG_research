package io.github.itsgiantcookie.hstglabs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/design.md §4.2 and §8.1: U units, T ticks at speed 1, at least one ingredient. */
class TechCostTest {

    private static final List<TechCost.Ingredient<String>> ONE = List.of(new TechCost.Ingredient<>("automation", 1));

    @Test
    void holdsUnitsTimeAndIngredients() {
        var cost = new TechCost<>(120, 600, List.of(new TechCost.Ingredient<>("automation", 1), new TechCost.Ingredient<>("logistic", 2)));
        assertEquals(120, cost.units());
        assertEquals(600, cost.unitTime());
        assertEquals(2, cost.ingredients().size());
        assertEquals("logistic", cost.ingredients().get(1).what());
        assertEquals(2, cost.ingredients().get(1).amount());
    }

    @Test
    void unitsRangeIsOneToOneMillion() {
        new TechCost<>(TechCost.MIN_UNITS, 1, ONE);
        new TechCost<>(TechCost.MAX_UNITS, 1, ONE);
        assertThrows(IllegalArgumentException.class, () -> new TechCost<>(0, 1, ONE));
        assertThrows(IllegalArgumentException.class, () -> new TechCost<>(1_000_001, 1, ONE));
    }

    @Test
    void unitTimeRangeIsOneTo72000Ticks() {
        new TechCost<>(1, TechCost.MIN_UNIT_TIME, ONE);
        new TechCost<>(1, TechCost.MAX_UNIT_TIME, ONE);
        assertThrows(IllegalArgumentException.class, () -> new TechCost<>(1, 0, ONE));
        assertThrows(IllegalArgumentException.class, () -> new TechCost<>(1, 72_001, ONE));
    }

    @Test
    void needsAtLeastOneIngredient() {
        assertThrows(IllegalArgumentException.class, () -> new TechCost<>(1, 1, List.of()));
        assertThrows(NullPointerException.class, () -> new TechCost<String>(1, 1, null));
    }

    @Test
    void ingredientNeedsPositiveAmountAndAKey() {
        assertThrows(IllegalArgumentException.class, () -> new TechCost.Ingredient<>("x", 0));
        assertThrows(IllegalArgumentException.class, () -> new TechCost.Ingredient<>("x", TechCost.MAX_AMOUNT + 1));
        assertThrows(NullPointerException.class, () -> new TechCost.Ingredient<String>(null, 1));
        assertEquals(TechCost.MAX_AMOUNT, new TechCost.Ingredient<>("x", TechCost.MAX_AMOUNT).amount());
    }

    @Test
    void duplicateIngredientKeysAreRefused() {
        var twice = List.of(new TechCost.Ingredient<>("a", 1), new TechCost.Ingredient<>("a", 2));
        assertThrows(IllegalArgumentException.class, () -> new TechCost<>(1, 1, twice));
    }

    @Test
    void ingredientListIsACopy() {
        var list = new ArrayList<>(ONE);
        var cost = new TechCost<>(1, 1, list);
        list.add(new TechCost.Ingredient<>("later", 1));
        assertEquals(1, cost.ingredients().size());
        assertThrows(UnsupportedOperationException.class, () -> cost.ingredients().add(new TechCost.Ingredient<>("y", 1)));
    }

    @Test
    void unitTicksDividesBySpeedAndRoundsUp() {
        var cost = new TechCost<>(1, 600, ONE);
        assertEquals(600, cost.unitTicks(1.0));
        assertEquals(300, cost.unitTicks(2.0));
        assertEquals(240, cost.unitTicks(2.5));
        assertEquals(1200, cost.unitTicks(0.5));
        assertEquals(201, new TechCost<>(1, 601, ONE).unitTicks(3.0)); // 200.33 -> 201, never faster than asked
    }

    @Test
    void unitTicksIsAtLeastOne() {
        assertEquals(1, new TechCost<>(1, 1, ONE).unitTicks(1000.0));
    }

    @Test
    void unitTicksRefusesNonPositiveOrNonFiniteSpeed() {
        var cost = new TechCost<>(1, 20, ONE);
        for (double bad : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> cost.unitTicks(bad), String.valueOf(bad));
        }
    }

    @Test
    void totalAmountIsUnitsTimesAmount() {
        var cost = new TechCost<>(TechCost.MAX_UNITS, 1, List.of(new TechCost.Ingredient<>("a", TechCost.MAX_AMOUNT)));
        assertEquals((long) TechCost.MAX_UNITS * TechCost.MAX_AMOUNT, cost.totalAmount("a")); // no int overflow
        assertEquals(0, cost.totalAmount("missing"));
    }

    @Test
    void totalTicksAtSpeedOne() {
        assertEquals(120L * 600L, new TechCost<>(120, 600, ONE).totalTicks());
        assertEquals((long) TechCost.MAX_UNITS * TechCost.MAX_UNIT_TIME,
              new TechCost<>(TechCost.MAX_UNITS, TechCost.MAX_UNIT_TIME, ONE).totalTicks());
    }

    @Test
    void withUnitsKeepsTheRest() {
        var cost = new TechCost<>(10, 20, ONE);
        var changed = cost.withUnits(3);
        assertEquals(3, changed.units());
        assertEquals(20, changed.unitTime());
        assertEquals(ONE, changed.ingredients());
        assertThrows(IllegalArgumentException.class, () -> cost.withUnits(0));
    }

    @Test
    void valueEquality() {
        assertEquals(new TechCost<>(1, 2, ONE), new TechCost<>(1, 2, List.of(new TechCost.Ingredient<>("automation", 1))));
        assertNotEquals(new TechCost<>(1, 2, ONE), new TechCost<>(1, 3, ONE));
        assertTrue(new TechCost<>(1, 2, ONE).toString().contains("units=1"));
    }
}
