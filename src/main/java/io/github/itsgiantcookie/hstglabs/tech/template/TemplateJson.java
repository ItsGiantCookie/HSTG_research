package io.github.itsgiantcookie.hstglabs.tech.template;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.itsgiantcookie.hstglabs.tech.TechJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Reads template files ({@code config/hstg_labs/templates/**.json}, docs/design.md §8.6):
 * <pre>
 * { "kind": "hstg_labs:technology",
 *   "extends": "hstg_labs:tiered",                       optional
 *   "declare": { "tier": "int",                          typed params (int, number, color, id, id_list, string)
 *                "category": { "type": "string", "default": "main" },
 *                "lab_tier": { "type": "int", "default": "=tier" } },
 *   "params": { "base_units": 30 },                      values for own or inherited params
 *   "defaults": { ... }                                  the definition, with ${param} and "=expr" values }
 * </pre>
 * Plain Gson, unit-tested without Minecraft.
 */
public final class TemplateJson {

    private static final Pattern PARAM_NAME = Pattern.compile("[a-z_][a-z0-9_]*");

    private TemplateJson() {
    }

    /** Result of {@link #parse}. */
    public record Parsed(Optional<Template> template, List<String> errors) {

        public Parsed {
            errors = List.copyOf(errors);
        }
    }

    public static Parsed parse(String id, JsonObject json, Predicate<String> knownKind) {
        List<String> errors = new ArrayList<>();
        String kind = null;
        JsonElement kindValue = json.get("kind");
        if (kindValue == null || !ParamType.isString(kindValue)) {
            errors.add("kind: missing or not a string");
        } else if (!knownKind.test(kindValue.getAsString())) {
            errors.add("kind: unknown template kind '" + kindValue.getAsString() + "'");
        } else {
            kind = kindValue.getAsString();
        }

        String parent = null;
        JsonElement extendsValue = json.get("extends");
        if (extendsValue != null) {
            if (!ParamType.isString(extendsValue) || !TechJson.isId(extendsValue.getAsString())) {
                errors.add("extends: must be a template id");
            } else {
                parent = extendsValue.getAsString();
            }
        }

        Map<String, Template.ParamSpec> declares = new LinkedHashMap<>();
        JsonElement declare = json.get("declare");
        if (declare != null) {
            if (!declare.isJsonObject()) {
                errors.add("declare: must be an object");
            } else {
                declare.getAsJsonObject().entrySet().forEach(e -> spec(e.getKey(), e.getValue(), errors).ifPresent(s -> declares.put(s.name(), s)));
            }
        }

        JsonObject params = object(json, "params", errors);
        JsonObject defaults = object(json, "defaults", errors);
        if (!errors.isEmpty()) {
            return new Parsed(Optional.empty(), errors);
        }
        return new Parsed(Optional.of(new JsonTemplate(id, kind, Optional.ofNullable(parent), declares, params, defaults)), List.of());
    }

    private static Optional<Template.ParamSpec> spec(String name, JsonElement value, List<String> errors) {
        String at = "declare." + name;
        if (!PARAM_NAME.matcher(name).matches()) {
            errors.add(at + ": param names are lower case [a-z0-9_] and start with a letter or _");
            return Optional.empty();
        }
        JsonElement typeValue = value;
        JsonElement defaultValue = null;
        if (value.isJsonObject()) {
            typeValue = value.getAsJsonObject().get("type");
            defaultValue = value.getAsJsonObject().get("default");
        }
        if (typeValue == null || !ParamType.isString(typeValue)) {
            errors.add(at + ": needs a type (int, number, color, id, id_list or string)");
            return Optional.empty();
        }
        Optional<ParamType> type = ParamType.byName(typeValue.getAsString());
        if (type.isEmpty()) {
            errors.add(at + ": unknown type '" + typeValue.getAsString() + "', expected int, number, color, id, id_list or string");
            return Optional.empty();
        }
        if (defaultValue != null && !isExpression(defaultValue)) {
            Optional<String> problem = type.get().check(defaultValue);
            if (problem.isPresent()) {
                errors.add(at + ": default " + problem.get());
                return Optional.empty();
            }
        }
        if (defaultValue != null && isExpression(defaultValue) && !type.get().numeric()) {
            errors.add(at + ": only int and number params may default to an = expression");
            return Optional.empty();
        }
        return Optional.of(new Template.ParamSpec(name, type.get(), Optional.ofNullable(defaultValue)));
    }

    static boolean isExpression(JsonElement value) {
        return ParamType.isString(value) && value.getAsString().startsWith("=");
    }

    private static JsonObject object(JsonObject json, String key, List<String> errors) {
        JsonElement value = json.get(key);
        if (value == null) {
            return new JsonObject();
        }
        if (!value.isJsonObject()) {
            errors.add(key + ": must be an object");
            return new JsonObject();
        }
        return value.getAsJsonObject();
    }

    /** A template read from a file. */
    record JsonTemplate(String id, String kind, Optional<String> parent, Map<String, ParamSpec> declares, JsonObject params,
          JsonObject defaults) implements Template {

        JsonTemplate {
            declares = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(declares));
            params = params.deepCopy();
            defaults = defaults.deepCopy();
        }

        @Override
        public JsonObject params() {
            return params.deepCopy();
        }

        @Override
        public JsonObject body(Map<String, JsonElement> values, Predicate<String> isDataPack) {
            return defaults.deepCopy();
        }
    }
}
