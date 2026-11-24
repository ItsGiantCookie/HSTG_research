package io.github.itsgiantcookie.hstglabs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.itsgiantcookie.hstglabs.core.ConsumedSet.Portion;
import io.github.itsgiantcookie.hstglabs.core.TechCost.Ingredient;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** docs/design.md §5.3: simulate then commit, one set gathered across consumers (D20), round-robin per ingredient. */
class ArbiterTest {

    private static List<Ingredient<String>> set(Object... pairs) {
        List<Ingredient<String>> out = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.add(new Ingredient<>((String) pairs[i], (Integer) pairs[i + 1]));
        }
        return out;
    }

    @Test
    void takesAWholeSetFromOneConsumer() {
        var a = new FakeSource("a").with("auto", 5).with("log", 5);
        var taken = new Arbiter<String>().takeSet(set("auto", 2, "log", 1), List.of(a));
        assertTrue(taken.success());
        assertEquals(3, a.count("auto"));
        assertEquals(4, a.count("log"));
        assertEquals(2, taken.set().orElseThrow().total("auto"));
        assertEquals(1, taken.set().orElseThrow().total("log"));
        assertEquals(List.of(), taken.spilled());
    }

    @Test
    void gathersOneSetAcrossConsumersD20() {
        var a = new FakeSource("a").with("auto", 1);
        var b = new FakeSource("b").with("auto", 2);
        var taken = new Arbiter<String>().takeSet(set("auto", 3), List.of(a, b));
        assertTrue(taken.success());
        assertEquals(List.of(new Portion<>("a", "auto", 1), new Portion<>("b", "auto", 2)), taken.set().orElseThrow().portions());
        assertEquals(0, a.total() + b.total());
    }

    @Test
    void allOrNothingWhenALineIsMissing() {
        var a = new FakeSource("a").with("auto", 5);
        var b = new FakeSource("b").with("log", 0);
        var taken = new Arbiter<String>().takeSet(set("auto", 1, "log", 1), List.of(a, b));
        assertFalse(taken.success());
        assertEquals("log", taken.missing().orElseThrow());
        assertEquals(5, a.count("auto"), "simulated only, nothing taken");
    }

    @Test
    void noConsumersMeansTheFirstLineIsMissing() {
        var taken = new Arbiter<String>().takeSet(set("auto", 1, "log", 1), List.of());
        assertEquals("auto", taken.missing().orElseThrow());
    }

    @Test
    void roundRobinPerIngredient() {
        var a = new FakeSource("a").with("auto", 10);
        var b = new FakeSource("b").with("auto", 10);
        var c = new FakeSource("c").with("auto", 10);
        var arbiter = new Arbiter<String>();
        List<String> order = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            order.add(arbiter.takeSet(set("auto", 1), List.of(a, b, c)).set().orElseThrow().portions().get(0).consumer());
        }
        assertEquals(List.of("a", "b", "c", "a", "b", "c"), order);
        assertEquals(8, a.count("auto"));
        assertEquals(8, b.count("auto"));
        assertEquals(8, c.count("auto"));
    }

    @Test
    void eachIngredientHasItsOwnCursor() {
        var a = new FakeSource("a").with("auto", 10).with("log", 10);
        var b = new FakeSource("b").with("auto", 10).with("log", 10);
        var arbiter = new Arbiter<String>();
        arbiter.takeSet(set("auto", 1), List.of(a, b));
        var second = arbiter.takeSet(set("auto", 1, "log", 1), List.of(a, b)).set().orElseThrow().portions();
        assertEquals(new Portion<>("b", "auto", 1), second.get(0), "auto moved on");
        assertEquals(new Portion<>("a", "log", 1), second.get(1), "log starts fresh");
    }

    @Test
    void roundRobinSkipsEmptyConsumers() {
        var a = new FakeSource("a");
        var b = new FakeSource("b").with("auto", 3);
        var arbiter = new Arbiter<String>();
        for (int i = 0; i < 3; i++) {
            assertEquals("b", arbiter.takeSet(set("auto", 1), List.of(a, b)).set().orElseThrow().portions().get(0).consumer());
        }
        assertFalse(arbiter.takeSet(set("auto", 1), List.of(a, b)).success());
    }

    @Test
    void cursorOnARemovedConsumerStartsOver() {
        var a = new FakeSource("a").with("auto", 5);
        var b = new FakeSource("b").with("auto", 5);
        var c = new FakeSource("c").with("auto", 5);
        var arbiter = new Arbiter<String>();
        arbiter.takeSet(set("auto", 1), List.of(a, b, c)); // a
        arbiter.takeSet(set("auto", 1), List.of(a, b, c)); // b
        assertEquals("a", arbiter.takeSet(set("auto", 1), List.of(a, c)).set().orElseThrow().portions().get(0).consumer());
    }

    @Test
    void neverStarvesAConsumer() {
        Random random = new Random(7);
        for (int run = 0; run < 200; run++) {
            int n = 2 + random.nextInt(5);
            List<FakeSource> sources = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                sources.add(new FakeSource("s" + i).with("auto", 1000));
            }
            int amount = 1 + random.nextInt(3);
            var arbiter = new Arbiter<String>();
            for (int k = 0; k < 50; k++) {
                assertTrue(arbiter.takeSet(set("auto", amount), sources).success());
                long min = sources.stream().mapToLong(s -> s.count("auto")).min().orElseThrow();
                long max = sources.stream().mapToLong(s -> s.count("auto")).max().orElseThrow();
                assertTrue(max - min <= amount, "equal consumers drain evenly, spread " + (max - min));
            }
        }
    }

    @Test
    void overlappingLinesFailOnCommitAndRollBack() {
        // two keys that match the same physical stock (e.g. an item and a tag holding it)
        var shared = new SharedSource("x", 1, true);
        var taken = new Arbiter<String>().takeSet(set("item", 1, "tag", 1), List.of(shared));
        assertFalse(taken.success());
        assertEquals("tag", taken.missing().orElseThrow());
        assertEquals(1, shared.stock, "the first line was given back");
        assertEquals(List.of(), taken.spilled());
    }

    @Test
    void rollbackThatCannotGiveBackSpills() {
        var shared = new SharedSource("x", 1, false);
        var taken = new Arbiter<String>().takeSet(set("item", 1, "tag", 1), List.of(shared));
        assertFalse(taken.success());
        assertEquals(List.of(new Portion<>("x", "item", 1)), taken.spilled(), "never lost: the caller drops it");
    }

    @Test
    void refundGoesBackToWhereItCameFrom() {
        var a = new FakeSource("a").with("auto", 1);
        var b = new FakeSource("b").with("auto", 2);
        var arbiter = new Arbiter<String>();
        var consumed = arbiter.takeSet(set("auto", 3), List.of(a, b)).set().orElseThrow();
        assertEquals(List.of(), arbiter.refund(consumed, List.of(a, b)));
        assertEquals(1, a.count("auto"));
        assertEquals(2, b.count("auto"));
    }

    @Test
    void refundSpillsWhatDoesNotFitOrHasNoHome() {
        var a = new FakeSource("a").with("auto", 2);
        var b = new FakeSource("b").with("auto", 2);
        var arbiter = new Arbiter<String>();
        var consumed = arbiter.takeSet(set("auto", 4), List.of(a, b)).set().orElseThrow();
        a.with("auto", 9).capacity(10); // refilled meanwhile, room for 1
        var spill = arbiter.refund(consumed, List.of(a)); // b was broken
        assertEquals(List.of(new Portion<>("a", "auto", 1), new Portion<>("b", "auto", 2)), spill);
        assertEquals(10, a.count("auto"));
    }

    @Test
    void duplicateConsumerIdsAreRefused() {
        var a = new FakeSource("a").with("auto", 1);
        var a2 = new FakeSource("a").with("auto", 1);
        assertThrows(IllegalArgumentException.class, () -> new Arbiter<String>().takeSet(set("auto", 1), List.of(a, a2)));
        assertThrows(IllegalArgumentException.class, () -> new Arbiter<String>().refund(new ConsumedSet<>(List.of()), List.of(a, a2)));
    }

    @Test
    void consumedSetValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Portion<>("a", "auto", 0));
        assertThrows(NullPointerException.class, () -> new Portion<>(null, "auto", 1));
        assertEquals(0, new ConsumedSet<String>(List.of()).total("auto"));
    }

    /** One stock answering for every key, like a tag and an item over the same slot. */
    private static final class SharedSource implements PackSource<String> {
        private final String id;
        private final boolean acceptsBack;
        long stock;

        SharedSource(String id, long stock, boolean acceptsBack) {
            this.id = id;
            this.stock = stock;
            this.acceptsBack = acceptsBack;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public long available(String what) {
            return stock;
        }

        @Override
        public long take(String what, long amount) {
            long t = Math.min(stock, amount);
            stock -= t;
            return t;
        }

        @Override
        public long giveBack(String what, long amount) {
            if (!acceptsBack) {
                return 0;
            }
            stock += amount;
            return amount;
        }
    }
}
