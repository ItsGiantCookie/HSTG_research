package de.germanrangers.hstglabs.gametest;

import com.google.gson.JsonParser;
import de.germanrangers.hstglabs.HstgLabs;
import de.germanrangers.hstglabs.api.tech.TechSource;
import de.germanrangers.hstglabs.core.LabRequirements;
import de.germanrangers.hstglabs.core.Role;
import de.germanrangers.hstglabs.core.tech.LabTypeModel;
import de.germanrangers.hstglabs.core.tech.Problem;
import de.germanrangers.hstglabs.core.tech.StageModel;
import de.germanrangers.hstglabs.core.tech.TechValidator;
import de.germanrangers.hstglabs.mek.ChemicalIngredientType;
import de.germanrangers.hstglabs.research.ApiRegistries;
import de.germanrangers.hstglabs.research.TechReloader;
import de.germanrangers.hstglabs.tech.BuiltInTypes;
import de.germanrangers.hstglabs.tech.PackLookup;
import de.germanrangers.hstglabs.tech.SciencePackItem;
import de.germanrangers.hstglabs.tech.TechJson;
import de.germanrangers.hstglabs.tech.TechRepository;
import de.germanrangers.hstglabs.tech.TechRepository.Snapshot;
import de.germanrangers.hstglabs.tech.TechTypes;
import de.germanrangers.hstglabs.tech.Technology;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * WP-07 acceptance (docs/design.md §13): definitions resolve against the real registries; a broken
 * file disables only its technology with a clear error; an unknown lab tag is reported; unknown data
 * survives. Uses a temp config folder, a fake stage catalog and fixture lab types (HS stages and lab
 * types come in WP-08 / WP-09).
 */
@GameTestHolder(HstgLabs.MODID)
@PrefixGameTestTemplate(false)
public final class TechRepositoryTests {

    private static final Function<String, Optional<StageModel>> STAGES = id -> id.startsWith("missing")
          ? Optional.empty() : Optional.of(new StageModel(id, true));
    private static final List<LabTypeModel> LABS = List.of(
          new LabTypeModel("hstg_labs:basic_lab", 1, Set.of(), EnumSet.of(Role.CONTROLLER, Role.CONSUMER, Role.WORKER), Set.of()),
          new LabTypeModel("hstg_labs:research_core", 2, Set.of(), EnumSet.of(Role.CONTROLLER, Role.NODE), Set.of()),
          new LabTypeModel("mypack:chemistry_lab", 2, Set.of("hstg_labs:chemistry"), EnumSet.of(Role.CONSUMER, Role.NODE), Set.of()));

    private static final String GOOD = """
          { "stage": "s_good", "cost": { "units": 3, "unit_time": 20, "ingredients": [ { "type": "hstg_labs:item", "item": "minecraft:paper", "amount": 1 } ] } }
          """;

    private TechRepositoryTests() {
    }

    private static TechTypes types(Collection<TechSource> sources) {
        return new TechTypes(ApiRegistries.INGREDIENT_TYPES::get, ApiRegistries.LAB_REQUIREMENT_TYPES::get, ApiRegistries.REWARD_TYPES::get, sources);
    }

