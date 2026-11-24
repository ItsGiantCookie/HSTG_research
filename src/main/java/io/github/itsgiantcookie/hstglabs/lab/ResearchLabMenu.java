package io.github.itsgiantcookie.hstglabs.lab;

import io.github.itsgiantcookie.hstglabs.Registration;
import io.github.itsgiantcookie.hstglabs.mek.UpgradeComponent;
import mekanism.api.Upgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

public class ResearchLabMenu extends AbstractContainerMenu {

    /**
     * Slot positions (top-left of the item; the 18x18 frame sits 1 px outside).
     * Same layout as Mekanism's electric machines (TileEntityElectricMachine): input above the
     * energy slot, output to the right.
     */
    public static final int INPUT_X = 64, INPUT_Y = 17, OUTPUT_X = 116, OUTPUT_Y = 35, ENERGY_X = 64, ENERGY_Y = 53;
    /** Mekanism's MekanismContainer.BASE_Y_OFFSET. */
    public static final int PLAYER_INV_Y = 84;
    /**
     * Upgrade window (Mekanism GuiUpgradeWindow, 198 wide at ((176-198)/2, 15)): its two virtual slots
     * sit at window-relative (175,18) and (175,72), i.e. GUI-relative items at (165,34) and (165,88).
     */
    public static final int UPGRADE_WINDOW_X = -11, UPGRADE_WINDOW_Y = 15, UPGRADE_WINDOW_W = 198, UPGRADE_WINDOW_H = 94;
    public static final int UPGRADE_SLOT_X = UPGRADE_WINDOW_X + 176, UPGRADE_IN_Y = UPGRADE_WINDOW_Y + 19, UPGRADE_OUT_Y = UPGRADE_WINDOW_Y + 73;

    public static final int SLOT_UPGRADE_IN = ResearchLabBlockEntity.SLOT_COUNT;
    public static final int SLOT_UPGRADE_OUT = SLOT_UPGRADE_IN + 1;
    private static final int MACHINE_SLOTS = SLOT_UPGRADE_OUT + 1;

    private final BlockPos pos;
    private final Level level;
    @Nullable
    private final ResearchLabBlockEntity lab;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    // client-only window state, set by the screen: slots under an open window are inactive (not drawn,
    // not clickable), the upgrade slots are only active while the upgrade window is open
    @Nullable
    private int[] coveredArea;
    private boolean upgradeSlotsShown;

