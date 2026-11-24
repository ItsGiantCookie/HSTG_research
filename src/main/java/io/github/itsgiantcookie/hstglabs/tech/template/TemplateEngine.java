package io.github.itsgiantcookie.hstglabs.tech.template;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.itsgiantcookie.hstglabs.core.expr.Expression;
import io.github.itsgiantcookie.hstglabs.core.expr.ExpressionException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves template instances (docs/design.md §8.6, D24, D27). An instance is a definition with
 * {@code "template"}, optional {@code "params"} and {@code "overrides"}; it stays linked, so a changed
 * template changes every instance on the next load.
 *
 * <p>Order, later wins field by field: root template → each {@code extends} child → params
 * ({@code ${param}} and {@code =expr} substituted) → the instance's own fields → {@code overrides}.
 * Objects merge key by key; arrays and plain values are replaced whole. Every resulting value
 * remembers where it came from ({@link Origin}), for the dev UI's origin badges.
 */
public final class TemplateEngine {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-z_][a-z0-9_]*)}");
    private static final Set<String> LINK_FIELDS = Set.of("template", "params", "overrides");

    private final Map<String, Template> templates;
    private final Predicate<String> isDataPack;

    public TemplateEngine(Map<String, Template> templates, Predicate<String> isDataPack) {
        this.templates = Map.copyOf(templates);
        this.isDataPack = isDataPack;
    }

    /** Whether {@code json} is a template instance rather than a plain definition. */
    public static boolean isInstance(JsonObject json) {
        return json.has("template");
    }

    /** Where one resolved value came from. */
    public record Origin(Source source, String detail) {

        public enum Source {
            /** A template's {@code defaults} (detail: template id). */
            TEMPLATE,
            /** A {@code ${param}} or {@code =expr} (detail: the source text). */
            PARAM,
            /** The instance file itself (detail: "file"). */
            INSTANCE,
            /** The instance's {@code overrides} (detail: "overrides"). */
            OVERRIDE
        }
    }

    /**
     * Outcome of {@link #resolve}.
     *
     * @param json     the plain definition, if there were no errors
     * @param errors   each prefixed with its path; any error leaves {@code json} empty
     * @param warnings each prefixed with its path
     * @param origins  JSON path ({@code cost.units}, {@code c[0]}) → origin
     */
    public record Resolution(Optional<JsonObject> json, List<String> errors, List<String> warnings, Map<String, Origin> origins) {

        public Resolution {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
            origins = Collections.unmodifiableMap(new TreeMap<>(origins));
        }

        static Resolution failed(List<String> errors, List<String> warnings) {
            return new Resolution(Optional.empty(), errors, warnings, Map.of());
        }
    }

    public Resolution resolve(String expectedKind, JsonObject instance) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        JsonElement templateValue = instance.get("template");
        if (templateValue == null || !ParamType.isString(templateValue)) {
            return Resolution.failed(List.of("template: must be a template id"), warnings);
        }
        List<Template> chain = chain(templateValue.getAsString(), errors);
        if (!errors.isEmpty()) {
            return Resolution.failed(errors, warnings);
        }
        for (Template t : chain) {
            if (!t.kind().equals(expectedKind)) {
                errors.add("template: " + t.id() + " is of kind " + t.kind() + ", expected " + expectedKind);
            }
        }
        if (!errors.isEmpty()) {
            return Resolution.failed(errors, warnings);
        }
        Template leaf = chain.get(chain.size() - 1);

        Map<String, Template.ParamSpec> specs = new LinkedHashMap<>();
        Map<String, String> declaredBy = new LinkedHashMap<>();
        for (Template t : chain) {
            t.declares().forEach((name, spec) -> {
                specs.put(name, spec);
                declaredBy.put(name, t.id());
            });
        }

        Map<String, JsonElement> raw = new LinkedHashMap<>();
        specs.forEach((name, spec) -> spec.defaultValue().ifPresent(v -> raw.put(name, v)));
        for (Template t : chain) {
            t.params().entrySet().forEach(e -> {
                if (specs.containsKey(e.getKey())) {
                    raw.put(e.getKey(), e.getValue());
                } else {
                    warnings.add("params." + e.getKey() + ": unknown param in template " + t.id());
                }
            });
        }
        JsonElement instanceParams = instance.get("params");
        if (instanceParams != null && !instanceParams.isJsonObject()) {
            errors.add("params: must be an object");
        } else if (instanceParams != null) {
            instanceParams.getAsJsonObject().entrySet().forEach(e -> {
                if (specs.containsKey(e.getKey())) {
                    raw.put(e.getKey(), e.getValue());
                } else {
                    warnings.add("params." + e.getKey() + ": unknown param, " + leaf.id() + " does not declare it");
                }
            });
        }

        Map<String, JsonElement> values = params(specs, declaredBy, raw, errors);
        if (!errors.isEmpty()) {
            return Resolution.failed(errors, warnings);
        }

        JsonObject result = new JsonObject();
        Map<String, Origin> origins = new LinkedHashMap<>();
        for (Template t : chain) {
            merge(result, t.body(values, isDataPack), "", new Origin(Origin.Source.TEMPLATE, t.id()), origins);
        }
        JsonElement substituted = substitute(result, "", values, origins, errors);

        JsonObject own = instance.deepCopy();
        LINK_FIELDS.forEach(own::remove);
        JsonObject out = substituted.getAsJsonObject();
        merge(out, own, "", new Origin(Origin.Source.INSTANCE, "file"), origins);
        JsonElement overrides = instance.get("overrides");
        if (overrides != null && !overrides.isJsonObject()) {
            errors.add("overrides: must be an object");
        } else if (overrides != null) {
            merge(out, overrides.getAsJsonObject(), "", new Origin(Origin.Source.OVERRIDE, "overrides"), origins);
        }
        if (!errors.isEmpty()) {
            return Resolution.failed(errors, warnings);
        }
        return new Resolution(Optional.of(out), List.of(), warnings, origins);
    }

    /** Root first; refuses unknown templates and extends cycles. */
    private List<Template> chain(String leafId, List<String> errors) {
        List<String> seen = new ArrayList<>();
        List<Template> chain = new ArrayList<>();
        String id = leafId;
        while (id != null) {
            if (seen.contains(id)) {
                seen.add(id);
                errors.add("template: extends cycle: " + String.join(" -> ", seen.subList(seen.indexOf(id), seen.size())));
                return List.of();
            }
            seen.add(id);
            Template t = templates.get(id);
            if (t == null) {
                errors.add("template: unknown template '" + id + "'" + (id.equals(leafId) ? "" : " (extended by " + seen.get(seen.size() - 2) + ")"));
                return List.of();
            }
            chain.add(0, t);
            id = t.parent().orElse(null);
        }
        return chain;
    }

    /** Type-checks values and evaluates {@code =expr} values (defaults may refer to other params). */
    private static Map<String, JsonElement> params(Map<String, Template.ParamSpec> specs, Map<String, String> declaredBy,
          Map<String, JsonElement> raw, List<String> errors) {
        Map<String, JsonElement> values = new LinkedHashMap<>();
        Map<String, Expression> pending = new LinkedHashMap<>();
        for (Map.Entry<String, Template.ParamSpec> e : specs.entrySet()) {
            String name = e.getKey();
            JsonElement value = raw.get(name);
            if (value == null) {
                errors.add("params." + name + ": missing (required by " + declaredBy.get(name) + ")");
            } else if (TemplateJson.isExpression(value) && e.getValue().type().numeric()) {
                try {
                    pending.put(name, Expression.parse(value.getAsString().substring(1)));
                } catch (ExpressionException ex) {
                    errors.add("params." + name + ": " + ex.getMessage());
                }
            } else {
                e.getValue().type().check(value).ifPresentOrElse(problem -> errors.add("params." + name + ": " + problem), () -> values.put(name, value));
            }
        }
        while (!pending.isEmpty() && errors.isEmpty()) {
            boolean progress = false;
            for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Expression> e = it.next();
                if (!values.keySet().containsAll(e.getValue().params())) {
                    continue;
                }
                try {
                    double v = e.getValue().evaluate(numbers(values, e.getValue().params(), specs));
                    JsonPrimitive number = number(v);
                    specs.get(e.getKey()).type().check(number).ifPresentOrElse(p -> errors.add("params." + e.getKey() + ": " + p),
                          () -> values.put(e.getKey(), number));
                } catch (ExpressionException ex) {
                    errors.add("params." + e.getKey() + ": " + ex.getMessage());
                }
                it.remove();
                progress = true;
            }
            if (!progress) {
                List<String> unresolved = new ArrayList<>(pending.keySet());
                for (String name : unresolved) {
                    for (String ref : pending.get(name).params()) {
                        if (!specs.containsKey(ref)) {
                            errors.add("params." + name + ": unknown param '" + ref + "'");
                        }
                    }
                }
                if (errors.isEmpty()) {
                    errors.add("params." + String.join(", params.", unresolved) + ": defaults depend on each other");
                }
            }
        }
        return values;
    }

    private static Map<String, Double> numbers(Map<String, JsonElement> values, Set<String> names, Map<String, Template.ParamSpec> specs) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String name : names) {
            JsonElement value = values.get(name);
            Template.ParamSpec spec = specs.get(name);
            if (value == null || spec == null) {
                continue; // reported by the evaluator as unknown
            }
            if (!spec.type().numeric() || !ParamType.isNumber(value)) {
                throw new ExpressionException("param '" + name + "' is not a number", 0);
            }
            out.put(name, value.getAsDouble());
        }
        return out;
    }

    private JsonElement substitute(JsonElement element, String path, Map<String, JsonElement> values, Map<String, Origin> origins, List<String> errors) {
        if (element.isJsonObject()) {
            JsonObject out = new JsonObject();
            element.getAsJsonObject().entrySet().forEach(e -> out.add(e.getKey(), substitute(e.getValue(), join(path, e.getKey()), values, origins, errors)));
            return out;
        }
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                out.add(substitute(array.get(i), path + "[" + i + "]", values, origins, errors));
            }
            return out;
        }
        if (!ParamType.isString(element)) {
            return element;
        }
        String text = element.getAsString();
        if (text.startsWith("=")) {
            origins.put(path, new Origin(Origin.Source.PARAM, text));
            try {
                Expression expression = Expression.parse(text.substring(1));
                Map<String, Double> numbers = new LinkedHashMap<>();
                for (String name : expression.params()) {
                    JsonElement value = values.get(name);
                    if (value != null && !ParamType.isNumber(value)) {
                        throw new ExpressionException("param '" + name + "' is not a number", 0);
                    }
                    if (value != null) {
                        numbers.put(name, value.getAsDouble());
                    }
                }
                return number(expression.evaluate(numbers));
            } catch (ExpressionException ex) {
                errors.add(path + ": " + ex.getMessage());
                return element;
            }
        }
        Matcher whole = PLACEHOLDER.matcher(text);
        if (whole.matches()) {
            origins.put(path, new Origin(Origin.Source.PARAM, text));
            JsonElement value = values.get(whole.group(1));
            if (value == null) {
                errors.add(path + ": unknown param '" + whole.group(1) + "'");
                return element;
            }
            return value.deepCopy();
        }
        if (!text.contains("${")) {
            return element;
        }
        origins.put(path, new Origin(Origin.Source.PARAM, text));
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            JsonElement value = values.get(m.group(1));
            if (value == null) {
                errors.add(path + ": unknown param '" + m.group(1) + "'");
                return element;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(asText(value)));
        }
        m.appendTail(out);
        return new JsonPrimitive(out.toString());
    }

    private static String asText(JsonElement value) {
        if (ParamType.isNumber(value)) {
            double d = value.getAsDouble();
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
        }
        return ParamType.isString(value) ? value.getAsString() : value.toString();
    }

    static JsonPrimitive number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? new JsonPrimitive((long) value) : new JsonPrimitive(value);
    }

    /** Merges {@code source} into {@code target}: objects key by key, everything else replaced; records origins of the new leaves. */
    static void merge(JsonObject target, JsonObject source, String path, Origin origin, Map<String, Origin> origins) {
        for (Map.Entry<String, JsonElement> e : source.entrySet()) {
            String at = join(path, e.getKey());
            JsonElement existing = target.get(e.getKey());
            if (existing != null && existing.isJsonObject() && e.getValue().isJsonObject()) {
                merge(existing.getAsJsonObject(), e.getValue().getAsJsonObject(), at, origin, origins);
            } else {
                origins.keySet().removeIf(k -> k.equals(at) || k.startsWith(at + ".") || k.startsWith(at + "["));
                target.add(e.getKey(), e.getValue().deepCopy());
                mark(e.getValue(), at, origin, origins);
            }
        }
    }

    private static void mark(JsonElement value, String path, Origin origin, Map<String, Origin> origins) {
        if (value.isJsonObject() && !value.getAsJsonObject().isEmpty()) {
            value.getAsJsonObject().entrySet().forEach(e -> mark(e.getValue(), join(path, e.getKey()), origin, origins));
        } else {
            origins.put(path, origin);
        }
    }

    private static String join(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }
}
