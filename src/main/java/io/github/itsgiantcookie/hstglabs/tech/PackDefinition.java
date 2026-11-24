package io.github.itsgiantcookie.hstglabs.tech;

import com.google.gson.JsonObject;
import java.util.Optional;

/**
 * One data-driven science pack (docs/design.md §8.2), shown as {@code hstg_labs:science_pack} with
 * the data component {@code hstg_labs:pack} naming {@link #id()}.
 *
 * @param id    pack id, from the file path
 * @param name  display name
 * @param color tint as 0xRRGGBB
 * @param tier  pack tier, 1 or more
 * @param model optional item model id; the tinted default model is used when empty
 * @param json  the whole file, written back as is
 */
public record PackDefinition(String id, String name, int color, int tier, Optional<String> model, JsonObject json) {

    public PackDefinition {
        json = json.deepCopy();
    }

    @Override
    public JsonObject json() {
        return json.deepCopy();
    }
}
