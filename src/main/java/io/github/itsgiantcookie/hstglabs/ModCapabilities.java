package io.github.itsgiantcookie.hstglabs;

import io.github.itsgiantcookie.hstglabs.grid.IDataHandler;
import io.github.itsgiantcookie.hstglabs.grid.DataCableBlockEntity;
import io.github.itsgiantcookie.hstglabs.grid.DataConsumerBlockEntity;
import io.github.itsgiantcookie.hstglabs.lab.ResearchLabBlockEntity;
import mekanism.api.IConfigCardAccess;
import mekanism.api.IConfigurable;
import mekanism.api.energy.IStrictEnergyHandler;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class ModCapabilities {

    /*
     * Mekanism's capabilities, looked up by id. BlockCapability.createSided / ItemCapability.createVoid
     * return the instance Mekanism registered under the same name/type, so we only need Mekanism's
     * public API jar (mekanism.common.capabilities.Capabilities is internal).
     */
    public static final BlockCapability<IStrictEnergyHandler, Direction> STRICT_ENERGY =
          BlockCapability.createSided(mekanism("strict_energy_handler"), IStrictEnergyHandler.class);
    /** Energy items (batteries, energy tablets, cubes) for the lab's energy slot. */
    public static final ItemCapability<IStrictEnergyHandler, Void> STRICT_ENERGY_ITEM =
          ItemCapability.createVoid(mekanism("strict_energy_handler"), IStrictEnergyHandler.class);
    public static final BlockCapability<IConfigurable, Direction> CONFIGURABLE =
          BlockCapability.createSided(mekanism("configurable"), IConfigurable.class);
    public static final BlockCapability<IConfigCardAccess, Direction> CONFIG_CARD =
          BlockCapability.createSided(mekanism("config_card"), IConfigCardAccess.class);

    /** Our own capability for Data faces, data cables and data consumers. */
    public static final BlockCapability<IDataHandler, Direction> DATA =
          BlockCapability.createSided(HstgLabs.rl("data_handler"), IDataHandler.class);

    private ModCapabilities() {
    }

    private static ResourceLocation mekanism(String path) {
        return ResourceLocation.fromNamespaceAndPath("mekanism", path);
    }

    public static void register(RegisterCapabilitiesEvent event) {
        BlockEntityType<ResearchLabBlockEntity> lab = Registration.RESEARCH_LAB_BE.get();
        event.registerBlockEntity(STRICT_ENERGY, lab, ResearchLabBlockEntity::getEnergyHandler);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, lab, ResearchLabBlockEntity::getItemHandler);
        event.registerBlockEntity(DATA, lab, ResearchLabBlockEntity::getDataHandler);
        event.registerBlockEntity(CONFIGURABLE, lab, ResearchLabBlockEntity::getConfigurable);
        event.registerBlockEntity(CONFIG_CARD, lab, (be, side) -> be);

        event.registerBlockEntity(DATA, Registration.DATA_CABLE_BE.get(), DataCableBlockEntity::getDataHandler);
        event.registerBlockEntity(DATA, Registration.DATA_CONSUMER_BE.get(), DataConsumerBlockEntity::getDataHandler);
    }
}
