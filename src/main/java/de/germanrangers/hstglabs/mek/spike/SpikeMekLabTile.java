package de.germanrangers.hstglabs.mek.spike;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.IChemicalTank;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.chemical.ChemicalTankHelper;
import mekanism.common.capabilities.holder.chemical.IChemicalTankHolder;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * WP-05a spike, variant A (THROWAWAY). One "pack" (paper) and 10 mB of any chemical start a unit; the
 * unit draws energy every tick for {@link #BASE_TICKS} ticks, shortened by speed upgrades.
 */
public class SpikeMekLabTile extends TileEntityConfigurableMachine {

    public static final long ENERGY_PER_TICK = 50L;
    public static final long CHEMICAL_PER_UNIT = 10L;
    public static final int BASE_TICKS = 20;

    // assigned from the super constructor via getInitial*: no field initialisers here
    BasicInventorySlot packSlot;
    IChemicalTank chemicalTank;
    private MachineEnergyContainer<SpikeMekLabTile> energyContainer;
    private int ticksLeft;
    private int units;

    public SpikeMekLabTile(BlockPos pos, BlockState state) {
        super(SpikeMekLab.BLOCK, pos, state);
        configComponent.setupInputConfig(TransmissionType.ITEM, packSlot);
        configComponent.setupInputConfig(TransmissionType.CHEMICAL, chemicalTank);
        configComponent.setupInputConfig(TransmissionType.ENERGY, energyContainer);
        ejectorComponent = new TileComponentEjector(this);
    }

    @NotNull
    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this);
        builder.addSlot(packSlot = BasicInventorySlot.at(stack -> stack.is(Items.PAPER), listener, 64, 35));
        return builder.build();
    }

    @NotNull
    @Override
    public IChemicalTankHolder getInitialChemicalTanks(IContentsListener listener) {
        ChemicalTankHelper builder = ChemicalTankHelper.forSideWithConfig(this);
        builder.addTank(chemicalTank = BasicChemicalTank.input(1_000L, chemical -> true, listener));
        return builder.build();
    }

    @NotNull
    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSideWithConfig(this);
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this, listener));
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        long perTick = energyContainer.getEnergyPerTick();
        if (ticksLeft == 0 && !packSlot.isEmpty() && chemicalTank.getStored() >= CHEMICAL_PER_UNIT
              && energyContainer.getEnergy() >= perTick) {
            packSlot.shrinkStack(1, Action.EXECUTE);
            chemicalTank.shrinkStack(CHEMICAL_PER_UNIT, Action.EXECUTE);
            ticksLeft = MekanismUtils.getTicks(this, BASE_TICKS);
        }
        boolean active = false;
        if (ticksLeft > 0 && energyContainer.getEnergy() >= perTick) {
            energyContainer.extract(perTick, Action.EXECUTE, AutomationType.INTERNAL);
            active = true;
            if (--ticksLeft == 0) {
                units++;
            }
            markForSave();
        }
        setActive(active);
        return sendUpdatePacket;
    }

    public MachineEnergyContainer<SpikeMekLabTile> getEnergyContainer() {
        return energyContainer;
    }

    public IChemicalTank getChemicalTank() {
        return chemicalTank;
    }

    public int units() {
        return units;
    }
}
