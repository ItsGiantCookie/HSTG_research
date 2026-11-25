package io.github.itsgiantcookie.hstglabs.lab;

import io.github.itsgiantcookie.hstglabs.Registration;
import io.github.itsgiantcookie.hstglabs.packet.LabStatusPayload;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The basic lab's menu: its consumer item slots, the speed and energy upgrade slots, and the player
 * inventory. Everything else the screen shows arrives as {@link LabStatusPayload}, sent every
 * {@link #STATUS_INTERVAL} ticks while the menu is open, and as research snapshots.
 */
public class BasicLabMenu extends AbstractContainerMenu {

    public static final int WIDTH = 256, HEIGHT = 240;
    /** Item position of the first lab slot; slots run in rows of {@link #ROW}. */
    public static final int ITEMS_X = 25, ITEMS_Y = 19, ROW = 6, MAX_ITEM_SLOTS = 12;
    public static final int UPGRADES_X = 25, UPGRADES_Y = 59;
    /** Below the energy bar. */
    public static final int ENERGY_X = 6, ENERGY_Y = 73;
    /** Upgrade slots and the energy slot, after the lab's item slots. */
    public static final int EXTRA_SLOTS = 3;
    public static final int INVENTORY_X = 48, INVENTORY_Y = 158;
    public static final int STATUS_INTERVAL = 5;

    private final BlockPos pos;
    private final int itemSlots;
    @Nullable
    private final BasicLabBlockEntity lab;
    private final Player player;
    private int sinceStatus = STATUS_INTERVAL;

    /** Server side. */
    public BasicLabMenu(int id, Inventory inventory, BasicLabBlockEntity lab) {
        this(id, inventory, lab.getBlockPos(), lab.items(), lab.upgrades(), lab.energyItem(), lab);
    }

    /** Client side: the server wrote the position and slot count ({@link #writeOpenData}). */
    public BasicLabMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), new ItemStackHandler(Math.min(MAX_ITEM_SLOTS, buf.readVarInt())), new ItemStackHandler(2), new ItemStackHandler(1),
              null);
    }

    private BasicLabMenu(int id, Inventory inventory, BlockPos pos, IItemHandler items, IItemHandler upgrades, IItemHandler energyItem,
          @Nullable BasicLabBlockEntity lab) {
        super(Registration.BASIC_LAB_MENU.get(), id);
        this.pos = pos;
        this.lab = lab;
        this.player = inventory.player;
        this.itemSlots = Math.min(MAX_ITEM_SLOTS, items.getSlots());
        for (int i = 0; i < itemSlots; i++) {
            addSlot(new SlotItemHandler(items, i, ITEMS_X + (i % ROW) * 18, ITEMS_Y + (i / ROW) * 18));
        }
        for (int i = 0; i < 2; i++) {
            addSlot(new SlotItemHandler(upgrades, i, UPGRADES_X + i * 18, UPGRADES_Y));
        }
        addSlot(new SlotItemHandler(energyItem, 0, ENERGY_X, ENERGY_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    public static void writeOpenData(BasicLabBlockEntity lab, RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(lab.getBlockPos());
        buf.writeVarInt(lab.items().getSlots());
    }

    public BlockPos pos() {
        return pos;
    }

    public int itemSlots() {
        return itemSlots;
    }

    /** The lab on the server; empty on the client. */
    public Optional<BasicLabBlockEntity> lab() {
        return Optional.ofNullable(lab);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof BasicLabBlockEntity
              && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (lab != null && player instanceof ServerPlayer sp && ++sinceStatus >= STATUS_INTERVAL) {
            sinceStatus = 0;
            PacketDistributor.sendToPlayer(sp, LabStatusPayload.of(containerId, lab, sp));
        }
    }

    /** The menu index of the energy slot. */
    public int energySlot() {
        return itemSlots + 2;
    }

    /** Shift-click: lab slots go to the inventory; inventory items go to the upgrade and energy slots first, then the lab slots. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int labEnd = itemSlots + EXTRA_SLOTS;
        if (index < labEnd) {
            if (!moveItemStackTo(stack, labEnd, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, itemSlots, labEnd, false) && !moveItemStackTo(stack, 0, itemSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return Objects.requireNonNull(copy);
    }
}
