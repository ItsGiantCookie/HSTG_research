package io.github.itsgiantcookie.hstglabs;

import io.github.itsgiantcookie.hstglabs.grid.DataCableBlock;
import io.github.itsgiantcookie.hstglabs.grid.DataCableBlockEntity;
import io.github.itsgiantcookie.hstglabs.grid.DataConsumerBlock;
import io.github.itsgiantcookie.hstglabs.grid.DataConsumerBlockEntity;
import io.github.itsgiantcookie.hstglabs.grid.CableTier;
import java.util.EnumMap;
import java.util.Map;
import io.github.itsgiantcookie.hstglabs.tech.SciencePackItem;
import io.github.itsgiantcookie.hstglabs.lab.BasicLabBlock;
import io.github.itsgiantcookie.hstglabs.lab.BasicLabBlockEntity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HstgLabs.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HstgLabs.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
          DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HstgLabs.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HstgLabs.MODID);

    // --- machines ---

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

    // --- block entities ---

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataConsumerBlockEntity>> DATA_CONSUMER_BE =
          BLOCK_ENTITIES.register("data_consumer",
                () -> BlockEntityType.Builder.of(DataConsumerBlockEntity::new, DATA_CONSUMER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataCableBlockEntity>> DATA_CABLE_BE =
          BLOCK_ENTITIES.register("data_cable",
                () -> BlockEntityType.Builder.of(DataCableBlockEntity::new,
                      DATA_CABLES.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    // --- menus ---

    public static final DeferredHolder<MenuType<?>, MenuType<io.github.itsgiantcookie.hstglabs.lab.BasicLabMenu>> BASIC_LAB_MENU =
          MENUS.register("basic_lab", () -> IMenuTypeExtension.create(io.github.itsgiantcookie.hstglabs.lab.BasicLabMenu::new));

    // --- labs (WP-09, docs/design.md §5.2, §5.5) ---

    public static final DeferredBlock<BasicLabBlock> BASIC_LAB = BLOCKS.register("basic_lab", () -> new BasicLabBlock(machineProperties()));
    public static final DeferredItem<BlockItem> BASIC_LAB_ITEM = ITEMS.registerSimpleBlockItem(BASIC_LAB);

    /**
     * One block entity type for every {@link BasicLabBlock}, built when block entity types register,
     * after all blocks exist, so lab blocks from other mods and KubeJS are included (§5.5).
     */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BasicLabBlockEntity>> BASIC_LAB_BE = BLOCK_ENTITIES.register("basic_lab",
          () -> BlockEntityType.Builder.of(BasicLabBlockEntity::new, blocksOf(BasicLabBlock.class)).build(null));

    private static Block[] blocksOf(Class<? extends Block> type) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream().filter(type::isInstance).toArray(Block[]::new);
    }

    // --- science packs (WP-07, docs/design.md §8.2) ---

    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
          DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, HstgLabs.MODID);

    /** {@code hstg_labs:pack}: which pack definition a science pack stack is. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> PACK_COMPONENT =
          DATA_COMPONENTS.registerComponentType("pack", builder -> builder.persistent(ResourceLocation.CODEC)
                .networkSynchronized(ResourceLocation.STREAM_CODEC));

    public static final DeferredItem<SciencePackItem> SCIENCE_PACK = ITEMS.registerItem("science_pack", SciencePackItem::new);

    // --- creative tab ---

    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HstgLabs.MODID);

    /**
     * One tab with every item of the mod, in registration order. Items that come in variants (science
     * packs, one per pack definition) list their own stacks through {@link CreativeTabStacks}.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
          () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.hstg_labs"))
                .icon(() -> new ItemStack(BASIC_LAB_ITEM.get()))
                .displayItems((parameters, output) -> {
                    for (DeferredHolder<Item, ? extends Item> entry : ITEMS.getEntries()) {
                        if (entry.get() instanceof CreativeTabStacks variants) {
                            variants.creativeTabStacks().forEach(output::accept);
                        } else {
                            output.accept(entry.get());
                        }
                    }
                })
                .build());

    /** An item that shows several stacks (variants) in the creative tab instead of its default stack. */
    public interface CreativeTabStacks {

        java.util.List<ItemStack> creativeTabStacks();
    }

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
        DATA_COMPONENTS.register(modBus);
        CREATIVE_TABS.register(modBus);
    }
}
