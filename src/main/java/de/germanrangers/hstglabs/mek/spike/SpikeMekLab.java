package de.germanrangers.hstglabs.mek.spike;

import de.germanrangers.hstglabs.HstgLabs;
import java.util.EnumMap;
import java.util.Map;
import mekanism.api.text.ILangEntry;
import mekanism.common.attachments.component.AttachedEjector;
import mekanism.common.attachments.component.AttachedSideConfig;
import mekanism.common.attachments.component.AttachedSideConfig.LightConfigInfo;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.block.attribute.AttributeSideConfig;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.content.blocktype.Machine.MachineBuilder;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.item.block.ItemBlockTooltip;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.base.TileEntityMekanism;
import net.neoforged.bus.api.IEventBus;

/**
 * WP-05a spike, variant A (THROWAWAY, deleted after the D22 ADR): a lab built the way Mekanism
 * builds its own machines, on {@code TileEntityConfigurableMachine} with Mekanism's block type,
 * registration helpers, side config, upgrades and GUI.
 */
public final class SpikeMekLab {

    private static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(HstgLabs.MODID);
    private static final TileEntityTypeDeferredRegister TILES = new TileEntityTypeDeferredRegister(HstgLabs.MODID);
    private static final ContainerTypeDeferredRegister CONTAINERS = new ContainerTypeDeferredRegister(HstgLabs.MODID);

    private static final ILangEntry DESCRIPTION = () -> "block.hstg_labs.spike_mek_lab.description";

    public static final Machine<SpikeMekLabTile> TYPE = MachineBuilder
          .createMachine(() -> SpikeMekLab.TILE, DESCRIPTION)
          .withGui(() -> SpikeMekLab.CONTAINER)
          .withEnergyConfig(() -> SpikeMekLabTile.ENERGY_PER_TICK, () -> 100_000L)
          .with(AttributeSideConfig.ADVANCED_ELECTRIC_MACHINE)
          .build();

    public static final BlockRegistryObject<BlockTile<SpikeMekLabTile, Machine<SpikeMekLabTile>>,
          ItemBlockTooltip<BlockTile<SpikeMekLabTile, Machine<SpikeMekLabTile>>>> BLOCK =
          BLOCKS.register("spike_mek_lab", () -> new BlockTile<>(TYPE, properties -> properties.strength(3.5F)),
                // Mekanism keeps a machine's default faces on its ITEM; a block placed without the item has every face NONE
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties
                      .component(MekanismDataComponents.EJECTOR, AttachedEjector.DEFAULT)
                      .component(MekanismDataComponents.SIDE_CONFIG, allInputs())));

    private static AttachedSideConfig allInputs() {
        Map<TransmissionType, LightConfigInfo> faces = new EnumMap<>(TransmissionType.class);
        faces.put(TransmissionType.ITEM, LightConfigInfo.INPUT_ONLY);
        faces.put(TransmissionType.CHEMICAL, LightConfigInfo.INPUT_ONLY);
        faces.put(TransmissionType.ENERGY, LightConfigInfo.INPUT_ONLY);
        return new AttachedSideConfig(faces);
    }

    public static final TileEntityTypeRegistryObject<SpikeMekLabTile> TILE = TILES
          .mekBuilder(BLOCK, SpikeMekLabTile::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .build();

    public static final ContainerTypeRegistryObject<MekanismTileContainer<SpikeMekLabTile>> CONTAINER =
          CONTAINERS.register("spike_mek_lab", SpikeMekLabTile.class);

    private SpikeMekLab() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        TILES.register(modBus);
        CONTAINERS.register(modBus);
    }
}
