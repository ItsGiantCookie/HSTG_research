package com.hstg.research.upgrade;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import mekanism.api.Upgrade;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Port of Mekanism's TileComponentUpgrade for the lab: an install slot that consumes Mekanism upgrade
 * items after {@value #INSTALL_TICKS} ticks, an output slot for uninstalled upgrades, and Mekanism's
 * upgrade math (MekanismUtils.getTicks / getEnergyPerTick / getMaxEnergy).
 * <p>
 * Upgrade items are Mekanism's own ({@code mekanism:upgrade_<type>}), looked up by registry id because
 * IUpgradeItem is not part of Mekanism's public API.
 */
public final class UpgradeComponent {

    public static final Set<Upgrade> SUPPORTED = EnumSet.of(Upgrade.SPEED, Upgrade.ENERGY);
    /** Mekanism's UPGRADE_TICKS_REQUIRED (one second). */
    public static final int INSTALL_TICKS = 20;
    /** Mekanism config general.maxUpgradeMultiplier (default 10); the config itself is internal. */
    public static final double MULTIPLIER = 10;
    public static final int SLOT_INSTALL = 0;
    public static final int SLOT_REMOVED = 1;

    private final Map<Upgrade, Integer> installed = new EnumMap<>(Upgrade.class);
    private final Runnable onChanged;
    private final ItemStackHandler slots = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            onChanged.run();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            Upgrade type = upgradeOf(stack);
            return type != null && (slot == SLOT_REMOVED || SUPPORTED.contains(type));
        }
    };
    private int installTicks;

    public UpgradeComponent(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public ItemStackHandler getSlots() {
        return slots;
    }

    public int get(Upgrade upgrade) {
        return installed.getOrDefault(upgrade, 0);
    }

    public int getInstallTicks() {
        return installTicks;
    }

    private double fraction(Upgrade upgrade) {
        return get(upgrade) / (double) upgrade.getMax();
    }

    // --- Mekanism upgrade math ---

    /** MekanismUtils.getTicks: def * M^(-speed/8), at least 1. */
    public int ticks(int def) {
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE, def * Math.pow(MULTIPLIER, -fraction(Upgrade.SPEED))));
    }

    /** MekanismUtils.getEnergyPerTick: def * M^(2 speed/8 - energy/8), rounded up. */
    public long energyPerTick(long def) {
        return (long) Math.ceil(def * Math.pow(MULTIPLIER, 2 * fraction(Upgrade.SPEED) - fraction(Upgrade.ENERGY)));
    }

    /** MekanismUtils.getMaxEnergy: def * M^(energy/8). */
    public long maxEnergy(long def) {
        return (long) Math.min(Long.MAX_VALUE, def * Math.pow(MULTIPLIER, fraction(Upgrade.ENERGY)));
    }

    /** UpgradeUtils.getMultScaledInfo: the "Effect: Nx" shown in the upgrade window. */
    public static double effect(Upgrade upgrade, int count) {
        return Math.round(Math.pow(MULTIPLIER, count / (double) upgrade.getMax()) * 100) / 100D;
    }

    // --- install / remove ---

    /** Server tick: count up while a supported upgrade waits in the install slot, then install the whole stack that fits. */
    public void tick() {
        ItemStack stack = slots.getStackInSlot(SLOT_INSTALL);
        Upgrade type = upgradeOf(stack);
        if (type != null && SUPPORTED.contains(type) && get(type) < type.getMax()) {
            if (installTicks < INSTALL_TICKS) {
                installTicks++;
                return;
            }
            int added = Math.min(type.getMax() - get(type), stack.getCount());
            installed.put(type, get(type) + added);
            slots.extractItem(SLOT_INSTALL, added, false);
            onChanged.run();
        }
        installTicks = 0;
    }

    /** One upgrade (or all of the type) into the removed-upgrades slot, as far as it fits. */
    public void remove(Upgrade upgrade, boolean all) {
        int count = get(upgrade);
        if (count <= 0) {
            return;
        }
        ItemStack stack = stackOf(upgrade, all ? count : 1);
        if (stack.isEmpty()) {
            return;
        }
        int wanted = stack.getCount();
        int removed = wanted - slots.insertItem(SLOT_REMOVED, stack, true).getCount();
        if (removed > 0) {
            slots.insertItem(SLOT_REMOVED, stackOf(upgrade, removed), false);
            if (count == removed) {
                installed.remove(upgrade);
            } else {
                installed.put(upgrade, count - removed);
            }
            onChanged.run();
        }
    }

    /** Client sync from the menu. */
    public void setClient(Upgrade upgrade, int count, int ticks) {
        if (count <= 0) {
            installed.remove(upgrade);
        } else {
            installed.put(upgrade, count);
        }
        installTicks = ticks;
    }

    // --- Mekanism upgrade items ---

    private static ResourceLocation itemId(Upgrade upgrade) {
        return ResourceLocation.fromNamespaceAndPath("mekanism", "upgrade_" + upgrade.getSerializedName());
    }

    @Nullable
    public static Upgrade upgradeOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (Upgrade upgrade : Upgrade.values()) {
            if (itemId(upgrade).equals(id)) {
                return upgrade;
            }
        }
        return null;
    }

    public static ItemStack stackOf(Upgrade upgrade, int count) {
        Item item = BuiltInRegistries.ITEM.get(itemId(upgrade));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    // --- persistence ---

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        CompoundTag counts = new CompoundTag();
        installed.forEach((upgrade, count) -> counts.putInt(upgrade.getSerializedName(), count));
        tag.put("installed", counts);
        tag.put("slots", slots.serializeNBT(registries));
        tag.putInt("ticks", installTicks);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        installed.clear();
        CompoundTag counts = tag.getCompound("installed");
        for (Upgrade upgrade : SUPPORTED) {
            int count = Math.min(counts.getInt(upgrade.getSerializedName()), upgrade.getMax());
            if (count > 0) {
                installed.put(upgrade, count);
            }
        }
        if (tag.contains("slots")) {
            slots.deserializeNBT(registries, tag.getCompound("slots"));
        }
        installTicks = tag.getInt("ticks");
    }
}
