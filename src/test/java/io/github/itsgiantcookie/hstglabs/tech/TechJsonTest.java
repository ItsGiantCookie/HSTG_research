package io.github.itsgiantcookie.hstglabs.tech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** docs/design.md §8.1 + §9.6: technology files parse, round-trip, and keep data we do not understand. */
class TechJsonTest {

    private static final String FULL = """
          {
            "stage": "polymer_science",
            "cost": {
              "units": 120,
              "unit_time": 600,
              "ingredients": [
                { "type": "hstg_labs:item", "item": "kubejs:automation_pack", "amount": 1 },
                { "type": "hstg_labs:pack", "pack": "hstg_labs:chemical", "amount": 2 },
                { "type": "someaddon:mana", "mana": 500, "amount": 1, "shiny": true }
              ]
            },
            "labs": {
              "min_tier": 2,
              "requires": [
                { "type": "hstg_labs:lab", "lab_tag": "hstg_labs:chemistry", "count": 1 },
                { "type": "someaddon:cleanroom", "size": 5 }
              ]
            },
            "rewards": [ { "type": "hstg_labs:unlock_stage" }, { "type": "someaddon:fireworks", "n": 3 } ],
            "tree": { "category": "chemistry", "x": 6, "y": 2 },
            "addons": { "someaddon": { "colour": "red", "nested": [1, 2, { "a": null }] } },
            "future_field": { "kept": true }
          }
          """;

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static TechDefinition parse(String text) {
        TechJson.Parsed parsed = TechJson.parse("hstg_labs:polymer", json(text));
        assertEquals(List.of(), parsed.errors());
        return parsed.definition().orElseThrow();
    }

    @Test
    void readsEveryField() {
        TechDefinition tech = parse(FULL);
        assertEquals("hstg_labs:polymer", tech.id());
        assertEquals("polymer_science", tech.stage());
        assertEquals(120, tech.units());
        assertEquals(600, tech.unitTime());
        assertEquals(List.of("hstg_labs:item", "hstg_labs:pack", "someaddon:mana"), tech.ingredients().stream().map(TypedEntry::type).toList());
        assertEquals(List.of(1, 2, 1), tech.ingredients().stream().map(TypedEntry::amount).toList());
        assertEquals(2, tech.minTier());
        assertEquals(List.of("hstg_labs:lab", "someaddon:cleanroom"), tech.requires().stream().map(TypedEntry::type).toList());
        assertEquals(List.of("hstg_labs:unlock_stage", "someaddon:fireworks"), tech.rewards().stream().map(TypedEntry::type).toList());
        assertEquals("chemistry", tech.tree().orElseThrow().get("category").getAsString());
        assertTrue(tech.addons().has("someaddon"));
    }

    @Test
    void typedEntryFieldsExcludeTypeAndAmount() {
        TypedEntry mana = parse(FULL).ingredients().get(2);
        assertEquals(json("{ \"mana\": 500, \"shiny\": true }"), mana.fields());
        assertEquals(json("{ \"type\": \"someaddon:mana\", \"mana\": 500, \"amount\": 1, \"shiny\": true }"), mana.raw());
    }

    @Test
    void unknownDataSurvivesARoundTrip() {
        TechDefinition tech = parse(FULL);
        JsonObject written = TechJson.write(tech);
        assertEquals(json(FULL), written, "unknown types, addons and unknown fields come back untouched");
        TechDefinition again = parse(written.toString());
        assertEquals(TechJson.write(again), written, "round trip is stable");
    }

    @Test
    void writeSortsKeysForCleanDiffs() {
        String written = TechJson.toPrettyString(parse(FULL));
        assertTrue(written.indexOf("\"addons\"") < written.indexOf("\"cost\""));
        assertTrue(written.indexOf("\"cost\"") < written.indexOf("\"future_field\""));
        assertTrue(written.indexOf("\"amount\": 1,\n") > 0, "keys inside entries are sorted too: " + written);
        assertTrue(written.endsWith("}\n"));
    }

