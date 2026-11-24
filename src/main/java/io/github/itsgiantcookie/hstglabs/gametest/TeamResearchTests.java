package io.github.itsgiantcookie.hstglabs.gametest;

import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import io.github.itsgiantcookie.hstglabs.hs.HistoryStagesBridge;
import io.github.itsgiantcookie.hstglabs.research.ApiRegistries;
import io.github.itsgiantcookie.hstglabs.research.BuiltInOwners;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager;
import io.github.itsgiantcookie.hstglabs.research.TechReloader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * WP-08 acceptance (docs/design.md §13), teams (D2, D31): two teams research independently; a joining
 * member adopts the team's progress; an offline member gets the stage (HS writes it and syncs it at
 * their next login, docs/hs-api-facts.md). Fresh random UUIDs per run, so HS's per-player data never
 * carries over. Each test has its own individual fixture stage (one technology per stage, §8.3).
 */
@GameTestHolder(HstgLabs.MODID)
@PrefixGameTestTemplate(false)
public final class TeamResearchTests {


    private TeamResearchTests() {
    }

    private static ResourceLocation technology(MinecraftServer server, String name, String stage, int units) {
        try {
            Path file = TechReloader.configDir().resolve("technologies/" + name + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, """
                  { "stage": "%s", "cost": { "units": %d, "unit_time": 20,
                    "ingredients": [ { "type": "hstg_labs:item", "item": "minecraft:paper", "amount": 1 } ] } }
                  """.formatted(stage, units), StandardCharsets.UTF_8);
            TechReloader.reload(server, "gametest");
            return HstgLabs.rl(name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void remove(MinecraftServer server, String name) {
        try {
            Files.deleteIfExists(TechReloader.configDir().resolve("technologies/" + name + ".json"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        TechReloader.reload(server, "gametest cleanup");
    }

    @GameTest(template = "empty", batch = "research")
    public static void twoTeamsResearchIndependently(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation tech = technology(server, "gametest_teams", "hstg_test_teams", 4);
        helper.assertTrue(TechReloader.current().technologies().containsKey(tech), "technology not enabled: " + TechReloader.current().problems());
        ResearchManager m = ResearchManager.get().orElseThrow();
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID b1 = UUID.randomUUID();
        OwnerId teamA = TestTeams.team("a-" + a1, a1, a2);
        OwnerId teamB = TestTeams.team("b-" + b1, b1);

        m.enqueue(teamA, tech);
        m.enqueue(teamB, tech);
        m.addProgress(teamA, tech, 4);
        m.addProgress(teamB, tech, 1);

        helper.assertTrue(HistoryStagesBridge.individualUnlockReported("hstg_test_teams", a1) && HistoryStagesBridge.individualUnlockReported("hstg_test_teams", a2),
              "team A's members did not get the stage");
        helper.assertTrue(!HistoryStagesBridge.individualUnlockReported("hstg_test_teams", b1), "team B got team A's stage");
        helper.assertTrue(m.book(teamB).progress(tech).orElseThrow().done() == 1, "team B's progress changed");
        helper.assertTrue(m.book(teamB).queue().contains(tech), "team B's queue changed");
        helper.assertTrue(!m.book(teamA).queue().contains(tech), "team A's entry left its queue");
        remove(server, "gametest_teams");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "research")
    public static void joiningMemberAdoptsTheTeamsProgress(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation tech = technology(server, "gametest_join", "hstg_test_join", 10);
        ResearchManager m = ResearchManager.get().orElseThrow();
        UUID newcomer = UUID.randomUUID();
        UUID veteran = UUID.randomUUID();
        OwnerId team = TestTeams.team("t-" + veteran, veteran);

        OwnerId solo = TestTeams.ownerOf(newcomer);
        m.enqueue(solo, tech);
        m.addProgress(solo, tech, 1);
        m.enqueue(team, tech);
        m.addProgress(team, tech, 6);

        TestTeams.join(team.key(), newcomer);
        OwnerId now = TestTeams.ownerOf(newcomer);
        helper.assertTrue(now.equals(team), "joining did not make the team the owner: " + now);
        helper.assertTrue(m.book(now).progress(tech).orElseThrow().done() == 6, "the newcomer does not see the team's progress");
        helper.assertTrue(m.members(team).contains(newcomer), "newcomer is not a member");
        helper.assertTrue(m.book(solo).progress(tech).orElseThrow().done() == 1, "the solo progress is kept, unused (§5.10)");
        remove(server, "gametest_join");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "research")
    public static void offlineMemberGetsTheStage(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation tech = technology(server, "gametest_offline", "hstg_test_offline", 1);
        ResearchManager m = ResearchManager.get().orElseThrow();
        UUID offline = UUID.randomUUID();
        helper.assertTrue(server.getPlayerList().getPlayer(offline) == null, "must be offline");
        OwnerId team = TestTeams.team("o-" + offline, offline);

        m.enqueue(team, tech);
        m.addProgress(team, tech, 1);

        helper.assertTrue(HistoryStagesBridge.individualUnlockReported("hstg_test_offline", offline),
              "HS did not unlock the stage for the offline member (it writes it and syncs at their next login)");
        helper.assertTrue(!m.book(team).queue().contains(tech), "completed technology still queued");
        remove(server, "gametest_offline");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ftbTeamsProvidesTheTeamOwner(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("ftbteams"), "FTB Teams is not loaded in the GameTest run");
        var ownerType = ApiRegistries.OWNER_TYPES.get(BuiltInOwners.TEAM).orElseThrow();
        helper.assertTrue(ownerType.getClass().getName().contains("compat.ftbteams"), "hstg_labs:team comes from " + ownerType.getClass().getName());
        UUID stranger = UUID.randomUUID();
        var members = ownerType.members(new OwnerId(BuiltInOwners.TEAM, stranger.toString()), helper.getLevel().getServer());
        helper.assertTrue(members.size() == 1 && members.contains(stranger), "a player in no team is a team of one: " + members);
        helper.succeed();
    }
}