    private static Path folder(Map<String, String> files) {
        try {
            Path dir = Files.createTempDirectory("hstglabs-test");
            for (Map.Entry<String, String> file : files.entrySet()) {
                Path path = dir.resolve(file.getKey());
                Files.createDirectories(path.getParent());
                Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
            }
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Snapshot load(Map<String, String> files) {
        return TechRepository.load(folder(files), types(List.of()), STAGES, LABS);
    }

    private static Problem problem(GameTestHelper helper, Snapshot snapshot, String subject, String code) {
        return snapshot.problems().stream().filter(p -> p.subject().equals(subject) && p.code().equals(code)).findFirst()
              .orElseGet(() -> {
                  helper.fail("no " + code + " problem for " + subject + " in " + snapshot.problems());
                  return null;
              });
    }

    @GameTest(template = "empty")
    public static void brokenFileDisablesOnlyItsTechnology(GameTestHelper helper) {
        Snapshot snapshot = load(Map.of(
              "technologies/good.json", GOOD,
              "technologies/syntax.json", "{ \"stage\": \"s_syntax\", ",
              "technologies/badrange.json", GOOD.replace("s_good", "s_badrange").replace("\"units\": 3", "\"units\": 0"),
              "technologies/unknowntype.json", GOOD.replace("s_good", "s_unknown").replace("hstg_labs:item", "hstg_labs:nope")));
        helper.assertTrue(snapshot.technologies().keySet().equals(Set.of(HstgLabs.rl("good"))), "enabled: " + snapshot.technologies().keySet());

        Problem syntax = problem(helper, snapshot, "technologies/syntax.json", TechRepository.INVALID_FILE);
        helper.assertTrue(syntax.severity() == Problem.Severity.ERROR && syntax.message().startsWith("not valid JSON"), syntax.toString());
        Problem range = problem(helper, snapshot, "hstg_labs:badrange", TechRepository.INVALID_DEFINITION);
        helper.assertTrue(range.message().equals("technologies/badrange.json: cost.units: must be 1–1000000, was 0"), range.toString());
        Problem type = problem(helper, snapshot, "hstg_labs:unknowntype", TechRepository.INVALID_DEFINITION);
        helper.assertTrue(type.message().contains("cost.ingredients[0].type: unknown ingredient type 'hstg_labs:nope'"), type.toString());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void builtInTypesResolve(GameTestHelper helper) {
        Snapshot snapshot = load(Map.of(
              "packs/logistic.json", "{ \"name\": \"Logistic Pack\", \"color\": \"#E04040\", \"tier\": 1 }",
              "technologies/chem/polymers.json", """
                    {
                      "stage": "s_polymers",
                      "cost": { "units": 120, "unit_time": 600, "ingredients": [
                        { "type": "hstg_labs:item", "item": "minecraft:paper", "amount": 1 },
                        { "type": "hstg_labs:tag", "tag": "minecraft:planks", "amount": 2 },
                        { "type": "hstg_labs:pack", "pack": "hstg_labs:logistic", "amount": 1 },
                        { "type": "hstg_labs:chemical", "chemical": "mekanism:oxygen", "amount": 100 }
                      ] },
                      "labs": { "min_tier": 2, "requires": [
                        { "type": "hstg_labs:all", "of": [
                          { "type": "hstg_labs:lab", "lab_tag": "hstg_labs:chemistry", "count": 1, "role": "consumer" },
                          { "type": "hstg_labs:network" } ] },
                        { "type": "hstg_labs:any", "of": [ { "type": "hstg_labs:min_tier", "tier": 2 }, { "type": "hstg_labs:lab", "lab_type": "hstg_labs:research_core" } ] }
                      ] }
                    }
                    """));
        helper.assertTrue(snapshot.errors() == 0, "errors: " + snapshot.problems());
        Technology tech = snapshot.technologies().get(HstgLabs.rl("chem/polymers"));
        helper.assertTrue(tech != null, "chem/polymers not enabled: " + snapshot.problems());
        List<ResourceLocation> types = tech.cost().ingredients().stream().map(i -> i.what().type()).toList();
        helper.assertTrue(types.equals(List.of(BuiltInTypes.ITEM, BuiltInTypes.TAG, BuiltInTypes.PACK, ChemicalIngredientType.ID)), "types: " + types);
        helper.assertTrue(tech.cost().ingredients().get(3).amount() == 100, "chemical amount");
        helper.assertTrue(tech.minTier() == 2 && tech.requirements().size() == 2, "requirements: " + tech.requirements());
        helper.assertTrue(tech.requirements().get(0) instanceof LabRequirements.AllOf all && all.of().size() == 2
              && all.of().get(0) instanceof LabRequirements.Lab lab && "hstg_labs:chemistry".equals(lab.labTag()) && lab.role() == Role.CONSUMER,
              "all: " + tech.requirements().get(0));
        helper.assertTrue(tech.rewards().size() == 1 && tech.rewards().get(0).type().equals(BuiltInTypes.UNLOCK_STAGE), "default reward");
        helper.assertTrue(snapshot.packs().containsKey(HstgLabs.rl("logistic")), "pack loaded");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownLabTagIsReported(GameTestHelper helper) {
        Snapshot snapshot = load(Map.of("technologies/gated.json", GOOD.replace("s_good", "s_gated").replace("] }", "] }, "
              + "\"labs\": { \"requires\": [ { \"type\": \"hstg_labs:lab\", \"lab_tag\": \"mypack:no_such_tag\" } ] }")));
        Problem tag = problem(helper, snapshot, "hstg_labs:gated", TechValidator.UNKNOWN_LAB_TAG);
        helper.assertTrue(tag.severity() == Problem.Severity.ERROR && tag.message().contains("mypack:no_such_tag"), tag.toString());
        helper.assertTrue(snapshot.technologies().isEmpty(), "gated technology must be disabled");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownStageAndBadEntriesAreErrors(GameTestHelper helper) {
        Snapshot snapshot = load(Map.of(
              "technologies/nostage.json", GOOD.replace("s_good", "missing_stage"),
              "technologies/nopack.json", GOOD.replace("s_good", "s_nopack").replace("\"type\": \"hstg_labs:item\", \"item\": \"minecraft:paper\"",
                    "\"type\": \"hstg_labs:pack\", \"pack\": \"hstg_labs:nope\""),
              "technologies/noitem.json", GOOD.replace("s_good", "s_noitem").replace("minecraft:paper", "minecraft:no_such_item"),
              "technologies/twolabs.json", GOOD.replace("s_good", "s_twolabs").replace("] }", "] }, \"labs\": { \"requires\": [ "
                    + "{ \"type\": \"hstg_labs:lab\", \"lab_type\": \"a:b\", \"lab_tag\": \"c:d\" } ] }")));
        problem(helper, snapshot, "hstg_labs:nostage", TechValidator.UNKNOWN_STAGE);
        helper.assertTrue(problem(helper, snapshot, "hstg_labs:nopack", TechRepository.INVALID_DEFINITION).message().contains("unknown pack 'hstg_labs:nope'"),
              "pack message");
        helper.assertTrue(problem(helper, snapshot, "hstg_labs:noitem", TechRepository.INVALID_DEFINITION).message().contains("cost.ingredients[0]"),
              "item message");
        helper.assertTrue(problem(helper, snapshot, "hstg_labs:twolabs", TechRepository.INVALID_DEFINITION).message()
              .contains("exactly one of lab_type or lab_tag"), "lab message");
        helper.assertTrue(snapshot.technologies().isEmpty(), "all four disabled");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownDataSurvivesLoadAndWrite(GameTestHelper helper) {
        String file = """
              { "stage": "s_addon",
                "cost": { "units": 1, "unit_time": 1, "ingredients": [ { "type": "someaddon:mana", "mana": 5, "amount": 1 } ] },
                "addons": { "someaddon": { "colour": "red" } }, "future": [1, 2] }
              """;
        Snapshot snapshot = load(Map.of("technologies/addon.json", file));
        helper.assertTrue(snapshot.technologies().isEmpty(), "unknown ingredient type disables it");
        var definition = snapshot.definitions().get("hstg_labs:addon");
        helper.assertTrue(definition != null, "definition kept although disabled");
        helper.assertTrue(TechJson.write(definition).equals(JsonParser.parseString(file)), "written back unchanged: " + TechJson.write(definition));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void addonRequirementTypesFailClosed(GameTestHelper helper) {
        // the demo's min_members type is registered, but the scheduler cannot evaluate addon types before WP-09
        Snapshot snapshot = load(Map.of("technologies/demo.json", GOOD.replace("s_good", "s_demo").replace("] }", "] }, "
              + "\"labs\": { \"requires\": [ { \"type\": \"hstg_labs:demo_min_members\", \"count\": 2 } ] }")));
        helper.assertTrue(snapshot.technologies().isEmpty(), "must not run without its requirement");
        helper.assertTrue(problem(helper, snapshot, "hstg_labs:demo", TechRepository.INVALID_DEFINITION).message().contains("WP-09"), "message");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void techSourcesAddAndOverride(GameTestHelper helper) {
        TechSource source = sink -> {
            sink.accept(HstgLabs.rl("from_code"), JsonParser.parseString(GOOD.replace("s_good", "s_code")));
            sink.accept(HstgLabs.rl("good"), JsonParser.parseString(GOOD.replace("\"units\": 3", "\"units\": 7")));
        };
        Snapshot snapshot = TechRepository.load(folder(Map.of("technologies/good.json", GOOD)), types(List.of(source)), STAGES, LABS);
        helper.assertTrue(snapshot.technologies().containsKey(HstgLabs.rl("from_code")), "source technology loaded");
        helper.assertTrue(snapshot.technologies().get(HstgLabs.rl("good")).cost().units() == 7, "source replaces the file (D3 order)");
        helper.assertTrue(problem(helper, snapshot, "hstg_labs:good", TechRepository.OVERRIDDEN).severity() == Problem.Severity.WARNING, "warning");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sciencePackStacksNameAndMatch(GameTestHelper helper) {
        Snapshot snapshot = load(Map.of("packs/logistic.json", "{ \"name\": \"Logistic Pack\", \"color\": \"#E04040\" }"));
        var previous = TechReloader.current().packs();
        try {
            PackLookup.setServer(snapshot.packs());
            ItemStack stack = SciencePackItem.of(HstgLabs.rl("logistic"), 3);
            helper.assertTrue(stack.getHoverName().getString().equals("Logistic Pack"), "name: " + stack.getHoverName().getString());
            var packType = ApiRegistries.INGREDIENT_TYPES.get(BuiltInTypes.PACK).orElseThrow();
            @SuppressWarnings("unchecked")
            var typed = (de.germanrangers.hstglabs.api.ingredient.IngredientType<BuiltInTypes.PackIngredient, ItemStack>) packType;
            helper.assertTrue(typed.matches(new BuiltInTypes.PackIngredient(HstgLabs.rl("logistic")), stack), "matches its pack");
            helper.assertTrue(!typed.matches(new BuiltInTypes.PackIngredient(HstgLabs.rl("other")), stack), "not another pack");
            helper.assertTrue(!typed.matches(new BuiltInTypes.PackIngredient(HstgLabs.rl("logistic")), new ItemStack(Items.PAPER)), "not paper");
            helper.assertTrue(SciencePackItem.of(HstgLabs.rl("ghost"), 1).getHoverName().getString().contains("hstg_labs:ghost"), "unknown pack named");
        } finally {
            PackLookup.setServer(previous);
        }
        helper.succeed();
    }
}
