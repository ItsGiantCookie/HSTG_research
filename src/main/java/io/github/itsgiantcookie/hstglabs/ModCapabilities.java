package io.github.itsgiantcookie.hstglabs;

import io.github.itsgiantcookie.hstglabs.grid.IDataHandler;
import io.github.itsgiantcookie.hstglabs.grid.DataCableBlockEntity;
import io.github.itsgiantcookie.hstglabs.grid.DataConsumerBlockEntity;
import mekanism.api.IConfigCardAccess;
import mekanism.api.IConfigurable;
import mekanism.api.energy.IStrictEnergyHandler;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
        // WP-09 basic lab: items from automation, Joules (Mekanism) and FE (D23)
        var basicLab = Registration.BASIC_LAB_BE.get();
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, basicLab, (be, side) -> be.items());
        event.registerBlockEntity(STRICT_ENERGY, basicLab, (be, side) -> be.strictEnergy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, basicLab, (be, side) -> be.feEnergy());

        event.registerBlockEntity(DATA, Registration.DATA_CABLE_BE.get(), DataCableBlockEntity::getDataHandler);
        event.registerBlockEntity(DATA, Registration.DATA_CONSUMER_BE.get(), DataConsumerBlockEntity::getDataHandler);
    }
}
