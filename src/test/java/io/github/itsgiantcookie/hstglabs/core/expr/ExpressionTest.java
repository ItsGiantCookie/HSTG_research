package io.github.itsgiantcookie.hstglabs.core.expr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * docs/design.md §8.6 + D24: numbers, params, + − × ÷ ^, min, max, round, floor, ceil, if(cond, a, b);
 * no loops, no side effects, a length limit. Fuzz-tested.
 */
class ExpressionTest {

    private static final Map<String, Double> P = Map.of("tier", 3.0, "base_units", 25.0, "factor", 2.0, "unit_time", 200.0);

    private static double eval(String source) {
        return Expression.parse(source).evaluate(P);
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource(delimiter = '|', value = {
          "1 + 2 * 3                        | 7",
          "(1 + 2) * 3                      | 9",
          "10 - 4 - 3                       | 3",
          "8 / 4 / 2                        | 1",
          "2 ^ 3 ^ 2                        | 512",
          "-2 ^ 2                           | -4",
          "(-2) ^ 2                         | 4",
          "--3                              | 3",
          "1.5 * 4                          | 6",
          "base_units * factor ^ (tier - 1) | 100",
          "unit_time * tier                 | 600",
          "min(5, 3, 9)                     | 3",
          "max(5, 3, 9)                     | 9",
          "round(2.5)                       | 3",
          "round(-2.5)                      | -2",
          "floor(2.9)                       | 2",
          "ceil(2.1)                        | 3",
          "if(tier > 2, 10, 20)             | 10",
          "if(tier <= 2, 10, 20)            | 20",
          "if(tier == 3, 1, 0)              | 1",
          "if(tier != 3, 1, 0)              | 0",
          "if(tier >= 3, 1, 0) + if(tier < 3, 1, 0) | 1",
          "2 × 3 ÷ 4 − 1                    | 0.5",
          "  7  *  ( 1 )                    | 7",
    })
    void evaluates(String source, double expected) {
        assertEquals(expected, eval(source), 1e-9, source);
    }

    @Test
    void ifIsLazy() {
        // the branch not taken is never evaluated, so it cannot fail
        assertEquals(1.0, eval("if(1, 1, 1 / 0)"));
        assertEquals(2.0, eval("if(0, 1 / 0, 2)"));
    }

    @Test
    void referencedParams() {
        assertEquals(Set.of("tier", "factor"), Expression.parse("factor ^ tier + min(tier, 2)").params());
        assertEquals(Set.of(), Expression.parse("1 + 2").params());
    }

    @Test
    void sourceIsKept() {
        assertEquals("tier * 2", Expression.parse("tier * 2").source());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
          "''                 | empty",
          "1 +                | expected a number",
          "(1 + 2             | expected ')'",
          "1 2                | unexpected",
          "foo(1)             | unknown function",
          "min()              | at least 1",
          "round(1, 2)        | exactly 1",
          "if(1, 2)           | exactly 3",
          "1 $ 2              | unexpected character",
          "1..2               | number",
          "max(1, 2           | expected ')'",
    })
    void parseErrorsAreReported(String source, String fragment) {
        ExpressionException e = assertThrows(ExpressionException.class, () -> Expression.parse(source));
        assertTrue(e.getMessage().contains(fragment), e.getMessage());
        assertTrue(e.position() >= 0 && e.position() <= source.length(), "position " + e.position());
    }

    @Test
    void evaluationErrorsAreReported() {
        assertTrue(assertThrows(ExpressionException.class, () -> eval("nope + 1")).getMessage().contains("unknown param 'nope'"));
        assertTrue(assertThrows(ExpressionException.class, () -> eval("1 / 0")).getMessage().contains("not a finite number"));
        assertTrue(assertThrows(ExpressionException.class, () -> eval("10 ^ 400")).getMessage().contains("not a finite number"));
    }

