package io.github.itsgiantcookie.hstglabs.gametest;

import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.Registration;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import io.github.itsgiantcookie.hstglabs.core.IdleReason;
import io.github.itsgiantcookie.hstglabs.core.ResearchProgress;
import io.github.itsgiantcookie.hstglabs.hs.HistoryStagesBridge;
import io.github.itsgiantcookie.hstglabs.lab.BasicLabBlockEntity;
import io.github.itsgiantcookie.hstglabs.lab.LabTypes;
import io.github.itsgiantcookie.hstglabs.research.BuiltInOwners;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager;
import io.github.itsgiantcookie.hstglabs.research.TechReloader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import mekanism.api.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * WP-09 acceptance (docs/design.md §13): a hopper feeds packs; one speed-1 lab finishes U units in
 * U × T ticks; breaking it mid-unit credits nothing; {@code /reload} changes lab speed; a basic lab
 * idles on a gated technology with {@code LAB_REQUIREMENTS} (D18: it never skips the head). Each test
 * has its own fixture stage and owner.
 */
@GameTestHolder(HstgLabs.MODID)
@PrefixGameTestTemplate(false)
public final class BasicLabTests {

    private static final BlockPos LAB = new BlockPos(1, 2, 1);

    private BasicLabTests() {
    }

    private static ResourceLocation technology(MinecraftServer server, String name, String stage, int units, int unitTime, String labs) {
        try {
            Path file = TechReloader.configDir().resolve("technologies/" + name + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, """
                  { "stage": "%s", "cost": { "units": %d, "unit_time": %d,
                    "ingredients": [ { "type": "hstg_labs:item", "item": "minecraft:paper", "amount": 1 } ] }%s }
                  """.formatted(stage, units, unitTime, labs), StandardCharsets.UTF_8);
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

    /** A basic lab owned by a fresh owner with {@code tech} queued and energy inside. */
    private static BasicLabBlockEntity lab(GameTestHelper helper, String ownerKey, ResourceLocation tech) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(TechReloader.current().technologies().containsKey(tech), "technology not enabled: " + TechReloader.current().problems());
        helper.setBlock(LAB, Registration.BASIC_LAB.get());
        BasicLabBlockEntity lab = (BasicLabBlockEntity) helper.getBlockEntity(LAB);
        OwnerId owner = new OwnerId(BuiltInOwners.SERVER, ownerKey);
        ResearchManager m = ResearchManager.get().orElseThrow();
        m.forgetOwner(owner);
        lab.setOwner(owner);
        helper.assertTrue(m.enqueue(owner, tech) == ResearchManager.EnqueueResult.ADDED, "enqueue " + tech);
        lab.strictEnergy().insertEnergy(10_000, Action.EXECUTE);
        return lab;
    }

    private static ResearchProgress progress(BasicLabBlockEntity lab, ResourceLocation tech) {
        return ResearchManager.get().orElseThrow().book(lab.owner().orElseThrow()).progress(tech).orElse(ResearchProgress.restore(1, 0, 0));
    }

    @GameTest(template = "empty", batch = "lab")
    public static void hopperFeedsPacks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation tech = technology(server, "gametest_lab_feed", "hstg_test_lab_feed", 50, 200, "");
        BasicLabBlockEntity lab = lab(helper, "gametest_lab_feed", tech);
        BlockPos hopperPos = LAB.above();
        helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        ((HopperBlockEntity) helper.getBlockEntity(hopperPos)).setItem(0, new ItemStack(Items.PAPER, 5));
        helper.succeedWhen(() -> {
            int inLab = 0;
            for (int i = 0; i < lab.items().getSlots(); i++) {
                inLab += lab.items().getStackInSlot(i).getCount();
            }
            ResearchProgress p = progress(lab, tech);
            helper.assertTrue(inLab + p.inFlight() + p.done() >= 5, "hopper has not delivered all packs yet: " + inLab);
            helper.assertTrue(p.inFlight() == 1, "the lab did not start a unit with the delivered packs");
            remove(server, "gametest_lab_feed");
        });
    }

