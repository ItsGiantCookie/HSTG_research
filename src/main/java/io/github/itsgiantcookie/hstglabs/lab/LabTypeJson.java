package io.github.itsgiantcookie.hstglabs.lab;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.itsgiantcookie.hstglabs.tech.TechJson;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads lab type JSON ({@code data/<ns>/hstg_labs/lab_type/<id>.json}, docs/design.md §5.5) and
 * applies live overrides ({@code config/hstg_labs/lab_types/<id>.json}). Plain Gson, unit-tested
 * without Minecraft. Every problem is reported; any error disables only this lab type (§8.3).
 */
public final class LabTypeJson {

    public static final Set<String> ROLES = Set.of("controller", "consumer", "worker", "node");
    public static final Set<String> UPGRADES = Set.of("speed", "energy");
    public static final int MAX_ITEM_SLOTS = 54;
    public static final int MAX_PARALLEL = 64;
    public static final double MAX_SPEED = 1_000;

    private LabTypeJson() {
    }

    /** Result of {@link #parse}. */
    public record Parsed(Optional<LabTypeDefinition> definition, List<String> errors) {

        public Parsed {
            errors = List.copyOf(errors);
        }
    }

    public static Parsed parse(String id, JsonObject json) {
        List<String> errors = new ArrayList<>();
        Set<String> roles = strings(json, "roles", true, errors, ROLES::contains, "one of controller, consumer, worker, node");
        if (json.has("roles") && roles.isEmpty() && errors.isEmpty()) {
            errors.add("roles: needs at least one role");
        }
        boolean worker = roles.contains("worker");
        int tier = integer(json, "tier", 1, 1, Integer.MAX_VALUE, errors);
        double speed = number(json, "speed", 1.0, errors);
        int parallel = integer(json, "parallel", worker ? 1 : 0, 0, MAX_PARALLEL, errors);
        long energyPerTick = integer(json, "energy_per_tick", 0, 0, Integer.MAX_VALUE, errors);
        long energyCapacity = integer(json, "energy_capacity", (int) Math.min(Integer.MAX_VALUE, energyPerTick * 200), 0, Integer.MAX_VALUE, errors);
        int itemSlots = integer(json, "item_slots", 0, 0, MAX_ITEM_SLOTS, errors);
        Set<String> accepts = strings(json, "accepts", false, errors, TechJson::isId, "a namespaced ingredient type id");
        if (!json.has("accepts")) {
            accepts = Set.of("hstg_labs:item", "hstg_labs:tag", "hstg_labs:pack");
        }
        Set<String> tags = strings(json, "tags", false, errors, TechJson::isId, "a namespaced lab tag");
        Set<String> upgrades = strings(json, "upgrades", false, errors, UPGRADES::contains, "speed or energy");
        int maxConsumers = integer(json, "max_consumers", 8, 1, Integer.MAX_VALUE, errors);
        int maxWorkers = integer(json, "max_workers", 8, 1, Integer.MAX_VALUE, errors);

        if (!(speed > 0) || speed > MAX_SPEED) {
            errors.add("speed: must be above 0 and at most " + (int) MAX_SPEED + ", was " + speed);
        }
        if (roles.contains("consumer") && itemSlots == 0 && errors.stream().noneMatch(e -> e.startsWith("item_slots"))) {
            errors.add("item_slots: a consumer with no storage cannot hold ingredients");
        }
        if (!errors.isEmpty()) {
            return new Parsed(Optional.empty(), errors);
        }
        return new Parsed(Optional.of(new LabTypeDefinition(id, roles, tier, speed, parallel, energyPerTick, energyCapacity, itemSlots, accepts, tags,
              upgrades, maxConsumers, maxWorkers, json)), List.of());
    }

    /** {@code base} with {@code override} merged over it: objects key by key, everything else replaced. A copy. */
    public static JsonObject override(JsonObject base, JsonObject override) {
        JsonObject out = base.deepCopy();
        merge(out, override);
        return out;
    }

    private static void merge(JsonObject target, JsonObject source) {
        for (Map.Entry<String, JsonElement> e : source.entrySet()) {
            JsonElement existing = target.get(e.getKey());
            if (existing != null && existing.isJsonObject() && e.getValue().isJsonObject()) {
                merge(existing.getAsJsonObject(), e.getValue().getAsJsonObject());
            } else {
                target.add(e.getKey(), e.getValue().deepCopy());
            }
        }
    }

    private static int integer(JsonObject json, String key, int fallback, int min, int max, List<String> errors) {
        JsonElement v = json.get(key);
        if (v == null) {
            return fallback;
        }
        if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber() || v.getAsDouble() != Math.rint(v.getAsDouble())) {
            errors.add(key + ": must be a whole number");
            return fallback;
        }
        long n = v.getAsLong();
        if (n < min || n > max) {
            errors.add(key + ": must be " + min + (max == Integer.MAX_VALUE ? " or more" : "–" + max) + ", was " + n);
            return fallback;
        }
        return (int) n;
    }

    private static double number(JsonObject json, String key, double fallback, List<String> errors) {
        JsonElement v = json.get(key);
        if (v == null) {
            return fallback;
        }
        if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) {
            errors.add(key + ": must be a number");
            return fallback;
        }
        return v.getAsDouble();
    }

    private static Set<String> strings(JsonObject json, String key, boolean required, List<String> errors, java.util.function.Predicate<String> valid,
          String expected) {
        JsonElement v = json.get(key);
        if (v == null) {
            if (required) {
                errors.add(key + ": missing");
            }
            return Set.of();
        }
        if (!v.isJsonArray()) {
            errors.add(key + ": must be a list");
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        JsonArray array = v.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement e = array.get(i);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || !valid.test(e.getAsString())) {
                errors.add(key + "[" + i + "]: must be " + expected);
            } else {
                out.add(e.getAsString());
            }
        }
        return out;
    }
}
