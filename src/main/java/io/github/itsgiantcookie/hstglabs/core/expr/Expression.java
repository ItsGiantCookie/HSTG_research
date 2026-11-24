package io.github.itsgiantcookie.hstglabs.core.expr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The small expression language of JSON templates (docs/design.md §8.6, decision D24). A template
 * value written as {@code "=base_units * factor^(tier - 1)"} is evaluated once per resolution.
 *
 * <p>Grammar, lowest precedence first:
 * <pre>
 * comparison := sum (('==' | '!=' | '<' | '<=' | '>' | '>=') sum)?    1 or 0
 * sum        := product (('+' | '-' | '−') product)*
 * product    := unary (('*' | '×' | '/' | '÷') unary)*
 * unary      := ('-' | '−') unary | power
 * power      := primary ('^' unary)?                                   right-associative
 * primary    := number | param | function '(' args ')' | '(' comparison ')'
 * functions  := min(a, ...), max(a, ...), round(a), floor(a), ceil(a), if(cond, a, b)
 * </pre>
 * Numbers only, no loops, no assignment, no side effects; {@code if} evaluates only the branch taken.
 * Sources are limited to {@link #MAX_LENGTH} characters and {@link #MAX_DEPTH} levels of nesting,
 * and every intermediate result must be a finite number.
 */
public final class Expression {

    public static final int MAX_LENGTH = 256;
    public static final int MAX_DEPTH = 32;

    private final String source;
    private final Node root;
    private final Set<String> params;

    private Expression(String source, Node root, Set<String> params) {
        this.source = source;
        this.root = root;
        this.params = Collections.unmodifiableSet(params);
    }

    public static Expression parse(String source) {
        if (source.length() > MAX_LENGTH) {
            throw new ExpressionException("expression is longer than " + MAX_LENGTH + " characters", MAX_LENGTH);
        }
        Parser parser = new Parser(source);
        Node root = parser.parse();
        return new Expression(source, root, parser.params);
    }

    /** Evaluates with these param values; every param the expression names must be present. */
    public double evaluate(Map<String, Double> values) {
        return root.eval(values);
    }

    /** Param names the expression refers to. */
    public Set<String> params() {
        return params;
    }

    public String source() {
        return source;
    }

    // ---- tree ----

    private interface Node {
        double eval(Map<String, Double> values);
    }

    private record Num(double value) implements Node {
        @Override
        public double eval(Map<String, Double> values) {
            return value;
        }
    }

    private record Param(String name, int position) implements Node {
        @Override
        public double eval(Map<String, Double> values) {
            Double value = values.get(name);
            if (value == null) {
                throw new ExpressionException("unknown param '" + name + "'", position);
            }
            return value;
        }
    }

    private record Binary(char op, Node left, Node right, int position) implements Node {
        @Override
        public double eval(Map<String, Double> values) {
            double a = left.eval(values);
            double b = right.eval(values);
            double r = switch (op) {
                case '+' -> a + b;
                case '-' -> a - b;
                case '*' -> a * b;
                case '/' -> a / b;
                case '^' -> Math.pow(a, b);
                case '=' -> a == b ? 1 : 0;
                case '!' -> a != b ? 1 : 0;
                case '<' -> a < b ? 1 : 0;
                case 'l' -> a <= b ? 1 : 0;
                case '>' -> a > b ? 1 : 0;
                default -> a >= b ? 1 : 0; // 'g'
            };
            return finite(r, position);
        }
    }

    private record Negate(Node operand) implements Node {
        @Override
        public double eval(Map<String, Double> values) {
            return -operand.eval(values);
        }
    }

    private record Call(String name, List<Node> args, int position) implements Node {
        @Override
        public double eval(Map<String, Double> values) {
            switch (name) {
                case "if" -> {
                    return args.get(0).eval(values) != 0 ? args.get(1).eval(values) : args.get(2).eval(values);
                }
                case "min", "max" -> {
                    double r = args.get(0).eval(values);
                    for (int i = 1; i < args.size(); i++) {
                        double v = args.get(i).eval(values);
                        r = name.equals("min") ? Math.min(r, v) : Math.max(r, v);
                    }
                    return r;
                }
                case "round" -> {
                    return Math.floor(args.get(0).eval(values) + 0.5);
                }
                case "floor" -> {
                    return Math.floor(args.get(0).eval(values));
                }
                default -> {
                    return Math.ceil(args.get(0).eval(values)); // "ceil"
                }
            }
        }
    }

    private static double finite(double value, int position) {
        if (!Double.isFinite(value)) {
            throw new ExpressionException("result is not a finite number (division by zero or overflow)", position);
        }
        return value;
    }

    // ---- parser ----

    private static final class Parser {

        private final String src;
        private final Set<String> params = new TreeSet<>();
        private int pos;
        private int depth;

        Parser(String src) {
            this.src = src;
        }

        Node parse() {
            skipSpaces();
            if (pos == src.length()) {
                throw new ExpressionException("empty expression", 0);
            }
            Node node = comparison();
            skipSpaces();
            if (pos < src.length()) {
                char c = src.charAt(pos);
                boolean known = Character.isLetterOrDigit(c) || "+-−*×/÷^=<>!,()._".indexOf(c) >= 0;
                throw new ExpressionException((known ? "unexpected '" : "unexpected character '") + c + "'", pos);
            }
            return node;
        }

        private Node comparison() {
            Node left = sum();
            skipSpaces();
            int at = pos;
            char op = 0;
            if (match("==")) {
                op = '=';
            } else if (match("!=")) {
                op = '!';
            } else if (match("<=")) {
                op = 'l';
            } else if (match(">=")) {
                op = 'g';
            } else if (match("<")) {
                op = '<';
            } else if (match(">")) {
                op = '>';
            }
            if (op == 0) {
                return left;
            }
            return new Binary(op, left, sum(), at);
        }

        private Node sum() {
            Node left = product();
            while (true) {
                skipSpaces();
                int at = pos;
                if (match("+")) {
                    left = new Binary('+', left, product(), at);
                } else if (match("-") || match("−")) {
                    left = new Binary('-', left, product(), at);
                } else {
                    return left;
                }
            }
        }

        private Node product() {
            Node left = unary();
            while (true) {
                skipSpaces();
                int at = pos;
                if (match("*") || match("×")) {
                    left = new Binary('*', left, unary(), at);
                } else if (match("/") || match("÷")) {
                    left = new Binary('/', left, unary(), at);
                } else {
                    return left;
                }
            }
        }

        private Node unary() {
            skipSpaces();
            if (match("-") || match("−")) {
                enter();
                Node operand = unary();
                depth--;
                return new Negate(operand);
            }
            return power();
        }

        private Node power() {
            Node base = primary();
            skipSpaces();
            int at = pos;
            if (match("^")) {
                enter();
                Node exponent = unary();
                depth--;
                return new Binary('^', base, exponent, at);
            }
            return base;
        }

        private Node primary() {
            skipSpaces();
            if (pos >= src.length()) {
                throw new ExpressionException("expected a number, param, function or '(' but the expression ended", pos);
            }
            char c = src.charAt(pos);
            if (c == '(') {
                pos++;
                enter();
                Node inner = comparison();
                depth--;
                expect(')');
                return inner;
            }
            if (Character.isDigit(c) || c == '.') {
                return number();
            }
            if (Character.isLetter(c) || c == '_') {
                int start = pos;
                while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
                    pos++;
                }
                String name = src.substring(start, pos);
                skipSpaces();
                if (pos < src.length() && src.charAt(pos) == '(') {
                    return call(name, start);
                }
                params.add(name);
                return new Param(name, start);
            }
            if ("+-−*×/÷^=<>!,)".indexOf(c) >= 0) {
                throw new ExpressionException("expected a number, param, function or '(' but found '" + c + "'", pos);
            }
            throw new ExpressionException("unexpected character '" + c + "'", pos);
        }

        private Node number() {
            int start = pos;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                pos++;
            }
            String text = src.substring(start, pos);
            if (!text.matches("\\d+(\\.\\d+)?|\\.\\d+")) {
                throw new ExpressionException("malformed number '" + text + "'", start);
            }
            return new Num(Double.parseDouble(text));
        }

        private Node call(String name, int start) {
            int[] arity = switch (name) {
                case "min", "max" -> new int[] {1, Integer.MAX_VALUE};
                case "round", "floor", "ceil" -> new int[] {1, 1};
                case "if" -> new int[] {3, 3};
                default -> throw new ExpressionException("unknown function '" + name + "'", start);
            };
            expect('(');
            enter();
            List<Node> args = new ArrayList<>();
            skipSpaces();
            if (pos < src.length() && src.charAt(pos) == ')') {
                pos++;
            } else {
                args.add(comparison());
                skipSpaces();
                while (match(",")) {
                    args.add(comparison());
                    skipSpaces();
                }
                expect(')');
            }
            depth--;
            if (args.size() < arity[0] || args.size() > arity[1]) {
                String need = arity[0] == arity[1] ? "exactly " + arity[0] : "at least " + arity[0];
                throw new ExpressionException(name + " needs " + need + " argument(s), got " + args.size(), start);
            }
            return new Call(name, List.copyOf(args), start);
        }

        private void enter() {
            if (++depth > MAX_DEPTH) {
                throw new ExpressionException("expression is nested deeper than " + MAX_DEPTH + " levels", pos);
            }
        }

        private void expect(char c) {
            skipSpaces();
            if (pos >= src.length() || src.charAt(pos) != c) {
                throw new ExpressionException("expected '" + c + "'", pos);
            }
            pos++;
        }

        private boolean match(String token) {
            if (src.startsWith(token, pos)) {
                pos += token.length();
                return true;
            }
            return false;
        }

        private void skipSpaces() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }
    }
}
