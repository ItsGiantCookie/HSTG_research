package de.germanrangers.hstglabs.tech;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Reads and writes technology files ({@code config/hstg_labs/technologies/<id>.json}, docs/design.md
 * §8.1). Plain Gson, no Minecraft classes, so it is unit-tested directly. Reports every problem in a
 * file at once, each prefixed with the JSON path ({@code cost.units: ...}); a file with any error
 * yields no definition, which disables only that technology.
 */
public final class TechJson {

    public static final String NAMESPACE = "hstg_labs";
    public static final String DEFAULT_REWARD = "hstg_labs:unlock_stage";
    public static final int MAX_UNITS = 1_000_000;
    public static final int MAX_UNIT_TIME = 72_000;
    public static final int MAX_AMOUNT = 1_000_000;

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    private TechJson() {
    }

    /** Result of {@link #parse}: a definition if there were no errors, and every error found. */
    public record Parsed(Optional<TechDefinition> definition, List<String> errors) {

        public Parsed {
            errors = List.copyOf(errors);
        }
    }

    public static Parsed parse(String id, JsonObject json) {
        List<String> errors = new ArrayList<>();
        String stage = string(json, "stage", "stage", errors);

        int units = 0;
        int unitTime = 0;
        List<TypedEntry> ingredients = List.of();
        JsonObject cost = object(json, "cost", "cost", true, errors);
        if (cost != null) {
            units = integer(cost, "units", "cost.units", 1, MAX_UNITS, null, errors);
            unitTime = integer(cost, "unit_time", "cost.unit_time", 1, MAX_UNIT_TIME, null, errors);
            ingredients = entries(cost, "ingredients", "cost.ingredients", true, true, errors);
        }

        int minTier = 1;
        List<TypedEntry> requires = List.of();
        JsonObject labs = object(json, "labs", "labs", false, errors);
        if (labs != null) {
            minTier = integer(labs, "min_tier", "labs.min_tier", 1, Integer.MAX_VALUE, 1, errors);
            requires = entries(labs, "requires", "labs.requires", false, false, errors);
        }

        List<TypedEntry> rewards = json.has("rewards") ? entries(json, "rewards", "rewards", false, false, errors)
              : List.of(new TypedEntry(DEFAULT_REWARD, 0, typeOnly(DEFAULT_REWARD)));
        JsonObject tree = object(json, "tree", "tree", false, errors);
        JsonObject addons = object(json, "addons", "addons", false, errors);

        if (!errors.isEmpty()) {
            return new Parsed(Optional.empty(), errors);
        }
        return new Parsed(Optional.of(new TechDefinition(id, stage, units, unitTime, ingredients, minTier, requires, rewards,
              Optional.ofNullable(tree), addons == null ? new JsonObject() : addons, json)), List.of());
    }

    /** The file as written, keys sorted at every level so files diff cleanly (§6.2). A copy. */
    public static JsonObject write(TechDefinition definition) {
        return sorted(definition.json()).getAsJsonObject();
    }

    /** {@link #write} as pretty-printed text with a final newline. */
    public static String toPrettyString(TechDefinition definition) {
        return PRETTY.toJson(write(definition)) + "\n";
    }

    /** Technology id for a file path relative to the technologies folder, e.g. {@code chem/poly.json}. */
    public static Optional<String> idFromPath(String relativePath) {
        String path = relativePath.replace('\\', '/');
        if (!path.endsWith(".json")) {
            return Optional.empty();
        }
        path = path.substring(0, path.length() - ".json".length());
        return PATH.matcher(path).matches() ? Optional.of(NAMESPACE + ":" + path) : Optional.empty();
    }

    /** Whether {@code s} is a namespaced id ({@code namespace:path}, lower case). */
    public static boolean isId(String s) {
        return ID.matcher(s).matches();
    }

    static JsonElement sorted(JsonElement element) {
        if (element.isJsonObject()) {
            Map<String, JsonElement> keys = new TreeMap<>(element.getAsJsonObject().asMap());
            JsonObject out = new JsonObject();
            keys.forEach((k, v) -> out.add(k, sorted(v)));
            return out;
        }
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            element.getAsJsonArray().forEach(e -> out.add(sorted(e)));
            return out;
        }
        return element.deepCopy();
    }

    private static JsonObject typeOnly(String type) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", type);
        return entry;
    }

    private static String string(JsonObject json, String key, String path, List<String> errors) {
        JsonElement value = json.get(key);
        if (value == null) {
            errors.add(path + ": missing");
            return null;
        }
        if (!isString(value)) {
            errors.add(path + ": must be a string");
            return null;
        }
        return value.getAsString();
    }

    private static JsonObject object(JsonObject json, String key, String path, boolean required, List<String> errors) {
        JsonElement value = json.get(key);
        if (value == null) {
            if (required) {
                errors.add(path + ": missing");
            }
            return null;
        }
        if (!value.isJsonObject()) {
            errors.add(path + ": must be an object");
            return null;
        }
        return value.getAsJsonObject();
    }

    private static int integer(JsonObject json, String key, String path, int min, int max, Integer fallback, List<String> errors) {
        JsonElement value = json.get(key);
        if (value == null) {
            if (fallback == null) {
                errors.add(path + ": missing");
                return 0;
            }
            return fallback;
        }
        if (!(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) || !isWhole(value.getAsJsonPrimitive())) {
            errors.add(path + ": must be a whole number");
            return 0;
        }
        long n = value.getAsLong();
        if (n < min || n > max) {
            errors.add(path + ": must be " + min + (max == Integer.MAX_VALUE ? " or more" : "–" + max) + ", was " + n);
            return 0;
        }
        return (int) n;
    }

    private static List<TypedEntry> entries(JsonObject json, String key, String path, boolean nonEmpty, boolean withAmount, List<String> errors) {
        JsonElement value = json.get(key);
        if (value == null) {
            if (nonEmpty) {
                errors.add(path + ": missing");
            }
            return List.of();
        }
        if (!value.isJsonArray()) {
            errors.add(path + ": must be a list");
            return List.of();
        }
        JsonArray array = value.getAsJsonArray();
        if (nonEmpty && array.isEmpty()) {
            errors.add(path + ": needs at least one entry");
        }
        List<TypedEntry> out = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String at = path + "[" + i + "]";
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) {
                errors.add(at + ": must be an object");
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String type = null;
            JsonElement typeValue = entry.get("type");
            if (typeValue == null || !isString(typeValue)) {
                errors.add(at + ".type: missing or not a string");
            } else if (!isId(typeValue.getAsString())) {
                errors.add(at + ".type: '" + typeValue.getAsString() + "' is not a namespaced id like hstg_labs:item");
            } else {
                type = typeValue.getAsString();
            }
            int amount = withAmount ? integer(entry, "amount", at + ".amount", 1, MAX_AMOUNT, null, errors) : 0;
            if (type != null) {
                out.add(new TypedEntry(type, amount, entry));
            }
        }
        return out;
    }

    private static boolean isString(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static boolean isWhole(JsonPrimitive number) {
        double d = number.getAsDouble();
        return d == Math.rint(d) && !Double.isInfinite(d);
    }
}
