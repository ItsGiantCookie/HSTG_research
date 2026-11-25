package io.github.itsgiantcookie.hstglabs.gametest;

import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.research.TechReloader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** {@code /hslabs} runs through the real dispatcher (WP-11 "every command through the dispatcher", started early). */
@GameTestHolder(HstgLabs.MODID)
@PrefixGameTestTemplate(false)
public final class CommandTests {

    private CommandTests() {
    }

    @GameTest(template = "empty")
    public static void techListCountsEnabledTechnologies(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        int result = server.getCommands().getDispatcher().execute("hslabs tech list", server.createCommandSourceStack().withSuppressedOutput());
        helper.assertTrue(result == TechReloader.current().technologies().size(), "returned " + result);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void queueCommandsNeedAPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        try {
            server.getCommands().getDispatcher().execute("hslabs queue list", server.createCommandSourceStack().withSuppressedOutput());
            helper.fail("the server console has no research queue");
        } catch (Exception expected) {
            helper.succeed();
        }
    }
}
