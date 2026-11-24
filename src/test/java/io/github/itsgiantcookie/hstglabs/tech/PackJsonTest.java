package io.github.itsgiantcookie.hstglabs.tech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** docs/design.md §8.2: data-driven science packs in config/hstg_labs/packs/<id>.json. */
class PackJsonTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void readsAndRoundTrips() {
        JsonObject file = json("""
              { "name": "Logistic Pack", "color": "#E04040", "tier": 2, "model": "hstg_labs:item/pack_logistic", "addons": { "x": 1 } }
              """);
        PackJson.Parsed parsed = PackJson.parse("hstg_labs:logistic", file);
        assertEquals(List.of(), parsed.errors());
        PackDefinition pack = parsed.definition().orElseThrow();
        assertEquals("hstg_labs:logistic", pack.id());
        assertEquals("Logistic Pack", pack.name());
        assertEquals(0xE04040, pack.color());
        assertEquals(2, pack.tier());
        assertEquals(Optional.of("hstg_labs:item/pack_logistic"), pack.model());
        assertEquals(file, PackJson.write(pack), "unknown fields survive");
    }

    @Test
    void defaults() {
        PackDefinition pack = PackJson.parse("hstg_labs:automation", json("{ \"name\": \"Automation Pack\", \"color\": \"#ff0000\" }"))
              .definition().orElseThrow();
        assertEquals(1, pack.tier());
        assertEquals(Optional.empty(), pack.model());
        assertEquals(0xFF0000, pack.color(), "lower-case hex works");
    }

    @Test
    void reportsEveryProblem() {
        PackJson.Parsed parsed = PackJson.parse("hstg_labs:bad", json("{ \"color\": \"red\", \"tier\": 0, \"model\": \"No Id\" }"));
        assertTrue(parsed.definition().isEmpty());
        for (String expected : List.of("name", "color", "tier", "model")) {
            assertTrue(parsed.errors().stream().anyMatch(e -> e.startsWith(expected + ":")), expected + " in " + parsed.errors());
        }
        assertTrue(PackJson.parse("hstg_labs:x", json("{ \"name\": 3, \"color\": 5 }")).errors().size() >= 2);
    }
}
