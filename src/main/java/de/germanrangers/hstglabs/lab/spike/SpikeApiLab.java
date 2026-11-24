package de.germanrangers.hstglabs.lab.spike;

import de.germanrangers.hstglabs.HstgLabs;
import de.germanrangers.hstglabs.ModCapabilities;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * WP-05a spike, variant B (THROWAWAY, deleted after the D22 ADR): the same lab on our own block
 * entity with Mekanism API types only. Lives outside mek/, so the Mekanism boundary guard test proves
 * it touches no internals.
 */
public final class SpikeApiLab {

    /** Mekanism's chemical capability, looked up by name (docs/mek-api-facts.md). */
    public static final BlockCapability<IChemicalHandler, Direction> CHEMICAL =
          BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HstgLabs.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HstgLabs.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
          DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HstgLabs.MODID);

    public static final DeferredBlock<SpikeApiLabBlock> BLOCK = BLOCKS.register("spike_api_lab",
          () -> new SpikeApiLabBlock(BlockBehaviour.Properties.of().strength(3.5F)));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK, new Item.Properties());
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpikeApiLabBlockEntity>> BLOCK_ENTITY =
          BLOCK_ENTITIES.register("spike_api_lab", () -> BlockEntityType.Builder.of(SpikeApiLabBlockEntity::new, BLOCK.get()).build(null));

    private SpikeApiLab() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(SpikeApiLab::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLOCK_ENTITY.get(), (be, side) -> be.packs());
        event.registerBlockEntity(CHEMICAL, BLOCK_ENTITY.get(), (be, side) -> be.chemicalHandler());
        event.registerBlockEntity(ModCapabilities.STRICT_ENERGY, BLOCK_ENTITY.get(), (be, side) -> be.energyHandler());
    }
}
