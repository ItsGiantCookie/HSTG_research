package io.github.itsgiantcookie.hstglabs.tech;

import com.google.gson.JsonObject;

/**
 * One typed entry of a definition file: an ingredient, a lab requirement or a reward
 * (docs/design.md §8.1). Keeps its raw JSON, so an entry whose type is not loaded survives load and
 * save untouched (§9.6).
 *
 * @param type   the {@code type} id
 * @param amount the {@code amount} for ingredients, 0 for other entries
 * @param raw    the whole entry as written in the file
 */
public record TypedEntry(String type, int amount, JsonObject raw) {

    public TypedEntry {
        raw = raw.deepCopy();
    }

    /** The type's own fields: the raw entry without {@code type} and {@code amount}. */
    public JsonObject fields() {
        JsonObject fields = raw.deepCopy();
        fields.remove("type");
        fields.remove("amount");
        return fields;
    }

    @Override
    public JsonObject raw() {
        return raw.deepCopy();
    }
}
