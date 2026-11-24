package io.github.itsgiantcookie.hstglabs.tech.template;

import com.google.gson.JsonElement;
import io.github.itsgiantcookie.hstglabs.tech.TechJson;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** The param types of templates (docs/design.md §8.6): int, number, color, id, id_list, string. */
public enum ParamType {
    INT,
    NUMBER,
    COLOR,
    ID,
    ID_LIST,
    STRING;

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    public static Optional<ParamType> byName(String name) {
        for (ParamType type : values()) {
            if (type.serializedName().equals(name)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Usable in {@code =} expressions. */
    public boolean numeric() {
        return this == INT || this == NUMBER;
    }

    /** Why {@code value} is not of this type, or empty if it is. */
    public Optional<String> check(JsonElement value) {
        boolean ok = switch (this) {
            case INT -> isNumber(value) && value.getAsDouble() == Math.rint(value.getAsDouble());
            case NUMBER -> isNumber(value);
            case COLOR -> isString(value) && HEX.matcher(value.getAsString()).matches();
            case ID -> isString(value) && TechJson.isId(value.getAsString());
            case ID_LIST -> value.isJsonArray() && value.getAsJsonArray().asList().stream().allMatch(e -> isString(e) && TechJson.isId(e.getAsString()));
            case STRING -> isString(value);
        };
        if (ok) {
            return Optional.empty();
        }
        return Optional.of(switch (this) {
            case INT -> "must be a whole number";
            case NUMBER -> "must be a number";
            case COLOR -> "must be a hex colour like \"#E04040\"";
            case ID -> "must be a namespaced id like mypack:thing";
            case ID_LIST -> "must be a list of namespaced ids";
            case STRING -> "must be a string";
        });
    }

    static boolean isNumber(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
    }

    static boolean isString(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }
}
