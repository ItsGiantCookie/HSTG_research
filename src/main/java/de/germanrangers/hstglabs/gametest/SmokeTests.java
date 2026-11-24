package de.germanrangers.hstglabs.gametest;

import de.germanrangers.hstglabs.HstgLabs;
import de.germanrangers.hstglabs.hs.HsVersion;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * WP-01 / WP-03 smoke test: the server starts with every required dependency and the History
 * Stages API generation this build was compiled against. Runs in {@code ./gradlew runGameTestServer}
 * (namespace {@code hstg_labs}, docs/design.md §12.5).
 * <p>
 * Uses our own empty template ({@code data/hstg_labs/structure/empty.nbt}); HS ships one too, but
 * its assets are All Rights Reserved and not copied (§7.1 rule 6).
 */
@GameTestHolder(HstgLabs.MODID)
@PrefixGameTestTemplate(false)
public final class SmokeTests {

    private SmokeTests() {
    }

    @GameTest(template = "empty")
    public static void requiredModsLoaded(GameTestHelper helper) {
        for (String mod : new String[]{"historystages", "lootr", "mekanism", HstgLabs.MODID}) {
            helper.assertTrue(ModList.get().isLoaded(mod), "required mod not loaded: " + mod);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void historyStagesApiGeneration(GameTestHelper helper) {
        helper.assertTrue(HsVersion.runtimeApiVersion() == HsVersion.COMPILED_API_VERSION,
              "HS API generation " + HsVersion.runtimeApiVersion() + " != compiled " + HsVersion.COMPILED_API_VERSION);
        helper.assertTrue(HsVersion.runtimeApiMinor() >= HsVersion.COMPILED_API_MINOR,
              "HS API minor " + HsVersion.runtimeApiMinor() + " older than compiled " + HsVersion.COMPILED_API_MINOR);
        helper.succeed();
    }
}