    /** Client side, opened from the server's openMenu(provider, pos). */
    public ResearchLabMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(id, playerInv, buf.readBlockPos(), new SimpleContainerData(LabData.COUNT));
    }

    public ResearchLabMenu(int id, Inventory playerInv, BlockPos pos, ContainerData data) {
        super(Registration.RESEARCH_LAB_MENU.get(), id);
        this.pos = pos;
        this.level = playerInv.player.level();
        this.lab = level.getBlockEntity(pos) instanceof ResearchLabBlockEntity be ? be : null;
        this.access = ContainerLevelAccess.create(level, pos);
        this.data = data;
        checkContainerDataCount(data, LabData.COUNT);

        IItemHandler inventory = lab != null ? lab.getInventory() : new ItemStackHandler(ResearchLabBlockEntity.SLOT_COUNT);
        addSlot(new MachineSlot(inventory, ResearchLabBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y, true));
        addSlot(new MachineSlot(inventory, ResearchLabBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y, false));
        addSlot(new MachineSlot(inventory, ResearchLabBlockEntity.SLOT_ENERGY, ENERGY_X, ENERGY_Y, true));

        IItemHandler upgrades = lab != null ? lab.getUpgrades().getSlots() : new UpgradeComponent(() -> {
        }).getSlots();
        addSlot(new UpgradeSlot(upgrades, UpgradeComponent.SLOT_INSTALL, UPGRADE_SLOT_X, UPGRADE_IN_Y, true));
        addSlot(new UpgradeSlot(upgrades, UpgradeComponent.SLOT_REMOVED, UPGRADE_SLOT_X, UPGRADE_OUT_Y, false));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new PlayerSlot(playerInv, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new PlayerSlot(playerInv, col, 8 + col * 18, PLAYER_INV_Y + 58));
        }
        addDataSlots(data);
    }

    // --- client window state ---

    /** @param area GUI-relative {x, y, width, height} of the open window, or null. */
    public void setWindowState(@Nullable int[] area, boolean upgradeSlotsShown) {
        this.coveredArea = area;
        this.upgradeSlotsShown = upgradeSlotsShown;
    }

    private boolean isCovered(Slot slot) {
        int[] a = coveredArea;
        return a != null && slot.x - 1 < a[0] + a[2] && slot.x + 17 > a[0] && slot.y - 1 < a[1] + a[3] && slot.y + 17 > a[1];
    }

    // --- accessors ---

    public BlockPos getPos() {
        return pos;
    }

    @Nullable
    public ResearchLabBlockEntity getLab() {
        return lab;
    }

    public long getEnergy() {
        return LabData.readLong(data, LabData.ENERGY);
    }

    public long getMaxEnergy() {
        return LabData.readLong(data, LabData.MAX_ENERGY);
    }

    public long getStoredData() {
        return LabData.readLong(data, LabData.DATA);
    }

    /** Recipe progress, 0..1. */
    public double getProgress() {
        return data.get(LabData.PROGRESS) / (double) LabData.PROGRESS_SCALE;
    }

    /** Joules used in the last tick (Mekanism energy tab "Using"). */
    public long getEnergyUsed() {
        return LabData.readLong(data, LabData.ENERGY_USED);
    }

    public int getUpgrades(Upgrade upgrade) {
        return switch (upgrade) {
            case SPEED -> data.get(LabData.SPEED_UPGRADES);
            case ENERGY -> data.get(LabData.ENERGY_UPGRADES);
            default -> 0;
        };
    }

    /** Install progress of the upgrade slot, 0..1. */
    public double getUpgradeProgress() {
        return data.get(LabData.UPGRADE_TICKS) / (double) UpgradeComponent.INSTALL_TICKS;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, Registration.RESEARCH_LAB.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveToMachine(stack)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    private boolean moveToMachine(ItemStack stack) {
        Upgrade upgrade = UpgradeComponent.upgradeOf(stack);
        if (upgrade != null) {
            return UpgradeComponent.SUPPORTED.contains(upgrade) && moveItemStackTo(stack, SLOT_UPGRADE_IN, SLOT_UPGRADE_IN + 1, false);
        } else if (ResearchLabBlockEntity.isEnergyItem(stack)) {
            return moveItemStackTo(stack, ResearchLabBlockEntity.SLOT_ENERGY, ResearchLabBlockEntity.SLOT_ENERGY + 1, false);
        } else if (ResearchLabBlockEntity.isValidInput(level, stack)) {
            return moveItemStackTo(stack, ResearchLabBlockEntity.SLOT_INPUT, ResearchLabBlockEntity.SLOT_INPUT + 1, false);
        }
        return false;
    }

    // --- slots ---

    private final class MachineSlot extends SlotItemHandler {

        private final boolean insertable;

        private MachineSlot(IItemHandler handler, int index, int x, int y, boolean insertable) {
            super(handler, index, x, y);
            this.insertable = insertable;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return insertable && super.mayPlace(stack);
        }

        @Override
        public boolean isActive() {
            return !isCovered(this);
        }
    }

    /** Mekanism's virtual upgrade slots: only present while the upgrade window is open. */
    private final class UpgradeSlot extends SlotItemHandler {

        private final boolean insertable;

        private UpgradeSlot(IItemHandler handler, int index, int x, int y, boolean insertable) {
            super(handler, index, x, y);
            this.insertable = insertable;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return insertable && super.mayPlace(stack);
        }

        @Override
        public boolean isActive() {
            return upgradeSlotsShown;
        }
    }

    private final class PlayerSlot extends Slot {

        private PlayerSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return !isCovered(this);
        }
    }

    /**
     * ContainerData syncs 16-bit values, so longs are split into four parts.
     * Layout: energy (4), data (4), progress in 1/1000 (1), energy used last tick (4), max energy (4),
     * speed upgrades (1), energy upgrades (1), upgrade install ticks (1).
     */
    public static final class LabData {
        public static final int ENERGY = 0;
        public static final int DATA = 4;
        public static final int PROGRESS = 8;
        public static final int ENERGY_USED = 9;
        public static final int MAX_ENERGY = 13;
        public static final int SPEED_UPGRADES = 17;
        public static final int ENERGY_UPGRADES = 18;
        public static final int UPGRADE_TICKS = 19;
        public static final int COUNT = 20;
        public static final int PROGRESS_SCALE = 1000;

        private LabData() {
        }

        public static int part(long value, int index) {
            return (int) ((value >>> (16 * index)) & 0xFFFF);
        }

        static long readLong(ContainerData data, int offset) {
            long value = 0;
            for (int i = 0; i < 4; i++) {
                value |= (long) (data.get(offset + i) & 0xFFFF) << (16 * i);
            }
            return value;
        }
    }
}
