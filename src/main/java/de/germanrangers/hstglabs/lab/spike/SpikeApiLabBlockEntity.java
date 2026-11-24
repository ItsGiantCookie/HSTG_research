package de.germanrangers.hstglabs.lab.spike;

import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.IMekanismChemicalHandler;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IMekanismStrictEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * WP-05a spike, variant B (THROWAWAY): same behaviour as variant A on our own block entity. Only
 * {@code mekanism.api} types: {@link BasicChemicalTank}, {@link IEnergyContainer} (own
 * implementation, Mekanism's {@code BasicEnergyContainer} is internal) and the handler helper
 * interfaces.
 */
public class SpikeApiLabBlockEntity extends BlockEntity {

    public static final long ENERGY_PER_TICK = 50L;
    public static final long CHEMICAL_PER_UNIT = 10L;
    public static final int BASE_TICKS = 20;

    private final ItemStackHandler packs = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.is(Items.PAPER);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IChemicalTank tank = BasicChemicalTank.input(1_000L, chemical -> true, this::setChanged);
    private final Buffer energy = new Buffer(100_000L);
    private final IMekanismChemicalHandler chemicalHandler = new IMekanismChemicalHandler() {
        @Override
        public List<IChemicalTank> getChemicalTanks(@Nullable Direction side) {
            return List.of(tank);
        }

        @Override
        public void onContentsChanged() {
            setChanged();
        }
    };
    private final IMekanismStrictEnergyHandler energyHandler = new IMekanismStrictEnergyHandler() {
        @Override
        public List<IEnergyContainer> getEnergyContainers(@Nullable Direction side) {
            return List.of(energy);
        }

        @Override
        public void onContentsChanged() {
            setChanged();
        }
    };
    private int ticksLeft;
    private int units;

    public SpikeApiLabBlockEntity(BlockPos pos, BlockState state) {
        super(SpikeApiLab.BLOCK_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpikeApiLabBlockEntity lab) {
        if (lab.ticksLeft == 0 && !lab.packs.getStackInSlot(0).isEmpty() && lab.tank.getStored() >= CHEMICAL_PER_UNIT
              && lab.energy.getEnergy() >= ENERGY_PER_TICK) {
            lab.packs.extractItem(0, 1, false);
            lab.tank.shrinkStack(CHEMICAL_PER_UNIT, Action.EXECUTE);
            lab.ticksLeft = BASE_TICKS;
        }
        if (lab.ticksLeft > 0 && lab.energy.getEnergy() >= ENERGY_PER_TICK) {
            lab.energy.extract(ENERGY_PER_TICK, Action.EXECUTE, AutomationType.INTERNAL);
            if (--lab.ticksLeft == 0) {
                lab.units++;
            }
            lab.setChanged();
        }
    }

    public ItemStackHandler packs() {
        return packs;
    }

    public IMekanismChemicalHandler chemicalHandler() {
        return chemicalHandler;
    }

    public IMekanismStrictEnergyHandler energyHandler() {
        return energyHandler;
    }

    public int units() {
        return units;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("packs", packs.serializeNBT(registries));
        tag.put("tank", tank.serializeNBT(registries));
        tag.putLong("energy", energy.getEnergy());
        tag.putInt("ticksLeft", ticksLeft);
        tag.putInt("units", units);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        packs.deserializeNBT(registries, tag.getCompound("packs"));
        tank.deserializeNBT(registries, tag.getCompound("tank"));
        energy.setEnergy(tag.getLong("energy"));
        ticksLeft = tag.getInt("ticksLeft");
        units = tag.getInt("units");
    }

    /** Minimal Joule buffer on the API interface (Mekanism's BasicEnergyContainer is internal). */
    private final class Buffer implements IEnergyContainer {

        private final long capacity;
        private long stored;

        Buffer(long capacity) {
            this.capacity = capacity;
        }

        @Override
        public long getEnergy() {
            return stored;
        }

        @Override
        public void setEnergy(long energy) {
            stored = Math.max(0, Math.min(capacity, energy));
            onContentsChanged();
        }

        @Override
        public long getMaxEnergy() {
            return capacity;
        }

        @Override
        public void onContentsChanged() {
            setChanged();
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("stored", stored);
            return tag;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
            stored = tag.getLong("stored");
        }
    }
}
