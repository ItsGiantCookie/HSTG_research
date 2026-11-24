package io.github.itsgiantcookie.hstglabs.gametest;

import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.research.TechReloader;
import io.github.itsgiantcookie.hstglabs.tech.TechDefinition;
import io.github.itsgiantcookie.hstglabs.tech.TechRepository;
import io.github.itsgiantcookie.hstglabs.tech.template.TemplateEngine;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * WP-07a acceptance (docs/design.md §13): editing a template file and running {@code /reload} changes
 * the cost of every linked technology (D27). Uses the real config folder and the real command, in its
 * own batch so the reload does not run beside other tests; the files are removed afterwards.
 */
@GameTestHolder(HstgLabs.MODID)
@PrefixGameTestTemplate(false)
public final class TemplateReloadTests {

    private static final String TEMPLATE = "templates/gametest_curve.json";
    private static final List<String> INSTANCES = List.of("technologies/gametest_linked_a.json", "technologies/gametest_linked_b.json");

    private TemplateReloadTests() {
    }

    private static String template(int baseUnits) {
        return """
              { "kind": "hstg_labs:technology", "extends": "hstg_labs:tiered", "params": { "base_units": %d, "factor": 2 } }
              """.formatted(baseUnits);
    }

    private static String instance(String stage, int tier) {
        return """
              { "stage": "%s", "template": "hstg_labs:gametest_curve", "params": { "tier": %d, "packs": ["minecraft:paper"] } }
              """.formatted(stage, tier);
    }

    private static void write(String relative, String content) {
        try {
            Path file = TechReloader.configDir().resolve(relative);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void cleanUp() {
        try {
            Files.deleteIfExists(TechReloader.configDir().resolve(TEMPLATE));
            for (String file : INSTANCES) {
                Files.deleteIfExists(TechReloader.configDir().resolve(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void reloadCommand(MinecraftServer server) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "reload");
    }

    private static int units(String id) {
        TechDefinition definition = TechReloader.current().definitions().get(id);
        return definition == null ? -1 : definition.units();
    }

    @GameTest(template = "empty", batch = "reload", timeoutTicks = 600)
    public static void editingATemplateAndReloadingChangesEveryLinkedTechnology(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        cleanUp();
        write(TEMPLATE, template(10));
        write(INSTANCES.get(0), instance("gametest_stage_a", 1));
        write(INSTANCES.get(1), instance("gametest_stage_b", 3));

        helper.startSequence()
              .thenExecute(() -> reloadCommand(server))
              .thenWaitUntil(() -> {
                  helper.assertTrue(units("hstg_labs:gametest_linked_a") == 10, "a after first reload: " + units("hstg_labs:gametest_linked_a"));
                  helper.assertTrue(units("hstg_labs:gametest_linked_b") == 40, "b after first reload: " + units("hstg_labs:gametest_linked_b"));
              })
              .thenExecute(() -> {
                  // the link is kept: the instance file is written back unchanged, origins point at the template
                  TechRepository.Snapshot snapshot = TechReloader.current();
                  helper.assertTrue(snapshot.definitions().get("hstg_labs:gametest_linked_a").json().has("template"), "instance keeps its link");
                  helper.assertTrue(snapshot.origins().get("hstg_labs:gametest_linked_b").get("cost.units").source()
                        == TemplateEngine.Origin.Source.TEMPLATE, "origin of cost.units");
                  write(TEMPLATE, template(30));
                  reloadCommand(server);
              })
              .thenWaitUntil(() -> {
                  helper.assertTrue(units("hstg_labs:gametest_linked_a") == 30, "a after editing the template: " + units("hstg_labs:gametest_linked_a"));
                  helper.assertTrue(units("hstg_labs:gametest_linked_b") == 120, "b after editing the template: " + units("hstg_labs:gametest_linked_b"));
              })
              .thenExecute(TemplateReloadTests::cleanUp)
              .thenSucceed();
    }
}
