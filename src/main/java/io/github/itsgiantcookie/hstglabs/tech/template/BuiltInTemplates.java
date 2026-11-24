package io.github.itsgiantcookie.hstglabs.tech.template;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Templates we ship (docs/design.md §8.6). Data kinds only for now; the item, block and network kinds
 * come with the features they make (WP-09, WP-14, WP-18).
 *
 * <ul>
 *   <li>{@code hstg_labs:tiered} (technology): units = base_units × factor^(tier − 1), unit time =
 *       unit_time × tier, one of each pack per unit, {@code labs.min_tier} = lab_tier (defaults to tier),
 *       tree category.</li>
 *   <li>{@code hstg_labs:data_flask} (pack): a data-driven pack from name, colour and tier.</li>
 * </ul>
 */
public final class BuiltInTemplates {

    public static final String TECHNOLOGY_KIND = "hstg_labs:technology";
    public static final String PACK_KIND = "hstg_labs:pack";
    public static final String TIERED = "hstg_labs:tiered";
    public static final String DATA_FLASK = "hstg_labs:data_flask";

    private BuiltInTemplates() {
    }

    public static Map<String, Template> all() {
        Map<String, Template> all = new LinkedHashMap<>();
        all.put(TIERED, new Tiered());
        all.put(DATA_FLASK, dataFlask());
        return all;
    }

    private static Template dataFlask() {
        JsonObject json = JsonParser.parseString("""
              { "kind": "hstg_labs:pack",
                "declare": { "name": "string", "color": "color", "tier": { "type": "int", "default": 1 } },
                "defaults": { "name": "${name}", "color": "${color}", "tier": "${tier}" } }
              """).getAsJsonObject();
        return TemplateJson.parse(DATA_FLASK, json, kind -> true).template().orElseThrow();
    }

    /** The Java curve behind {@code hstg_labs:tiered}. */
    static final class Tiered implements Template {

        private static final Map<String, ParamSpec> DECLARES;

        static {
            Map<String, ParamSpec> d = new LinkedHashMap<>();
            d.put("tier", new ParamSpec("tier", ParamType.INT, Optional.empty()));
            d.put("packs", new ParamSpec("packs", ParamType.ID_LIST, Optional.empty()));
            d.put("base_units", new ParamSpec("base_units", ParamType.INT, Optional.of(TemplateEngine.number(25))));
            d.put("factor", new ParamSpec("factor", ParamType.NUMBER, Optional.of(TemplateEngine.number(2))));
            d.put("unit_time", new ParamSpec("unit_time", ParamType.INT, Optional.of(TemplateEngine.number(200))));
            d.put("category", new ParamSpec("category", ParamType.STRING, Optional.of(new com.google.gson.JsonPrimitive("main"))));
            d.put("lab_tier", new ParamSpec("lab_tier", ParamType.INT, Optional.of(new com.google.gson.JsonPrimitive("=tier"))));
            DECLARES = java.util.Collections.unmodifiableMap(d);
        }

        @Override
        public String id() {
            return TIERED;
        }

        @Override
        public String kind() {
            return TECHNOLOGY_KIND;
        }

        @Override
        public Optional<String> parent() {
            return Optional.empty();
        }

        @Override
        public Map<String, ParamSpec> declares() {
            return DECLARES;
        }

        @Override
        public JsonObject params() {
            return new JsonObject();
        }

        @Override
        public JsonObject body(Map<String, JsonElement> p, Predicate<String> isDataPack) {
            int tier = p.get("tier").getAsInt();
            double units = Math.floor(p.get("base_units").getAsDouble() * Math.pow(p.get("factor").getAsDouble(), tier - 1) + 0.5);
            JsonArray ingredients = new JsonArray();
            for (JsonElement pack : p.get("packs").getAsJsonArray()) {
                String id = pack.getAsString();
                JsonObject entry = new JsonObject();
                boolean data = isDataPack.test(id);
                entry.addProperty("type", data ? "hstg_labs:pack" : "hstg_labs:item");
                entry.addProperty(data ? "pack" : "item", id);
                entry.addProperty("amount", 1);
                ingredients.add(entry);
            }
            JsonObject cost = new JsonObject();
            cost.add("units", TemplateEngine.number(units));
            cost.add("unit_time", TemplateEngine.number(p.get("unit_time").getAsDouble() * tier));
            cost.add("ingredients", ingredients);
            JsonObject labs = new JsonObject();
            labs.add("min_tier", TemplateEngine.number(p.get("lab_tier").getAsDouble()));
            JsonObject tree = new JsonObject();
            tree.addProperty("category", p.get("category").getAsString());
            JsonObject body = new JsonObject();
            body.add("cost", cost);
            body.add("labs", labs);
            body.add("tree", tree);
            return body;
        }
    }
}
