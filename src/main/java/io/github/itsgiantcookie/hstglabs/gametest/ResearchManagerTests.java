package io.github.itsgiantcookie.hstglabs.gametest;

import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import io.github.itsgiantcookie.hstglabs.core.ResearchBook;
import io.github.itsgiantcookie.hstglabs.hs.HistoryStagesBridge;
import io.github.itsgiantcookie.hstglabs.research.BuiltInOwners;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager.EnqueueResult;
import io.github.itsgiantcookie.hstglabs.research.ResearchSavedData;
import io.github.itsgiantcookie.hstglabs.research.TechReloader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * WP-08 acceptance (docs/design.md §13), global stages: units done → stage unlocked with cause
 * {@code ADDON}; a vetoed unlock keeps progress and marks BLOCKED (D32: out of the queue, re-adding
 * retries); a relock follows {@code on_relock} (default RESET, D5); progress survives save and reload.
 * Stages are our fixtures (testpack/hs-fixtures); technologies are written to the real config folder,
 * loaded, and removed again.
 */
@GameTestHolder(HstgLabs.MODID)
@PrefixGameTestTemplate(false)
public final class ResearchManagerTests {

    private ResearchManagerTests() {
    }

    private static ResourceLocation technology(MinecraftServer server, String name, String stage, int units) {
        try {
            Path file = TechReloader.configDir().resolve("technologies/" + name + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, """
                  { "stage": "%s", "cost": { "units": %d, "unit_time": 20,
                    "ingredients": [ { "type": "hstg_labs:item", "item": "minecraft:paper", "amount": 1 } ] } }
                  """.formatted(stage, units), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        TechReloader.reload(server, "gametest");
        return HstgLabs.rl(name);
    }

    private static void remove(MinecraftServer server, String name) {
        try {
            Files.deleteIfExists(TechReloader.configDir().resolve("technologies/" + name + ".json"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        TechReloader.reload(server, "gametest cleanup");
    }

    private static ResearchManager manager(GameTestHelper helper) {
        return ResearchManager.get().orElseThrow(() -> new IllegalStateException("research manager not running"));
    }

    @GameTest(template = "empty", batch = "research")
    public static void unitsDoneUnlockTheStageWithCauseAddon(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HistoryStagesBridge.relockGlobalForTest("hstg_test_unlock", server);
        ResourceLocation tech = technology(server, "gametest_unlock", "hstg_test_unlock", 3);
        helper.assertTrue(TechReloader.current().technologies().containsKey(tech), "technology not enabled: " + TechReloader.current().problems());
        ResearchManager m = manager(helper);
        OwnerId owner = new OwnerId(BuiltInOwners.SERVER, "gametest_unlock");
        m.forgetOwner(owner); // §14: start clean, the GameTest world persists between runs

        helper.assertTrue(m.enqueue(owner, tech) == EnqueueResult.ADDED, "enqueue");
        helper.assertTrue(m.addProgress(owner, tech, 2) == 2, "two units");
        helper.assertTrue(!HistoryStagesBridge.INSTANCE.isUnlockedGlobal("hstg_test_unlock", server), "unlocked too early");
        helper.assertTrue(m.addProgress(owner, tech, 5) == 1, "only the last unit counts");

        helper.assertTrue(HistoryStagesBridge.INSTANCE.isUnlockedGlobal("hstg_test_unlock", server), "stage not unlocked");
        helper.assertTrue(HistoryStagesBridge.lastUnlockCause("hstg_test_unlock").orElse("").equals("ADDON"),
              "cause: " + HistoryStagesBridge.lastUnlockCause("hstg_test_unlock"));
        ResearchBook<ResourceLocation> book = m.book(owner);
        helper.assertTrue(!book.queue().contains(tech) && book.readyToUnlock().isEmpty(), "left the queue");
        helper.assertTrue(m.enqueue(owner, tech) == EnqueueResult.ALREADY_RESEARCHED, "cannot queue a researched technology");
        remove(server, "gametest_unlock");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "research")
    public static void vetoedUnlockKeepsProgressAndMarksBlocked(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HistoryStagesBridge.relockGlobalForTest("hstg_test_veto", server);
        ResourceLocation tech = technology(server, "gametest_veto", "hstg_test_veto", 2);
        ResearchManager m = manager(helper);
        OwnerId owner = new OwnerId(BuiltInOwners.SERVER, "gametest_veto");
        m.forgetOwner(owner); // §14: start clean, the GameTest world persists between runs

        m.enqueue(owner, tech);
        HistoryStagesBridge.vetoNextUnlockForTest("hstg_test_veto");
        m.addProgress(owner, tech, 2);

        ResearchBook<ResourceLocation> book = m.book(owner);
        helper.assertTrue(!HistoryStagesBridge.INSTANCE.isUnlockedGlobal("hstg_test_veto", server), "veto ignored");
        helper.assertTrue(book.isBlocked(tech), "not marked BLOCKED");
        helper.assertTrue(book.isComplete(tech), "progress lost");
        helper.assertTrue(!book.queue().contains(tech), "D32: still in the queue");

        helper.assertTrue(m.enqueue(owner, tech) == EnqueueResult.ADDED, "re-add");
        helper.assertTrue(HistoryStagesBridge.INSTANCE.isUnlockedGlobal("hstg_test_veto", server), "re-adding did not retry the unlock");
        helper.assertTrue(!book.isBlocked(tech), "still BLOCKED after the unlock");
        remove(server, "gametest_veto");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "research")
    public static void relockFollowsOnRelockReset(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HistoryStagesBridge.relockGlobalForTest("hstg_test_relock", server);
        ResourceLocation tech = technology(server, "gametest_relock", "hstg_test_relock", 1);
        ResearchManager m = manager(helper);
        OwnerId owner = new OwnerId(BuiltInOwners.SERVER, "gametest_relock");
        m.forgetOwner(owner); // §14: start clean, the GameTest world persists between runs

        m.enqueue(owner, tech);
        m.addProgress(owner, tech, 1);
        helper.assertTrue(HistoryStagesBridge.INSTANCE.isUnlockedGlobal("hstg_test_relock", server), "unlock");
        helper.assertTrue(m.book(owner).isComplete(tech), "completed progress kept after our unlock");

        HistoryStagesBridge.relockGlobalForTest("hstg_test_relock", server);
        helper.assertTrue(m.book(owner).progress(tech).isEmpty(), "on_relock = RESET (default) must discard the progress");
        remove(server, "gametest_relock");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "research")
    public static void progressSurvivesSaveAndReload(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ResearchSavedData original = new ResearchSavedData();
        OwnerId owner = new OwnerId(BuiltInOwners.TEAM, "0f7c3e2a-1111-4222-8333-944455556666");
        ResearchBook<ResourceLocation> book = original.book(owner, 5);
        ResourceLocation a = HstgLabs.rl("a");
        ResourceLocation b = HstgLabs.rl("b");
        ResourceLocation c = HstgLabs.rl("c");
        book.enqueue(a);
        book.enqueue(b);
        book.progressFor(a, 10).tryClaim();
        book.addUnits(a, 10, 4);
        book.addUnits(c, 2, 2);
        book.blocked(c);
        book.addUnits(b, 1, 1);

        CompoundTag saved = original.save(new CompoundTag(), registries);
        CompoundTag foreign = new CompoundTag();
        foreign.putString("type", "Not An Id");
        foreign.putString("note", "kept as written");
        saved.getList("owners", 10).add(foreign);

        ResearchSavedData loaded = ResearchSavedData.load(saved, registries);
        ResearchBook<ResourceLocation> back = loaded.books().get(owner);
        helper.assertTrue(back != null, "owner lost");
        helper.assertTrue(back.queue().entries().equals(List.of(a, b)), "queue: " + back.queue().entries());
        helper.assertTrue(back.queue().max() == 5, "queue max");
        var pa = back.progress(a).orElseThrow();
        helper.assertTrue(pa.units() == 10 && pa.done() == 4 && pa.inFlight() == 1, "progress a: " + pa);
        helper.assertTrue(back.isBlocked(c) && back.isComplete(c), "blocked c");
        helper.assertTrue(back.readyToUnlock().equals(List.of(b)), "pending: " + back.readyToUnlock());

        ListTag resaved = loaded.save(new CompoundTag(), registries).getList("owners", 10);
        helper.assertTrue(resaved.stream().anyMatch(t -> ((CompoundTag) t).getString("note").equals("kept as written")),
              "an unreadable owner entry must survive load and save (§9.6)");
        helper.succeed();
    }
}
