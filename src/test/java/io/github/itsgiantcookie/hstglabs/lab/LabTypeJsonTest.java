package io.github.itsgiantcookie.hstglabs.lab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** docs/design.md §5.5, D17: lab types are data; config overrides change single fields; unknown data survives. */
class LabTypeJsonTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void readsABasicLab() {
        LabTypeJson.Parsed parsed = LabTypeJson.parse("hstg_labs:basic_lab", json("""
              { "roles": ["controller", "consumer", "worker"], "tier": 1, "speed": 1.0, "parallel": 1,
                "energy_per_tick": 20, "energy_capacity": 40000, "item_slots": 4,
                "accepts": ["hstg_labs:item", "hstg_labs:tag", "hstg_labs:pack"], "upgrades": ["speed", "energy"],
                "addons": { "x": 1 } }
              """));
        assertEquals(List.of(), parsed.errors());
        LabTypeDefinition t = parsed.definition().orElseThrow();
        assertEquals("hstg_labs:basic_lab", t.id());
        assertEquals(Set.of("controller", "consumer", "worker"), t.roles());
        assertEquals(1, t.tier());
        assertEquals(1.0, t.speed());
        assertEquals(1, t.parallel());
        assertEquals(20, t.energyPerTick());
        assertEquals(40_000, t.energyCapacity());
        assertEquals(4, t.itemSlots());
        assertEquals(Set.of("hstg_labs:item", "hstg_labs:tag", "hstg_labs:pack"), t.accepts());
        assertEquals(Set.of("speed", "energy"), t.upgrades());
        assertEquals(Set.of(), t.tags());
        assertTrue(t.json().has("addons"), "unknown and addon data kept");
    }

    @Test
    void defaults() {
        LabTypeDefinition t = LabTypeJson.parse("x:server", json("{ \"roles\": [\"worker\", \"node\"], \"tags\": [\"hstg_labs:simulation\"] }"))
              .definition().orElseThrow();
        assertEquals(1, t.tier());
        assertEquals(1.0, t.speed());
        assertEquals(1, t.parallel(), "a worker defaults to one slot");
        assertEquals(0, t.energyPerTick(), "D6: energy optional, default 0");
        assertEquals(0, t.energyCapacity());
        assertEquals(0, t.itemSlots());
        assertEquals(Set.of("hstg_labs:simulation"), t.tags());
        assertEquals(0, LabTypeJson.parse("x:c", json("{ \"roles\": [\"consumer\"], \"item_slots\": 2 }")).definition().orElseThrow().parallel(),
              "non-workers have no slots");
        assertEquals(400, LabTypeJson.parse("x:p", json("{ \"roles\": [\"worker\"], \"energy_per_tick\": 2 }")).definition().orElseThrow()
              .energyCapacity(), "capacity defaults to 200 ticks of energy");
    }

    @Test
    void errorsDisableOnlyThisLabType() {
        LabTypeJson.Parsed parsed = LabTypeJson.parse("x:bad", json("""
              { "roles": ["chef"], "tier": 0, "speed": 0, "parallel": -1, "energy_per_tick": -5, "item_slots": 100,
                "tags": ["Bad Tag"], "accepts": [3], "upgrades": ["turbo"] }
              """));
        assertTrue(parsed.definition().isEmpty());
        for (String path : List.of("roles", "tier", "speed", "parallel", "energy_per_tick", "item_slots", "tags", "accepts", "upgrades")) {
            assertTrue(parsed.errors().stream().anyMatch(e -> e.startsWith(path)), path + " in " + parsed.errors());
        }
        assertTrue(LabTypeJson.parse("x:none", json("{}")).errors().stream().anyMatch(e -> e.startsWith("roles")), "roles required");
        assertTrue(LabTypeJson.parse("x:cons", json("{ \"roles\": [\"consumer\"], \"item_slots\": 0 }")).errors().stream()
              .anyMatch(e -> e.contains("no storage")), "§8.3: a consumer with no storage");
        assertTrue(LabTypeJson.parse("x:ctrl", json("{ \"roles\": [\"controller\", \"node\"], \"max_workers\": 0 }")).errors().stream()
              .anyMatch(e -> e.startsWith("max_workers")), "§8.3: a controller with max_workers 0");
    }

    @Test
    void overridesChangeSingleFields() {
        JsonObject base = json("{ \"roles\": [\"controller\", \"consumer\", \"worker\"], \"speed\": 1.0, \"item_slots\": 4, \"addons\": { \"a\": { \"k\": 1 } } }");
        JsonObject merged = LabTypeJson.override(base, json("{ \"speed\": 2.5, \"addons\": { \"a\": { \"j\": 2 } } }"));
        LabTypeDefinition t = LabTypeJson.parse("x:lab", merged).definition().orElseThrow();
        assertEquals(2.5, t.speed());
        assertEquals(4, t.itemSlots(), "untouched fields stay");
        assertEquals(json("{ \"k\": 1, \"j\": 2 }"), merged.getAsJsonObject("addons").getAsJsonObject("a"), "objects merge key by key");
        assertEquals(1.0, base.get("speed").getAsDouble(), "the base is not changed");
    }
}
