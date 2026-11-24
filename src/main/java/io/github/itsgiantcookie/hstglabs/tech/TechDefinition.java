package io.github.itsgiantcookie.hstglabs.tech;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;

/**
 * One technology file as written (docs/design.md §8.1), parsed but not yet resolved against the
 * registries. Holds the complete raw JSON; {@link TechJson#write} writes exactly that back, so
 * {@code addons}, unknown types and unknown fields survive (§9.6).
 *
 * @param id          technology id, from the file path
 * @param stage       HS stage id
 * @param units       U
 * @param unitTime    T in ticks at speed 1
 * @param ingredients one unit's ingredient set
 * @param minTier     {@code labs.min_tier}
 * @param requires    {@code labs.requires}
 * @param rewards     rewards; {@code [hstg_labs:unlock_stage]} when the file has none
 * @param tree        layout for the dev UI and tree view
 * @param addons      raw per-addon data
 * @param json        the whole file
 */
public record TechDefinition(String id, String stage, int units, int unitTime, List<TypedEntry> ingredients, int minTier,
      List<TypedEntry> requires, List<TypedEntry> rewards, Optional<JsonObject> tree, JsonObject addons, JsonObject json) {

    public TechDefinition {
        ingredients = List.copyOf(ingredients);
        requires = List.copyOf(requires);
        rewards = List.copyOf(rewards);
        tree = tree.map(JsonObject::deepCopy);
        addons = addons.deepCopy();
        json = json.deepCopy();
    }

    @Override
    public JsonObject addons() {
        return addons.deepCopy();
    }

    @Override
    public JsonObject json() {
        return json.deepCopy();
    }

    /**
     * The same definition, written back as {@code file}. For a template instance the fields come from
     * the resolved template, but the file keeps its link ({@code template}, {@code params},
     * {@code overrides}, D27).
     */
    public TechDefinition withJson(JsonObject file) {
        return new TechDefinition(id, stage, units, unitTime, ingredients, minTier, requires, rewards, tree, addons, file);
    }
}