    @Test
    void writingDoesNotChangeTheDefinition() {
        TechDefinition tech = parse(FULL);
        JsonObject written = TechJson.write(tech);
        written.addProperty("stage", "changed");
        assertEquals("polymer_science", tech.stage(), "write returns a copy");
    }

    @Test
    void defaults() {
        TechDefinition tech = parse("""
              { "stage": "s", "cost": { "units": 1, "unit_time": 1, "ingredients": [ { "type": "hstg_labs:item", "item": "minecraft:paper", "amount": 1 } ] } }
              """);
        assertEquals(1, tech.minTier());
        assertEquals(List.of(), tech.requires());
        assertEquals(List.of("hstg_labs:unlock_stage"), tech.rewards().stream().map(TypedEntry::type).toList(), "default reward");
        assertEquals(Optional.empty(), tech.tree());
        assertEquals(new JsonObject(), tech.addons());
        assertFalse(TechJson.write(tech).has("rewards"), "defaults are not written back");
    }

    @Test
    void reportsEveryProblemInAFile() {
        TechJson.Parsed parsed = TechJson.parse("hstg_labs:bad", json("""
              {
                "cost": { "units": 0, "unit_time": 72001, "ingredients": [ { "amount": 0 }, { "type": "Bad Id", "amount": 1 }, 7 ] },
                "labs": { "min_tier": 0, "requires": [ {} ] },
                "rewards": "nope",
                "addons": []
              }
              """));
        assertTrue(parsed.definition().isEmpty());
        List<String> errors = parsed.errors();
        for (String expected : List.of("stage", "cost.units", "cost.unit_time", "cost.ingredients[0].type", "cost.ingredients[0].amount",
              "cost.ingredients[1].type", "cost.ingredients[2]", "labs.min_tier", "labs.requires[0].type", "rewards", "addons")) {
            assertTrue(errors.stream().anyMatch(e -> e.startsWith(expected + ":")), "no error for " + expected + " in " + errors);
        }
    }

    @Test
    void missingCostAndEmptyIngredients() {
        assertTrue(TechJson.parse("x:a", json("{ \"stage\": \"s\" }")).errors().stream().anyMatch(e -> e.startsWith("cost:")));
        var empty = TechJson.parse("x:a", json("{ \"stage\": \"s\", \"cost\": { \"units\": 1, \"unit_time\": 1, \"ingredients\": [] } }"));
        assertTrue(empty.errors().stream().anyMatch(e -> e.startsWith("cost.ingredients:")), empty.errors().toString());
    }

    @Test
    void wrongJsonTypesAreErrorsNotExceptions() {
        var parsed = TechJson.parse("x:a", json("""
              { "stage": 5, "cost": { "units": "many", "unit_time": 1.5, "ingredients": {} }, "labs": [], "tree": 3 }
              """));
        List<String> errors = parsed.errors();
        for (String expected : List.of("stage", "cost.units", "cost.unit_time", "cost.ingredients", "labs", "tree")) {
            assertTrue(errors.stream().anyMatch(e -> e.startsWith(expected + ":")), "no error for " + expected + " in " + errors);
        }
    }

    @Test
    void idsFromFilePaths() {
        assertEquals(Optional.of("hstg_labs:polymer_science"), TechJson.idFromPath("polymer_science.json"));
        assertEquals(Optional.of("hstg_labs:chemistry/polymers"), TechJson.idFromPath("chemistry/polymers.json"));
        assertEquals(Optional.empty(), TechJson.idFromPath("Bad Name.json"));
        assertEquals(Optional.empty(), TechJson.idFromPath("notes.txt"));
        assertTrue(TechJson.isId("hstg_labs:a/b.c-d_e"));
        assertFalse(TechJson.isId("nocolon"));
        assertFalse(TechJson.isId("UPPER:case"));
    }
}
