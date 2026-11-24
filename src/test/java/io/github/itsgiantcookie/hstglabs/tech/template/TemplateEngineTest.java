package io.github.itsgiantcookie.hstglabs.tech.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.itsgiantcookie.hstglabs.tech.template.TemplateEngine.Origin;
import io.github.itsgiantcookie.hstglabs.tech.template.TemplateEngine.Resolution;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * docs/design.md §8.6, D24, D27: resolution order root → extends → params → overrides; extends cycles
 * refused; a missing param is an error, an unknown one a warning; ${param} and = expressions.
 */
class TemplateEngineTest {

    private static final String TECH = "hstg_labs:technology";
    private static final String PACK = "hstg_labs:pack";

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static Template template(String id, String text) {
        TemplateJson.Parsed parsed = TemplateJson.parse(id, json(text), Set.of(TECH, PACK)::contains);
        assertEquals(List.of(), parsed.errors(), id);
        return parsed.template().orElseThrow();
    }

    private static TemplateEngine engine(Template... templates) {
        Map<String, Template> map = new LinkedHashMap<>();
        for (Template t : templates) {
            map.put(t.id(), t);
        }
        return new TemplateEngine(map, Set.of("x:datapack")::contains);
    }

    private static final Template ROOT = template("t:root", """
          { "kind": "hstg_labs:technology",
            "declare": { "tier": "int", "category": { "type": "string", "default": "main" }, "factor": { "type": "number", "default": 2 } },
            "defaults": { "cost": { "units": 1, "unit_time": 100, "ingredients": [ { "type": "hstg_labs:item", "item": "minecraft:paper", "amount": 1 } ] },
                          "tree": { "category": "${category}" } } }
          """);
    private static final Template CHILD = template("t:child", """
          { "kind": "hstg_labs:technology", "extends": "t:root",
            "params": { "category": "child" },
            "defaults": { "cost": { "units": "=10 * factor ^ (tier - 1)" }, "labs": { "min_tier": "=tier" } } }
          """);

    @Test
    void resolutionOrderRootExtendsParamsOverrides() {
        Resolution r = engine(ROOT, CHILD).resolve(TECH, json("""
              { "stage": "s", "template": "t:child", "params": { "tier": 3 }, "overrides": { "cost": { "unit_time": 999 } } }
              """));
        assertEquals(List.of(), r.errors());
        JsonObject out = r.json().orElseThrow();
        assertEquals(40, out.getAsJsonObject("cost").get("units").getAsInt(), "child expression over root value, with the instance's tier");
        assertEquals(999, out.getAsJsonObject("cost").get("unit_time").getAsInt(), "override over template");
        assertEquals("child", out.getAsJsonObject("tree").get("category").getAsString(), "child params over root default");
        assertEquals(3, out.getAsJsonObject("labs").get("min_tier").getAsInt());
        assertEquals("s", out.get("stage").getAsString(), "instance fields kept");
        assertFalse(out.has("template") || out.has("params") || out.has("overrides"), "link fields are not part of the result");
        assertTrue(out.get("cost").getAsJsonObject().get("units").getAsJsonPrimitive().getAsNumber() instanceof Long
              || out.get("cost").getAsJsonObject().get("units").toString().equals("40"), "whole results are written as integers");
    }

    @Test
    void instanceParamsWinOverTemplateParams() {
        Resolution r = engine(ROOT, CHILD).resolve(TECH, json("{ \"stage\": \"s\", \"template\": \"t:child\", \"params\": { \"tier\": 1, \"category\": \"mine\" } }"));
        assertEquals("mine", r.json().orElseThrow().getAsJsonObject("tree").get("category").getAsString());
    }

    @Test
    void originsSayWhereEachValueCameFrom() {
        Resolution r = engine(ROOT, CHILD).resolve(TECH, json("""
              { "stage": "s", "template": "t:child", "params": { "tier": 2 }, "overrides": { "cost": { "unit_time": 5 } } }
              """));
        Map<String, Origin> o = r.origins();
        assertEquals(new Origin(Origin.Source.OVERRIDE, "overrides"), o.get("cost.unit_time"));
        assertEquals(new Origin(Origin.Source.PARAM, "=10 * factor ^ (tier - 1)"), o.get("cost.units"));
        assertEquals(new Origin(Origin.Source.PARAM, "${category}"), o.get("tree.category"));
        assertEquals(new Origin(Origin.Source.TEMPLATE, "t:root"), o.get("cost.ingredients"));
        assertEquals(new Origin(Origin.Source.INSTANCE, "file"), o.get("stage"));
    }