    @Test
    void limitsLengthAndNesting() {
        String longSource = "1" + " + 1".repeat(Expression.MAX_LENGTH);
        assertTrue(assertThrows(ExpressionException.class, () -> Expression.parse(longSource)).getMessage().contains("longer than"));
        String deep = "(".repeat(Expression.MAX_DEPTH + 1) + "1" + ")".repeat(Expression.MAX_DEPTH + 1);
        assertTrue(assertThrows(ExpressionException.class, () -> Expression.parse(deep)).getMessage().contains("nested deeper"));
        String ok = "(".repeat(Expression.MAX_DEPTH - 1) + "1" + ")".repeat(Expression.MAX_DEPTH - 1);
        assertEquals(1.0, Expression.parse(ok).evaluate(Map.of()));
        String deepCalls = "floor(".repeat(Expression.MAX_DEPTH + 1) + "1" + ")".repeat(Expression.MAX_DEPTH + 1);
        assertThrows(ExpressionException.class, () -> Expression.parse(deepCalls));
        String deepUnary = "-".repeat(Expression.MAX_DEPTH + 1) + "1";
        assertThrows(ExpressionException.class, () -> Expression.parse(deepUnary));
    }

    /** Random input never throws anything but ExpressionException, and never hangs. */
    @Test
    void fuzzGarbageOnlyThrowsExpressionException() {
        Random random = new Random(24);
        String alphabet = "0123456789.+-*/^()=<>!, tierfactormaxminroundfloorceilif×÷−_ $";
        for (int i = 0; i < 20_000; i++) {
            int length = random.nextInt(40);
            StringBuilder s = new StringBuilder();
            for (int k = 0; k < length; k++) {
                s.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            try {
                double value = Expression.parse(s.toString()).evaluate(P);
                assertTrue(Double.isFinite(value), s.toString());
            } catch (ExpressionException expected) {
                // fine: a clear error
            }
        }
    }

    /** Random well-formed trees: printing and re-parsing gives the same value as evaluating directly. */
    @Test
    void fuzzRandomTreesRoundTrip() {
        Random random = new Random(7);
        for (int i = 0; i < 5_000; i++) {
            Gen gen = randomTree(random, 0);
            double expected;
            try {
                expected = gen.value();
            } catch (ArithmeticException e) {
                continue; // reference itself not finite, skip
            }
            if (!Double.isFinite(expected) || gen.source().length() > Expression.MAX_LENGTH) {
                continue;
            }
            assertEquals(expected, Expression.parse(gen.source()).evaluate(P), Math.abs(expected) * 1e-9 + 1e-9, gen.source());
        }
    }

    private record Gen(String source, double value) {
    }

    private static Gen randomTree(Random random, int depth) {
        int pick = depth > 4 ? random.nextInt(2) : random.nextInt(9);
        return switch (pick) {
            case 0 -> {
                int n = random.nextInt(20);
                yield new Gen(Integer.toString(n), n);
            }
            case 1 -> {
                String name = random.nextBoolean() ? "tier" : "factor";
                yield new Gen(name, P.get(name));
            }
            case 2 -> bin(random, depth, "+", Double::sum);
            case 3 -> bin(random, depth, "-", (a, b) -> a - b);
            case 4 -> bin(random, depth, "*", (a, b) -> a * b);
            case 5 -> {
                Gen a = randomTree(random, depth + 1);
                Gen b = randomTree(random, depth + 1);
                yield new Gen("max(" + a.source() + ", " + b.source() + ")", Math.max(a.value(), b.value()));
            }
            case 6 -> {
                Gen a = randomTree(random, depth + 1);
                yield new Gen("floor(" + a.source() + ")", Math.floor(a.value()));
            }
            case 7 -> {
                Gen c = randomTree(random, depth + 1);
                Gen a = randomTree(random, depth + 1);
                Gen b = randomTree(random, depth + 1);
                yield new Gen("if(" + c.source() + " > 3, " + a.source() + ", " + b.source() + ")", c.value() > 3 ? a.value() : b.value());
            }
            default -> {
                Gen a = randomTree(random, depth + 1);
                yield new Gen("-(" + a.source() + ")", -a.value());
            }
        };
    }

    private static Gen bin(Random random, int depth, String op, java.util.function.DoubleBinaryOperator f) {
        Gen a = randomTree(random, depth + 1);
        Gen b = randomTree(random, depth + 1);
        return new Gen("(" + a.source() + " " + op + " " + b.source() + ")", f.applyAsDouble(a.value(), b.value()));
    }
}
