package io.github.itsgiantcookie.hstglabs.gametest;

import com.mojang.authlib.GameProfile;
import io.github.itsgiantcookie.hstglabs.HstgLabs;
import io.github.itsgiantcookie.hstglabs.Registration;
import io.github.itsgiantcookie.hstglabs.api.owner.OwnerId;
import io.github.itsgiantcookie.hstglabs.lab.BasicLabBlockEntity;
import io.github.itsgiantcookie.hstglabs.lab.BasicLabMenu;
import io.github.itsgiantcookie.hstglabs.packet.LabStatusPayload;
import io.github.itsgiantcookie.hstglabs.packet.LabStatusPayload.SlotView;
import io.github.itsgiantcookie.hstglabs.research.BuiltInOwners;
import io.github.itsgiantcookie.hstglabs.research.ResearchManager;
import io.github.itsgiantcookie.hstglabs.research.TechReloader;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import io.github.itsgiantcookie.hstglabs.mek.config.PortType;
import io.github.itsgiantcookie.hstglabs.mek.config.TransmissionKind;
import io.github.itsgiantcookie.hstglabs.packet.SideConfigPayload;
import mekanism.api.Action;
import mekanism.api.RelativeSide;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * WP-10 lab screen, server half: the {@link LabStatusPayload} codec, what a viewer is shown of a
 * running lab, and shift-click routing in {@link BasicLabMenu}. The screen itself is on Hendrik's
 * client checklist.
 */
@GameTestHolder(HstgLabs.MODID)
@PrefixGameTestTemplate(false)
public final class LabMenuTests {

    private static final BlockPos LAB = new BlockPos(1, 2, 1);
    private static final ResourceLocation ENERGY_TABLET = ResourceLocation.fromNamespaceAndPath("mekanism", "energy_tablet");