    @Test
    void extendsCyclesAreRefused() {
        Template a = template("t:a", "{ \"kind\": \"hstg_labs:technology\", \"extends\": \"t:b\" }");
        Template b = template("t:b", "{ \"kind\": \"hstg_labs:technology\", \"extends\": \"t:a\" }");
        Resolution r = engine(a, b).resolve(TECH, json("{ \"template\": \"t:a\" }"));
        assertTrue(r.json().isEmpty());
        assertTrue(r.errors().get(0).contains("extends cycle: t:a -> t:b -> t:a"), r.errors().toString());
        Template self = template("t:self", "{ \"kind\": \"hstg_labs:technology\", \"extends\": \"t:self\" }");
        assertTrue(engine(self).resolve(TECH, json("{ \"template\": \"t:self\" }")).errors().get(0).contains("cycle"));
    }

    @Test
    void unknownTemplatesAndKindMismatchAreErrors() {
        assertTrue(engine(ROOT).resolve(TECH, json("{ \"template\": \"t:nope\" }")).errors().get(0).contains("unknown template 't:nope'"));
        Template orphan = template("t:orphan", "{ \"kind\": \"hstg_labs:technology\", \"extends\": \"t:gone\" }");
        assertTrue(engine(orphan).resolve(TECH, json("{ \"template\": \"t:orphan\" }")).errors().get(0).contains("unknown template 't:gone'"));
        assertTrue(engine(ROOT).resolve(PACK, json("{ \"template\": \"t:root\", \"params\": { \"tier\": 1 } }")).errors().get(0)
              .contains("kind hstg_labs:technology, expected hstg_labs:pack"));
        Template mixed = template("t:mixed", "{ \"kind\": \"hstg_labs:pack\", \"extends\": \"t:root\" }");
        assertTrue(engine(ROOT, mixed).resolve(PACK, json("{ \"template\": \"t:mixed\", \"params\": { \"tier\": 1 } }")).errors().get(0)
              .contains("kind"));
        assertTrue(engine(ROOT).resolve(TECH, json("{ \"template\": 5 }")).errors().get(0).contains("template"));
    }

    @Test
    void missingParamIsAnErrorUnknownParamAWarning() {
        Resolution missing = engine(ROOT, CHILD).resolve(TECH, json("{ \"template\": \"t:child\" }"));
        assertTrue(missing.json().isEmpty());
        assertTrue(missing.errors().contains("params.tier: missing (required by t:root)"), missing.errors().toString());

        Resolution unknown = engine(ROOT, CHILD).resolve(TECH, json("{ \"stage\": \"s\", \"template\": \"t:child\", \"params\": { \"tier\": 1, \"colour\": 3 } }"));
        assertEquals(List.of(), unknown.errors());
        assertEquals(List.of("params.colour: unknown param, t:child does not declare it"), unknown.warnings());
        assertTrue(unknown.json().isPresent());
    }

    @Test
    void paramsAreTypeChecked() {
        Template typed = template("t:typed", """
              { "kind": "hstg_labs:pack", "declare": { "i": "int", "n": "number", "c": "color", "id": "id", "ids": "id_list", "s": "string" } }
              """);
        Resolution bad = engine(typed).resolve(PACK, json("""
              { "template": "t:typed", "params": { "i": 1.5, "n": "x", "c": "red", "id": "No Id", "ids": ["a:b", 3], "s": 4 } }
              """));
        for (String p : List.of("params.i", "params.n", "params.c", "params.id", "params.ids", "params.s")) {
            assertTrue(bad.errors().stream().anyMatch(e -> e.startsWith(p + ":")), p + " in " + bad.errors());
        }
        Resolution good = engine(typed).resolve(PACK, json("""
              { "template": "t:typed", "params": { "i": 2, "n": 1.5, "c": "#a0B0c0", "id": "a:b", "ids": ["a:b", "c:d"], "s": "x" } }
              """));
        assertEquals(List.of(), good.errors());
    }

