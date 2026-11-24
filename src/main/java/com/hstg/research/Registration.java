package com.hstg.research;

import com.hstg.research.block.DataCableBlock;
import com.hstg.research.block.DataCableBlockEntity;
import com.hstg.research.block.DataConsumerBlock;
import com.hstg.research.block.DataConsumerBlockEntity;
import com.hstg.research.block.ResearchLabBlock;
import com.hstg.research.block.ResearchLabBlockEntity;
import com.hstg.research.cable.CableTier;
import com.hstg.research.menu.ResearchLabMenu;
import com.hstg.research.recipe.ResearchRecipe;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Registration {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HstgResearch.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HstgResearch.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
          DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HstgResearch.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HstgResearch.MODID);
    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, HstgResearch.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
          DeferredRegister.create(Registries.RECIPE_SERIALIZER, HstgResearch.MODID);

    // --- machines ---

    public static final DeferredBlock<ResearchLabBlock> RESEARCH_LAB = BLOCKS.register("research_lab",
          () -> new ResearchLabBlock(machineProperties()));
    public static final DeferredItem<BlockItem> RESEARCH_LAB_ITEM = ITEMS.registerSimpleBlockItem(RESEARCH_LAB);

    /** Dummy data sink for testing the cable network. */
    public static final DeferredBlock<DataConsumerBlock> DATA_CONSUMER = BLOCKS.register("data_consumer",
          () -> new DataConsumerBlock(machineProperties()));
    public static final DeferredItem<BlockItem> DATA_CONSUMER_ITEM = ITEMS.registerSimpleBlockItem(DATA_CONSUMER);

    // --- data cables (one block per tier, like Mekanism transmitters) ---

    public static final Map<CableTier, DeferredBlock<DataCableBlock>> DATA_CABLES = new EnumMap<>(CableTier.class);
    public static final Map<CableTier, DeferredItem<BlockItem>> DATA_CABLE_ITEMS = new EnumMap<>(CableTier.class);

    static {
        for (CableTier tier : CableTier.values()) {
            DeferredBlock<DataCableBlock> block = BLOCKS.register(tier.getSerializedName() + "_data_cable",
                  () -> new DataCableBlock(tier, BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(1F, 6F)
                        .sound(SoundType.METAL)
                        .noOcclusion()
                        .forceSolidOn()));
            DATA_CABLES.put(tier, block);
            DATA_CABLE_ITEMS.put(tier, ITEMS.registerSimpleBlockItem(block));
        }
    }

    // --- items ---

    /** Dummy research input: consumed by the lab, turned into data. */
    public static final DeferredItem<Item> RESEARCH_SAMPLE = ITEMS.registerSimpleItem("research_sample", new Item.Properties().stacksTo(16));
    /** What is left of a sample after the lab has read it. */
    public static final DeferredItem<Item> EMPTY_SAMPLE_VIAL = ITEMS.registerSimpleItem("empty_sample_vial");

    // --- block entities ---

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchLabBlockEntity>> RESEARCH_LAB_BE =
          BLOCK_ENTITIES.register("research_lab",
                () -> BlockEntityType.Builder.of(ResearchLabBlockEntity::new, RESEARCH_LAB.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataConsumerBlockEntity>> DATA_CONSUMER_BE =
          BLOCK_ENTITIES.register("data_consumer",
                () -> BlockEntityType.Builder.of(DataConsumerBlockEntity::new, DATA_CONSUMER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataCableBlockEntity>> DATA_CABLE_BE =
          BLOCK_ENTITIES.register("data_cable",
                () -> BlockEntityType.Builder.of(DataCableBlockEntity::new,
                      DATA_CABLES.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    // --- menus ---

    public static final DeferredHolder<MenuType<?>, MenuType<ResearchLabMenu>> RESEARCH_LAB_MENU =
          MENUS.register("research_lab", () -> IMenuTypeExtension.create(ResearchLabMenu::new));

    // --- recipes ---

    public static final DeferredHolder<RecipeType<?>, RecipeType<ResearchRecipe>> RESEARCH_RECIPE_TYPE =
          RECIPE_TYPES.register("research", () -> RecipeType.simple(HstgResearch.rl("research")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ResearchRecipe>> RESEARCH_RECIPE_SERIALIZER =
          RECIPE_SERIALIZERS.register("research", ResearchRecipe.Serializer::new);

    private Registration() {
    }

    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.of()
              .mapColor(MapColor.METAL)
              .strength(3.5F, 4.8F)
              .requiresCorrectToolForDrops()
              .sound(SoundType.METAL);
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
    }
}