    private LabMenuTests() {
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "hstg_tester"),
              ClientInformation.createDefault());
    }

    @GameTest(template = "empty")
    public static void labStatusCodecRoundTrips(GameTestHelper helper) {
        LabStatusPayload status = new LabStatusPayload(5, 12_345_678_901L, 40_000, 20, 2, 1.5, true, List.of(
              new SlotView(Optional.of(HstgLabs.rl("t")), 7, 20, "RUNNING", "", ""),
              new SlotView(Optional.empty(), 0, 0, "IDLE", "hstg_labs:missing_ingredients", "minecraft:paper")),
              List.of(1, 2, 0, 3, 4, 0, 1, 1, 0, 0, 0, 1)); // FRONT, LEFT, RIGHT, BACK, TOP, BOTTOM per kind
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        LabStatusPayload.STREAM_CODEC.encode(buf, status);
        LabStatusPayload back = LabStatusPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(back.equals(status), "round trip: " + back);
        helper.assertTrue(buf.readableBytes() == 0, "bytes left behind");
        helper.assertTrue(Math.abs(back.slots().get(0).unitProgress() - 0.65) < 1e-9 && back.slots().get(1).unitProgress() == 0, "unit progress");
        helper.assertTrue(back.port(TransmissionKind.ITEM, RelativeSide.FRONT) == PortType.INPUT
              && back.port(TransmissionKind.ITEM, RelativeSide.LEFT) == PortType.OUTPUT
              && back.port(TransmissionKind.ENERGY, RelativeSide.BOTTOM) == PortType.INPUT
              && back.port(TransmissionKind.DATA, RelativeSide.FRONT) == PortType.NONE, "faces");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "lab_menu", timeoutTicks = 200)
    public static void statusShowsTheRunningUnitAndTheOwner(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String name = "gametest_lab_status";
        try {
            Path file = TechReloader.configDir().resolve("technologies/" + name + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, """
                  { "stage": "hstg_test_queue_open", "cost": { "units": 5, "unit_time": 400,
                    "ingredients": [ { "type": "hstg_labs:item", "item": "minecraft:paper", "amount": 1 } ] } }
                  """, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        TechReloader.reload(server, "gametest");
        ResourceLocation tech = HstgLabs.rl(name);
        helper.setBlock(LAB, Registration.BASIC_LAB.get());
        BasicLabBlockEntity lab = (BasicLabBlockEntity) helper.getBlockEntity(LAB);
        ResearchManager m = ResearchManager.get().orElseThrow();
        OwnerId owner = new OwnerId(BuiltInOwners.SERVER, "gametest_lab_status");
        m.forgetOwner(owner);
        lab.setOwner(owner);
        helper.assertTrue(m.enqueue(owner, tech) == ResearchManager.EnqueueResult.ADDED, "enqueue");
        lab.strictEnergy().insertEnergy(10_000, Action.EXECUTE);
        lab.items().insertItem(0, new ItemStack(Items.PAPER, 2), false);
        ServerPlayer viewer = player(helper);

        helper.succeedWhen(() -> {
            LabStatusPayload s = LabStatusPayload.of(1, lab, viewer);
            helper.assertTrue(!s.slots().isEmpty() && s.slots().get(0).technology().equals(Optional.of(tech)), "not running yet: " + s);
            SlotView slot = s.slots().get(0);
            helper.assertTrue(slot.ticksTotal() == 400 && slot.ticksLeft() <= 400 && slot.reason().isEmpty(), "slot: " + slot);
            helper.assertTrue(!s.ownedByViewer(), "the viewer's owner is not the lab's");
            helper.assertTrue(s.tier() == 1 && s.capacity() == lab.energyCapacity(), "tier and capacity: " + s);
            lab.setOwner(m.ownerOf(viewer));
            helper.assertTrue(LabStatusPayload.of(1, lab, viewer).ownedByViewer(), "owned once the lab is the viewer's owner's");
            lab.setOwner(owner);
            m.forgetOwner(owner);
            try {
                Files.deleteIfExists(TechReloader.configDir().resolve("technologies/" + name + ".json"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            TechReloader.reload(server, "gametest cleanup");
        });
    }

    @GameTest(template = "empty")
    public static void shiftClickRoutesUpgradesAndRefusesOthers(GameTestHelper helper) {
        helper.setBlock(LAB, Registration.BASIC_LAB.get());
        BasicLabBlockEntity lab = (BasicLabBlockEntity) helper.getBlockEntity(LAB);
        ServerPlayer player = player(helper);
        BasicLabMenu menu = new BasicLabMenu(1, player.getInventory(), lab);
        int hotbar0 = menu.itemSlots() + BasicLabMenu.EXTRA_SLOTS + 27;
        helper.assertTrue(menu.slots.size() == hotbar0 + 9, "slot count " + menu.slots.size());

        player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.get(BasicLabBlockEntity.SPEED_UPGRADE), 3));
        menu.quickMoveStack(player, hotbar0);
        helper.assertTrue(lab.upgrades().getStackInSlot(0).getCount() == 3 && player.getInventory().getItem(0).isEmpty(), "speed upgrade moved");

        player.getInventory().setItem(1, new ItemStack(Items.DIRT, 4));
        menu.quickMoveStack(player, hotbar0 + 1);
        helper.assertTrue(player.getInventory().getItem(1).getCount() == 4, "dirt is no ingredient and no upgrade, it stays");

        player.getInventory().setItem(2, new ItemStack(BuiltInRegistries.ITEM.get(ENERGY_TABLET)));
        menu.quickMoveStack(player, hotbar0 + 2);
        helper.assertTrue(BuiltInRegistries.ITEM.getKey(lab.energyItem().getStackInSlot(0).getItem()).equals(ENERGY_TABLET),
              "an energy item goes to the energy slot");

        menu.quickMoveStack(player, menu.itemSlots());
        helper.assertTrue(lab.upgrades().getStackInSlot(0).isEmpty(), "upgrade shift-clicked back out");
        helper.assertTrue(menu.stillValid(player) == player.distanceToSqr(helper.absoluteVec(LAB.getCenter())) <= 64, "stillValid by distance");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void energySlotChargesTheLab(GameTestHelper helper) {
        helper.setBlock(LAB, Registration.BASIC_LAB.get());
        BasicLabBlockEntity lab = (BasicLabBlockEntity) helper.getBlockEntity(LAB);
        lab.setOwner(new OwnerId(BuiltInOwners.SERVER, "gametest_energy_slot"));
        ItemStack tablet = new ItemStack(BuiltInRegistries.ITEM.get(ENERGY_TABLET));
        var handler = tablet.getCapability(io.github.itsgiantcookie.hstglabs.ModCapabilities.STRICT_ENERGY_ITEM);
        helper.assertTrue(handler != null, "the energy tablet has Mekanism's item energy capability");
        handler.insertEnergy(5_000, Action.EXECUTE);
        long inTablet = handler.getEnergy(0);
        helper.assertTrue(inTablet > 0, "tablet charged: " + inTablet);
        helper.assertTrue(lab.energyItem().insertItem(0, tablet, false).isEmpty(), "the tablet fits the energy slot");
        helper.assertTrue(lab.energyItem().insertItem(0, new ItemStack(Items.PAPER), true).getCount() == 1, "paper is no energy item");
        helper.succeedWhen(() -> {
            long tabletLeft = lab.energyItem().getStackInSlot(0).getCapability(io.github.itsgiantcookie.hstglabs.ModCapabilities.STRICT_ENERGY_ITEM)
                  .getEnergy(0);
            helper.assertTrue(lab.energy() > 0 && lab.energy() + tabletLeft >= inTablet - lab.energyPerTick() * 40,
                  "lab " + lab.energy() + " tablet " + tabletLeft);
            helper.assertTrue(tabletLeft < inTablet, "the tablet was drained");
        });
    }

    @GameTest(template = "empty")
    public static void sideConfigDecidesWhichFacesTakeItemsAndEnergy(GameTestHelper helper) {
        helper.setBlock(LAB, Registration.BASIC_LAB.get());
        BasicLabBlockEntity lab = (BasicLabBlockEntity) helper.getBlockEntity(LAB);
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(LAB);
        Direction facing = lab.getBlockState().getValue(io.github.itsgiantcookie.hstglabs.lab.BasicLabBlock.FACING);
        Direction top = Direction.UP;
        helper.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, abs, top) != null, "by default every face takes items");
        helper.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, abs, top).extractItem(0, 1, true).isEmpty()
              || lab.items().getStackInSlot(0).isEmpty(), "an input face does not hand items out");

        ServerPlayer player = player(helper);
        player.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 0.5); // within reach, so the menu stays valid
        BasicLabMenu menu = new BasicLabMenu(7, player.getInventory(), lab);
        player.containerMenu = menu;
        // item TOP: INPUT -> OUTPUT -> INPUT_OUTPUT -> ENERGY -> NONE
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(SideConfigPayload.apply(player, new SideConfigPayload(7, TransmissionKind.ITEM, Optional.of(RelativeSide.TOP), true)),
                  "cycle " + i);
        }
        helper.assertTrue(lab.port(TransmissionKind.ITEM, top) == PortType.NONE, "top cycled to none: " + lab.port(TransmissionKind.ITEM, top));
        helper.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, abs, top) == null, "a NONE face has no item handler");
        helper.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, abs, facing) != null, "the front is untouched");

        SideConfigPayload.apply(player, new SideConfigPayload(7, TransmissionKind.ENERGY, Optional.empty(), true));
        helper.assertTrue(level.getCapability(io.github.itsgiantcookie.hstglabs.ModCapabilities.STRICT_ENERGY, abs, facing) == null
              && level.getCapability(Capabilities.EnergyStorage.BLOCK, abs, facing) == null, "clear sides: no face takes energy");
        helper.assertTrue(!SideConfigPayload.apply(player, new SideConfigPayload(7, TransmissionKind.DATA, Optional.empty(), true)),
              "the basic lab has no data faces (D13)");
        helper.assertTrue(!SideConfigPayload.apply(player, new SideConfigPayload(8, TransmissionKind.ITEM, Optional.empty(), true)),
              "another container id is refused");

        LabStatusPayload status = LabStatusPayload.of(7, lab, player);
        helper.assertTrue(status.port(TransmissionKind.ITEM, RelativeSide.TOP) == PortType.NONE
              && status.port(TransmissionKind.ENERGY, RelativeSide.FRONT) == PortType.NONE
              && status.port(TransmissionKind.ITEM, RelativeSide.FRONT) == PortType.INPUT, "status carries the faces");

        var saved = lab.saveWithoutMetadata(level.registryAccess());
        helper.setBlock(LAB, Registration.BASIC_LAB.get());
        BasicLabBlockEntity reloaded = (BasicLabBlockEntity) helper.getBlockEntity(LAB);
        reloaded.loadWithComponents(saved, level.registryAccess());
        helper.assertTrue(reloaded.sides().get(TransmissionKind.ITEM, RelativeSide.TOP) == PortType.NONE, "side config is saved");
        helper.succeed();
    }
}