    @Test
    void substitutionWholeAndPartial() {
        Template t = template("t:sub", """
              { "kind": "hstg_labs:pack", "declare": { "name": "string", "color": "color", "tier": { "type": "int", "default": 1 }, "ids": "id_list" },
                "defaults": { "name": "${name}", "label": "Tier ${tier} ${name}", "color": "${color}", "tier": "${tier}", "list": "${ids}",
                              "maths": "=tier * 2 + 0.5", "escaped": "plain text" } }
              """);
        Resolution r = engine(t).resolve(PACK, json("{ \"template\": \"t:sub\", \"params\": { \"name\": \"Logistic\", \"color\": \"#E04040\", \"tier\": 2, \"ids\": [\"a:b\"] } }"));
        assertEquals(List.of(), r.errors());
        JsonObject out = r.json().orElseThrow();
        assertEquals("Logistic", out.get("name").getAsString());
        assertEquals("Tier 2 Logistic", out.get("label").getAsString());
        assertEquals("#E04040", out.get("color").getAsString(), "colours come out as #RRGGBB");
        assertEquals(2, out.get("tier").getAsInt(), "a whole ${param} keeps its JSON type");
        assertTrue(out.get("list").isJsonArray());
        assertEquals(4.5, out.get("maths").getAsDouble());
        assertEquals("plain text", out.get("escaped").getAsString());
    }

