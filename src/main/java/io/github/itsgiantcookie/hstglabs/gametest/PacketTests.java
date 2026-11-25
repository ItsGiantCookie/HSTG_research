package io.github.itsgiantcookie.hstglabs.gametest;

import com.mojang.authlib.GameProfile;
import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.packet.QueueEditPayload;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.IngredientView;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.ProgressView;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.QueueView;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.TechState;
import io.github.itsgiantcookie.hstglabs.packet.ResearchViews.TechView;
import io.github.itsgiantcookie.hstglabs.packet.TechSnapshotPayload;
import io.github.itsgiantcookie.hstglabs.research.LabsConfig;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager.EnqueueResult;
import io.github.itsgiantcookie.hstglabs.research.TechReloader;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * WP-10 acceptance (docs/design.md §13): codec round trips; {@code QueueEdit} refuses a locked
 * technology and a player without permission. The player is a plain {@link ServerPlayer} that never
 * joins (no login events), which is all the server-side checks need.
 */
@GameTestHolder(HstgLabs.MODID)
@PrefixGameTestTemplate(false)
public final class PacketTests {

    private PacketTests() {
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "hstg_tester"),
              ClientInformation.createDefault());
    }

    private static ResourceLocation technology(MinecraftServer server, String name, String stage) {
        try {
            Path file = TechReloader.configDir().resolve("technologies/" + name + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, """
                  { "stage": "%s", "cost": { "units": 2, "unit_time": 20,
                    "ingredients": [ { "type": "hstg_labs:item", "item": "minecraft:paper", "amount": 1 } ] } }
                  """.formatted(stage), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        TechReloader.reload(server, "gametest");
        return HstgLabs.rl(name);
    }

    private static void remove(MinecraftServer server, String... names) {
        try {
            for (String name : names) {
                Files.deleteIfExists(TechReloader.configDir().resolve("technologies/" + name + ".json"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        TechReloader.reload(server, "gametest cleanup");
    }

    @GameTest(template = "empty")
    public static void snapshotAndQueueEditCodecsRoundTrip(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        TechView view = new TechView(HstgLabs.rl("t"), Component.literal("Steam Power"), new ItemStack(Items.PISTON), 120, 600, 2,
              List.of(new IngredientView(new ItemStack(Items.PAPER), Component.literal("Paper"), 3)), TechState.AVAILABLE,
              List.of(HstgLabs.rl("before")), List.of(new ItemStack(Items.LECTERN)));
        QueueView queue = new QueueView(List.of(HstgLabs.rl("t")), 7, List.of(new ProgressView(HstgLabs.rl("t"), 4, 120, 1)), List.of(HstgLabs.rl("b")));
        TechSnapshotPayload snapshot = new TechSnapshotPayload(List.of(view), queue);

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        TechSnapshotPayload.STREAM_CODEC.encode(buf, snapshot);
        TechSnapshotPayload back = TechSnapshotPayload.STREAM_CODEC.decode(buf);
        TechView v = back.technologies().get(0);
        helper.assertTrue(v.id().equals(view.id()) && v.name().getString().equals("Steam Power") && v.icon().is(Items.PISTON), "tech header");
        helper.assertTrue(v.units() == 120 && v.unitTime() == 600 && v.minTier() == 2 && v.state() == TechState.AVAILABLE, "tech numbers");
        helper.assertTrue(v.ingredients().get(0).amount() == 3 && v.ingredients().get(0).icon().is(Items.PAPER), "ingredients");
        helper.assertTrue(v.prerequisites().equals(view.prerequisites()) && v.preview().get(0).is(Items.LECTERN), "prerequisites and preview");
        helper.assertTrue(back.queue().equals(queue), "queue: " + back.queue());
        helper.assertTrue(buf.readableBytes() == 0, "snapshot left bytes behind");

        QueueEditPayload edit = new QueueEditPayload(QueueEditPayload.Op.MOVE, HstgLabs.rl("t"), 3);
        RegistryFriendlyByteBuf buf2 = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        QueueEditPayload.STREAM_CODEC.encode(buf2, edit);
        helper.assertTrue(QueueEditPayload.STREAM_CODEC.decode(buf2).equals(edit), "queue edit");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "queue_edit")
    public static void queueEditRefusesALockedTechnology(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        technology(server, "gametest_queue_open", "hstg_test_queue_open");
        ResourceLocation locked = technology(server, "gametest_queue_locked", "hstg_test_queue_locked");
        ResourceLocation open = HstgLabs.rl("gametest_queue_open");
        ResearchManager m = ResearchManager.get().orElseThrow();
        ServerPlayer player = player(helper);

        QueueEditPayload.Result refused = QueueEditPayload.apply(m, player, new QueueEditPayload(QueueEditPayload.Op.ADD, locked, 0));
        helper.assertTrue(!refused.ok() && refused.code() == EnqueueResult.PREREQUISITES_MISSING, "locked: " + refused);
        helper.assertTrue(!m.book(m.ownerOf(player)).queue().contains(locked), "the locked technology was queued");

        QueueEditPayload.Result added = QueueEditPayload.apply(m, player, new QueueEditPayload(QueueEditPayload.Op.ADD, open, 0));
        helper.assertTrue(added.ok(), "a reachable technology must queue: " + added);
        var snapshot = TechSnapshotPayload.build(m, player);
        helper.assertTrue(snapshot.technologies().stream().anyMatch(t -> t.id().equals(locked) && t.state() == TechState.PREREQUISITES_MISSING),
              "snapshot shows the locked state");
        helper.assertTrue(snapshot.queue().entries().contains(open), "snapshot shows the queue");
        remove(server, "gametest_queue_open", "gametest_queue_locked");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "queue_edit")
    public static void queueEditRefusesAPlayerWithoutPermission(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation open = technology(server, "gametest_queue_perm", "hstg_test_queue_open");
        ResearchManager m = ResearchManager.get().orElseThrow();
        ServerPlayer player = player(helper);
        LabsConfig.QueuePermission before = LabsConfig.QUEUE_PERMISSION.get();
        try {
            LabsConfig.QUEUE_PERMISSION.set(LabsConfig.QueuePermission.OPS);
            QueueEditPayload.Result refused = QueueEditPayload.apply(m, player, new QueueEditPayload(QueueEditPayload.Op.ADD, open, 0));
            helper.assertTrue(!refused.ok() && refused.code() == EnqueueResult.NO_PERMISSION, "non-op with queue_permission = OPS: " + refused);
            QueueEditPayload.Result remove = QueueEditPayload.apply(m, player, new QueueEditPayload(QueueEditPayload.Op.REMOVE, open, 0));
            helper.assertTrue(remove.code() == EnqueueResult.NO_PERMISSION, "remove must be refused too: " + remove);
        } finally {
            LabsConfig.QUEUE_PERMISSION.set(before);
            remove(server, "gametest_queue_perm");
        }
        helper.succeed();
    }
}