    @GameTest(template = "empty", batch = "lab", timeoutTicks = 300)
    public static void speedOneLabFinishesUUnitsInUTimesTTicks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        HistoryStagesBridge.relockGlobalForTest("hstg_test_lab_time", server);
        ResourceLocation tech = technology(server, "gametest_lab_time", "hstg_test_lab_time", 3, 20, "");
        BasicLabBlockEntity lab = lab(helper, "gametest_lab_time", tech);
        lab.items().insertItem(0, new ItemStack(Items.PAPER, 3), false);
        long[] start = {-1};
        long[] end = {-1};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            ResearchProgress p = progress(lab, tech);
            if (start[0] < 0 && p.inFlight() == 1) {
                start[0] = now;
            }
            if (end[0] < 0 && HistoryStagesBridge.INSTANCE.isUnlockedGlobal("hstg_test_lab_time", server)) {
                end[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(end[0] >= 0, "not finished yet");
            helper.assertTrue(lab.speed(tech) == 1.0, "speed is " + lab.speed(tech));
            helper.assertTrue(end[0] - start[0] == 3 * 20, "3 units of 20 ticks took " + (end[0] - start[0]) + " ticks");
            remove(server, "gametest_lab_time");
        });
    }

    @GameTest(template = "empty", batch = "lab")
    public static void breakingMidUnitCreditsNothing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation tech = technology(server, "gametest_lab_break", "hstg_test_lab_break", 5, 200, "");
        BasicLabBlockEntity lab = lab(helper, "gametest_lab_break", tech);
        lab.items().insertItem(0, new ItemStack(Items.PAPER, 3), false);
        helper.startSequence()
              .thenWaitUntil(() -> helper.assertTrue(progress(lab, tech).inFlight() == 1, "no unit started"))
              .thenExecute(() -> helper.destroyBlock(LAB))
              .thenWaitUntil(() -> {
                  ResearchProgress p = progress(lab, tech);
                  helper.assertTrue(p.done() == 0 && p.inFlight() == 0, "breaking mid-unit must credit nothing and free the claim: " + p);
                  int paper = helper.getEntities(EntityType.ITEM, LAB, 4.0).stream().map(ItemEntity::getItem).filter(s -> s.is(Items.PAPER))
                        .mapToInt(ItemStack::getCount).sum();
                  helper.assertTrue(paper == 3, "all three packs must drop, the consumed one included: " + paper);
              })
              .thenExecute(() -> remove(server, "gametest_lab_break"))
              .thenSucceed();
    }

    @GameTest(template = "empty", batch = "lab")
    public static void basicLabIdlesOnAGatedHeadWithLabRequirements(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation gated = technology(server, "gametest_lab_gated", "hstg_test_lab_gated", 5, 20,
              ", \"labs\": { \"requires\": [ { \"type\": \"hstg_labs:network\" } ] }");
        BasicLabBlockEntity lab = lab(helper, "gametest_lab_gated", gated);
        lab.items().insertItem(0, new ItemStack(Items.PAPER, 3), false);
        helper.runAfterDelay(10, () -> {
            Optional<IdleReason> reason = lab.firstIdleReason();
            helper.assertTrue(reason.map(r -> r.id().equals(IdleReason.LAB_REQUIREMENTS)).orElse(false), "idle reason: " + reason);
            helper.assertTrue(lab.items().getStackInSlot(0).getCount() == 3, "a gated head must not consume packs");
            helper.assertTrue(progress(lab, gated).inFlight() == 0, "no unit may start");
            remove(server, "gametest_lab_gated");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "lab_reload", timeoutTicks = 400)
    public static void reloadChangesLabSpeed(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path override = TechReloader.configDir().resolve("lab_types/basic_lab.json");
        ResourceLocation basicLab = HstgLabs.rl("basic_lab");
        helper.setBlock(LAB, Registration.BASIC_LAB.get());
        BasicLabBlockEntity lab = (BasicLabBlockEntity) helper.getBlockEntity(LAB);
        helper.assertTrue(lab.speed(null) == 1.0, "starts at the shipped speed 1.0, was " + lab.speed(null));
        try {
            Files.createDirectories(override.getParent());
            Files.writeString(override, "{ \"speed\": 2.0 }", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "reload");
        helper.startSequence()
              .thenWaitUntil(() -> helper.assertTrue(LabTypes.get(basicLab).map(t -> t.speed() == 2.0).orElse(false), "override not applied yet"))
              .thenExecute(() -> {
                  helper.assertTrue(lab.speed(null) == 2.0, "the placed lab does not run at the new speed: " + lab.speed(null));
                  try {
                      Files.deleteIfExists(override);
                  } catch (IOException e) {
                      throw new UncheckedIOException(e);
                  }
                  server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "reload");
              })
              .thenWaitUntil(() -> helper.assertTrue(LabTypes.get(basicLab).map(t -> t.speed() == 1.0).orElse(false), "back to 1.0"))
              .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void labBlockEntityTypeCollectsEveryLabBlock(GameTestHelper helper) {
        helper.assertTrue(Registration.BASIC_LAB_BE.get().isValid(Registration.BASIC_LAB.get().defaultBlockState()),
              "the collected block entity type does not accept the basic lab");
        helper.succeed();
    }
}