    @Test
    void badSubstitutionsNameTheirPath() {
        Template t = template("t:bad", """
              { "kind": "hstg_labs:pack", "declare": { "s": "string" },
                "defaults": { "a": { "b": "${nope}" }, "c": [ "=1 / 0" ], "d": "=s + 1", "e": "=1 +" } }
              """);
        List<String> errors = engine(t).resolve(PACK, json("{ \"template\": \"t:bad\", \"params\": { \"s\": \"x\" } }")).errors();
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("a.b: unknown param 'nope'")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("c[0]: ") && e.contains("not a finite number")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("d: ") && e.contains("'s' is not a number")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("e: ")), errors.toString());
    }

    @Test
    void paramDefaultsMayBeExpressionsOfOtherParams() {
        Template t = template("t:defaults", """
              { "kind": "hstg_labs:technology",
                "declare": { "tier": "int", "lab_tier": { "type": "int", "default": "=tier" }, "half": { "type": "number", "default": "=lab_tier / 2" } },
                "defaults": { "a": "${lab_tier}", "b": "${half}" } }
              """);
        JsonObject out = engine(t).resolve(TECH, json("{ \"template\": \"t:defaults\", \"params\": { \"tier\": 4 } }")).json().orElseThrow();
        assertEquals(4, out.get("a").getAsInt());
        assertEquals(2, out.get("b").getAsInt());
        JsonObject overridden = engine(t).resolve(TECH, json("{ \"template\": \"t:defaults\", \"params\": { \"tier\": 4, \"lab_tier\": 2 } }"))
              .json().orElseThrow();
        assertEquals(2, overridden.get("a").getAsInt(), "an explicit param beats the default expression");
        assertEquals(1, overridden.get("b").getAsInt());
        Template loop = template("t:loop", """
              { "kind": "hstg_labs:technology", "declare": { "a": { "type": "int", "default": "=b" }, "b": { "type": "int", "default": "=a" } } }
              """);
        assertTrue(engine(loop).resolve(TECH, json("{ \"template\": \"t:loop\" }")).errors().get(0).contains("depend on each other"));
    }

    @Test
    void arraysAreReplacedNotMerged() {
        Resolution r = engine(ROOT, CHILD).resolve(TECH, json("""
              { "stage": "s", "template": "t:child", "params": { "tier": 1 },
                "overrides": { "cost": { "ingredients": [ { "type": "hstg_labs:item", "item": "minecraft:stick", "amount": 2 } ] } } }
              """));
        var ingredients = r.json().orElseThrow().getAsJsonObject("cost").getAsJsonArray("ingredients");
        assertEquals(1, ingredients.size());
        assertEquals("minecraft:stick", ingredients.get(0).getAsJsonObject().get("item").getAsString());
    }

    @Test
    void builtInTieredCurve() {
        TemplateEngine engine = new TemplateEngine(BuiltInTemplates.all(), Set.of("hstg_labs:logistic")::contains);
        Resolution r = engine.resolve(TECH, json("""
              { "stage": "s", "template": "hstg_labs:tiered", "params": { "tier": 3, "packs": ["kubejs:automation_pack", "hstg_labs:logistic"], "category": "energy" } }
              """));
        assertEquals(List.of(), r.errors());
        JsonObject out = r.json().orElseThrow();
        JsonObject cost = out.getAsJsonObject("cost");
        assertEquals(100, cost.get("units").getAsInt(), "25 × 2^(3 − 1)");
        assertEquals(600, cost.get("unit_time").getAsInt(), "200 × 3");
        assertEquals("hstg_labs:item", cost.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("hstg_labs:pack", cost.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("type").getAsString(), "known data pack");
        assertEquals(3, out.getAsJsonObject("labs").get("min_tier").getAsInt(), "lab_tier defaults to tier");
        assertEquals("energy", out.getAsJsonObject("tree").get("category").getAsString());

        JsonObject split = engine.resolve(TECH, json("""
              { "stage": "s", "template": "hstg_labs:tiered", "params": { "tier": 2, "lab_tier": 1, "packs": ["a:b"], "base_units": 30, "factor": 3 } }
              """)).json().orElseThrow();
        assertEquals(90, split.getAsJsonObject("cost").get("units").getAsInt());
        assertEquals(1, split.getAsJsonObject("labs").get("min_tier").getAsInt(), "cost and gate can be split");
    }

    @Test
    void jsonTemplateExtendsTheBuiltInCurve() {
        // the design's own example: a pack template extending hstg_labs:tiered with steeper params
        Template main = template("mypack:main", """
              { "kind": "hstg_labs:technology", "extends": "hstg_labs:tiered", "params": { "base_units": 30, "factor": 3 },
                "defaults": { "rewards": [ { "type": "hstg_labs:unlock_stage" } ] } }
              """);
        Map<String, Template> all = new LinkedHashMap<>(BuiltInTemplates.all());
        all.put(main.id(), main);
        JsonObject out = new TemplateEngine(all, id -> false).resolve(TECH, json("""
              { "stage": "steam_power", "template": "mypack:main", "params": { "tier": 2, "packs": ["kubejs:automation_pack"] },
                "overrides": { "cost": { "unit_time": 400 } } }
              """)).json().orElseThrow();
        assertEquals(90, out.getAsJsonObject("cost").get("units").getAsInt());
        assertEquals(400, out.getAsJsonObject("cost").get("unit_time").getAsInt());
        assertTrue(out.has("rewards"));
    }

    @Test
    void builtInDataFlask() {
        TemplateEngine engine = new TemplateEngine(BuiltInTemplates.all(), id -> false);
        JsonObject out = engine.resolve(PACK, json("{ \"template\": \"hstg_labs:data_flask\", \"params\": { \"name\": \"Logistic Pack\", \"color\": \"#40C040\", \"tier\": 2 } }"))
              .json().orElseThrow();
        assertEquals(json("{ \"name\": \"Logistic Pack\", \"color\": \"#40C040\", \"tier\": 2 }"), out);
    }

    @Test
    void templateFileErrors() {
        List<String> errors = new ArrayList<>(TemplateJson.parse("t:x", json("""
              { "kind": "nope:kind", "extends": 3, "declare": { "a": "float", "b": { "type": "int", "default": "x" }, "Bad Name": "int" },
                "params": [], "defaults": 7 }
              """), Set.of(TECH)::contains).errors());
        for (String expected : List.of("kind", "extends", "declare.a", "declare.b", "declare.Bad Name", "params", "defaults")) {
            assertTrue(errors.stream().anyMatch(e -> e.startsWith(expected + ":")), expected + " in " + errors);
        }
        assertTrue(TemplateJson.parse("t:y", json("{}"), Set.of(TECH)::contains).errors().stream().anyMatch(e -> e.startsWith("kind:")));
    }
}
