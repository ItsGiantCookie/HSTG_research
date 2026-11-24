package de.germanrangers.hstglabs.tech;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads and writes pack files ({@code config/hstg_labs/packs/<id>.json}, docs/design.md §8.2). Plain
 * Gson, unit-tested without Minecraft; unknown fields survive like in {@link TechJson}.
 */
public final class PackJson {

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    private PackJson() {
    }

    /** Result of {@link #parse}. */
    public record Parsed(Optional<PackDefinition> definition, List<String> errors) {

        public Parsed {
            errors = List.copyOf(errors);
        }
    }

    public static Parsed parse(String id, JsonObject json) {
        List<String> errors = new ArrayList<>();
        String name = null;
        JsonElement nameValue = json.get("name");
        if (!isString(nameValue)) {
            errors.add("name: missing or not a string");
        } else {
            name = nameValue.getAsString();
        }

        int color = 0;
        JsonElement colorValue = json.get("color");
        if (!isString(colorValue) || !HEX.matcher(colorValue.getAsString()).matches()) {
            errors.add("color: must be a hex colour like \"#E04040\"");
        } else {
            color = Integer.parseInt(colorValue.getAsString().substring(1), 16);
        }

        int tier = 1;
        JsonElement tierValue = json.get("tier");
        if (tierValue != null) {
            if (!tierValue.isJsonPrimitive() || !tierValue.getAsJsonPrimitive().isNumber() || tierValue.getAsDouble() != Math.rint(tierValue.getAsDouble())
                  || tierValue.getAsLong() < 1) {
                errors.add("tier: must be a whole number, 1 or more");
            } else {
                tier = tierValue.getAsInt();
            }
        }

        String model = null;
        JsonElement modelValue = json.get("model");
        if (modelValue != null) {
            if (!isString(modelValue) || !TechJson.isId(modelValue.getAsString())) {
                errors.add("model: must be a namespaced id like hstg_labs:item/my_pack");
            } else {
                model = modelValue.getAsString();
            }
        }

        if (!errors.isEmpty()) {
            return new Parsed(Optional.empty(), errors);
        }
        return new Parsed(Optional.of(new PackDefinition(id, name, color, tier, Optional.ofNullable(model), json)), List.of());
    }

    /** The file as written, keys sorted. A copy. */
    public static JsonObject write(PackDefinition pack) {
        return TechJson.sorted(pack.json()).getAsJsonObject();
    }

    private static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }
}
