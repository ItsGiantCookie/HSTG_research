# Mekanism API facts

Every Mekanism signature our code uses, with `path:line`. Source: `ref/Mekanism` at tag
`v1.21.1-10.7.19.85` = commit `a00109e4856fd38b9c5b3dd7f22ce4a59cd65a80`. Paths are relative to
`ref/Mekanism/src/`. The jar we build against is `mekanism:Mekanism:1.21.1-10.7.19.85`.

Rule (docs/design.md §7.2): imports outside `mekanism.api..` only in our `mek/` package; anything
internal we reach without importing (registry ids, capability names, data components) is listed
under "Internal, reached by id" so a Mekanism update can be checked against this file.

## Public API used (`api/java/mekanism/api/`)

| Need | Signature | Where | Used by |
| --- | --- | --- | --- |
| Simulate / execute | `enum Action { EXECUTE, SIMULATE }`, `boolean execute()` | `Action.java:6-7, 18` | lab, grid |
| Relative sides | `RelativeSide.fromDirections(Direction facing, Direction side)` | `RelativeSide.java:94` | lab, side config |
| | `Direction getDirection(Direction facing)` | `RelativeSide.java:65` | screen |
| | `String getTranslationKey()`, `STREAM_CODEC` | `RelativeSide.java:54, 43` | screen, packets |
| Strict energy | `int getEnergyContainerCount()`, `long insertEnergy(int, long, Action)`, `long extractEnergy(int, long, Action)`, `default long extractEnergy(long, Action)` | `energy/IStrictEnergyHandler.java:17, 68, 82, 118` | lab |
| Configurator hook | `IConfigurable.onRightClick(Player)`, `onSneakRightClick(Player)` | `IConfigurable.java:33, 22` | lab |
| Config card | `IConfigCardAccess.getConfigurationDataType()`, `getConfigurationData(Provider, Player)`, `setConfigurationData`, `configurationDataSet` | `IConfigCardAccess.java:30, 56` | lab |
| Colors | `EnumColor.getRgbCode()`, `getColor()`; `DARK_RED`, `DARK_BLUE`, `PURPLE`, `DARK_GREEN`, `GRAY` | `text/EnumColor.java:192, 158, 32, 29, 33, 30, 35` | side config |
| Tiers | `BaseTier.getLowerName()` | `tier/BaseTier.java:64` | cable tiers |
| Upgrades | `enum Upgrade { SPEED(…, 8, RED), ENERGY(…, 8, BRIGHT_GREEN), … }`, `getMax()`, `getColor()`, `getDescription()`, `getSerializedName()` | `Upgrade.java:29-30, 159, 166, 152, 140` | upgrade component, screen |

## Internal, reached by id (no import)

| Need | Internal definition | Where | How we reach it |
| --- | --- | --- | --- |
| Strict energy capability | `Capabilities.STRICT_ENERGY = new MultiTypeCapability<>(rl("strict_energy_handler"), …)` | `main/java/mekanism/common/capabilities/Capabilities.java:63` | `BlockCapability.createSided` / `ItemCapability.createVoid` with the same name return the same instance (`MultiTypeCapability.java:16-17`) |
| Chemical capability (WP-07) | `Capabilities.CHEMICAL`, name `chemical_handler` | `Capabilities.java:59` | same trick |
| Configurator capability | `CONFIGURABLE = BlockCapability.createSided(rl("configurable"), IConfigurable.class)` | `Capabilities.java:65` | same name |
| Config card capability | `CONFIG_CARD = BlockCapability.createSided(rl("config_card"), IConfigCardAccess.class)` | `Capabilities.java:69` | same name |
| Configurator item | `CONFIGURATOR = registerItem("configurator", …)` | `main/java/mekanism/common/registries/MekanismItems.java:99` | item id `mekanism:configurator` |
| Configurator mode | `DataComponentType<ConfiguratorMode> CONFIGURATOR_MODE` (`"configurator_mode"`) | `main/java/mekanism/common/registries/MekanismDataComponents.java:111` | component lookup by id, enum read by `name()` |
| Configurate modes | `CONFIGURATE_ITEMS, _FLUIDS, _CHEMICALS, _ENERGY, _HEAT` | `main/java/mekanism/common/item/ItemConfigurator.java:248-252` | names only |
| Configurator → IConfigurable | `getCapability(world, Capabilities.CONFIGURABLE, pos, null, tile, side)` in configurate modes for non-`ISideConfiguration` tiles | `main/java/mekanism/common/item/ItemConfigurator.java:155` | behaviour we rely on |
| Upgrade items | `SPEED_UPGRADE`, `ENERGY_UPGRADE` via `registerUpgrade(Upgrade)` | `main/java/mekanism/common/registries/MekanismItems.java:234-235` | ids `mekanism:upgrade_<type>` |
| Upgrade math | `getTicksD` = def·M^-speed; `getEnergyPerTick` = def·M^(2·speed − energy); `getMaxEnergy` = def·M^energy (fractions of 8) | `main/java/mekanism/common/util/MekanismUtils.java:326, 355, 390` | re-implemented in `mek/UpgradeComponent` |
| Multiplier M | `maxUpgradeMultiplier` default 10 | `main/java/mekanism/common/config/GeneralConfig.java:139` | hard-coded 10 (config not readable via api) |
| Install time | `UPGRADE_TICKS_REQUIRED = TICKS_PER_SECOND` | `main/java/mekanism/common/tile/component/TileComponentUpgrade.java:42` | 20 ticks |
| Eject interval | `tickDelay = TICKS_PER_HALF_SECOND` | `main/java/mekanism/common/tile/component/TileComponentEjector.java:329` | 10 ticks |
| Configurator overlay | colour = data type colour at 0.6 alpha | `main/java/mekanism/client/render/RenderTickHandler.java:491` | `client/PortOverlayRenderer` |
| GUI drawing (ported, MIT) | `GuiUtils.blitNineSlicedSized(...)` | `main/java/mekanism/client/gui/GuiUtils.java:329` | `mek/client/MekGui` |
| GUI textures | `assets/mekanism/gui/**` (base, button, slot/*, bar/*, progress/*, tabs/*, holder_*, …) | resources | referenced as `mekanism:gui/<path>` |

## WP-05a spike (D22): signatures the two throwaway variants used

The spike code is deleted (see docs/decisions/D22-mekanism-depth.md); these rows stay so the chosen
option starts from verified facts. All paths relative to `ref/Mekanism/src/`.

### Variant A, Mekanism internals (`main/java/mekanism/`)

| Need | Signature | Where |
| --- | --- | --- |
| Tile base with side config + ejector | `TileEntityConfigurableMachine(Holder<Block> blockProvider, BlockPos, BlockState)`; builds `TileComponentConfig` from the block's `AttributeSideConfig` | `common/tile/prefab/TileEntityConfigurableMachine.java:22` |
| Tile base | `TileEntityMekanism(Holder<Block> blockProvider, BlockPos, BlockState)`; reads every behaviour from block attributes (`:343-356`) and calls the `getInitial*` hooks **from its constructor**, so subclass fields they assign must have no initialiser | `common/tile/base/TileEntityMekanism.java:278` |
| Container hooks | `getInitialInventory(IContentsListener)`, `getInitialChemicalTanks(IContentsListener)`, `getInitialEnergyContainers(IContentsListener)` | `TileEntityMekanism.java:1208, 1278, 1376` |
| Server tick | `protected boolean onUpdateServer()` | `TileEntityMekanism.java:730` |
| Side config | `TileComponentConfig.setupInputConfig(TransmissionType, Object container)` (adds the slot info only, every face stays `NONE`) | `common/tile/component/TileComponentConfig.java:147` |
| Default faces | `TileComponentConfig.applyImplicitComponents` reads `MekanismDataComponents.SIDE_CONFIG` from the **placing item**; `SIDE_CONFIG` / `EJECTOR` components | `TileComponentConfig.java:258`, `common/registries/MekanismDataComponents.java:250, 245` |
| Face presets | `record AttachedSideConfig(Map<TransmissionType, LightConfigInfo>)`, `LightConfigInfo.INPUT_ONLY` (all faces `INPUT`), `AttachedEjector.DEFAULT` | `common/attachments/component/AttachedSideConfig.java:27, 227`, `AttachedEjector.java:25` |
| Ejector | `TileComponentEjector(TileEntityMekanism)` | `common/tile/component/TileComponentEjector.java:92` |
| Block type | `MachineBuilder.createMachine(Supplier<TileEntityTypeRegistryObject<TILE>>, ILangEntry)`; `Machine` adds facing, inventory, security, redstone, comparator, upgrade support (`SPEED, ENERGY, MUFFLING`) | `common/content/blocktype/Machine.java:49, 12-13` |
| | `withGui(Supplier<ContainerTypeRegistryObject<…>>)`, `withEnergyConfig(LongSupplier usage, LongSupplier storage)` | `common/content/blocktype/BlockTypeTile.java:49, 57` |
| | `AttributeSideConfig.ADVANCED_ELECTRIC_MACHINE` (ITEM, CHEMICAL, ENERGY) | `common/block/attribute/AttributeSideConfig.java:12` |
| Block + item | `BlockTile(TYPE, UnaryOperator<Properties>)`, `ItemBlockTooltip(BLOCK, boolean hasDetails, Properties)` | `common/block/prefab/BlockTile.java:37`, `common/item/block/ItemBlockTooltip.java:61` |
| Registration | `BlockDeferredRegister.register(name, blockSupplier, itemCreator)` | `common/registration/impl/BlockDeferredRegister.java:43` |
| | `TileEntityTypeDeferredRegister.mekBuilder(DeferredHolder<Block,?>, factory)`: **one block per type**; attaches item/fluid/chemical/heat/energy/security capability providers and registers them itself on `RegisterCapabilitiesEvent` | `common/registration/impl/TileEntityTypeDeferredRegister.java:46, 79` |
| | `ContainerTypeDeferredRegister.register(String, Class<TILE>)` → `MekanismTileContainer<TILE>` | `common/registration/impl/ContainerTypeDeferredRegister.java:42` |
| Energy | `MachineEnergyContainer.input(TILE, IContentsListener)`, usage and capacity from the block's energy attribute and upgrades | `common/capabilities/energy/MachineEnergyContainer.java:21` |
| Holders | `InventorySlotHelper` / `ChemicalTankHelper` / `EnergyContainerHelper.forSideWithConfig(ISideConfiguration)` | `common/capabilities/holder/slot/InventorySlotHelper.java:34`, `holder/chemical/ChemicalTankHelper.java:34`, `holder/energy/EnergyContainerHelper.java:23` |
| Slot | `BasicInventorySlot.at(Predicate<ItemStack>, IContentsListener, x, y)` | `common/inventory/slot/BasicInventorySlot.java:34` |
| Speed upgrades | `MekanismUtils.getTicks(IUpgradeTile, int def)` | `common/util/MekanismUtils.java:311` |
| GUI | `GuiConfigurableTile(CONTAINER, Inventory, Component)` adds side-config + transporter tabs; `GuiMekanismTile.addGenericTabs` adds upgrade, redstone, security tabs | `client/gui/GuiConfigurableTile.java:17`, `client/gui/GuiMekanismTile.java:39` |
| | `GuiVerticalPowerBar(IGuiWrapper, IEnergyContainer, x, y)`, `GuiChemicalGauge(Supplier<IChemicalTank>, Supplier<List<IChemicalTank>>, GaugeType, IGuiWrapper, x, y)`, `GuiEnergyTab(IGuiWrapper, MachineEnergyContainer<?>, BooleanSupplier)` | `client/gui/element/bar/GuiVerticalPowerBar.java:22`, `element/gauge/GuiChemicalGauge.java:41`, `element/tab/GuiEnergyTab.java:50` |
| GUI without the tile base | `GuiMekanism<CONTAINER extends AbstractContainerMenu>` accepts any menu, but `GuiSideConfigurationTab<TILE extends TileEntityMekanism & ISideConfiguration>` and `GuiUpgradeWindowTab(IGuiWrapper, TileEntityMekanism, …)` need the tile base | `client/gui/GuiMekanism.java:51`, `element/tab/window/GuiSideConfigurationTab.java:18`, `element/tab/window/GuiUpgradeWindowTab.java:21` |
| Not in the API | `BasicEnergyContainer` is internal, so variant B wrote its own `IEnergyContainer` | `common/capabilities/energy/BasicEnergyContainer.java:21` |

### Variant B, API only (`api/java/mekanism/api/`)

| Need | Signature | Where |
| --- | --- | --- |
| Chemical tank | `BasicChemicalTank.input(long capacity, Predicate<Chemical> validator, IContentsListener)` | `chemical/BasicChemicalTank.java:197` |
| Handler helpers | `IMekanismChemicalHandler.getChemicalTanks(Direction)`, `IMekanismStrictEnergyHandler.getEnergyContainers(Direction)` (all transfer logic is default methods) | `chemical/IMekanismChemicalHandler.java:38`, `energy/IMekanismStrictEnergyHandler.java:39` |
| Energy container | `interface IEnergyContainer extends INBTSerializable<CompoundTag>, IContentsListener` | `energy/IEnergyContainer.java:16` |
| Test input | `MekanismAPI.CHEMICAL_REGISTRY`, `new ChemicalStack(Chemical, long)`, `IChemicalHandler.insertChemical(ChemicalStack, Action)` | `MekanismAPI.java:120`, `chemical/ChemicalStack.java:206`, `chemical/IChemicalHandler.java:121` |
| Misc | `AutomationType.INTERNAL`, `ILangEntry` (used by variant A for the block description) | `AutomationType.java:14`, `text/ILangEntry.java:12` |

## WP-07: chemical ingredient type (`mek/ChemicalIngredientType`, API only)

| Need | Signature | Where |
| --- | --- | --- |
| Chemical by id in JSON | `public static final Codec<Holder<Chemical>> HOLDER_CODEC = MekanismAPI.CHEMICAL_REGISTRY.holderByNameCodec()` | `api/java/mekanism/api/chemical/Chemical.java:67` |
| Chemical tags | `MekanismAPI.CHEMICAL_REGISTRY_NAME` (`ResourceKey<Registry<Chemical>>`), with vanilla `TagKey.codec` / `TagKey.create` | `api/java/mekanism/api/MekanismAPI.java:65` |
| Holder by id (network decode) | `MekanismAPI.CHEMICAL_REGISTRY` (`DefaultedRegistry<Chemical>`), vanilla `getHolder(ResourceLocation)` | `api/java/mekanism/api/MekanismAPI.java:120` |
| Matching | `ChemicalStack.is(Holder<Chemical>)`, `is(TagKey<Chemical>)` | `api/java/mekanism/api/chemical/ChemicalStack.java:333, 294` |

## WP-09: basic lab energy and upgrades

| Need | Fact | Where | How we use it |
| --- | --- | --- | --- |
| FE ↔ Joules | `feConversionRate` default `2.5` (1 FE = 2.5 J) | `main/java/mekanism/common/config/GeneralConfig.java:194-195` | Hard-coded `BasicLabBlockEntity.JOULES_PER_FE`; Mekanism's config is internal (D22) |
| Upgrade item ids | `mekanism:upgrade_speed`, `mekanism:upgrade_energy` | `main/java/mekanism/common/registries/MekanismItems.java:234-235` | By id; up to 8 each (`Upgrade.getMax()`) |
| Upgrade maths | ticks × 10^(−s/8), energy/t × 10^((2s−e)/8), capacity × 10^(e/8) | `main/java/mekanism/common/util/MekanismUtils.java:326, 355, 390` | Re-implemented in `BasicLabBlockEntity` (speed, energyPerTick, energyCapacity) |

## WP-10 lab screen (`mek/client/LabScreen`, D22 hybrid: `mekanism.client.**` only, no tile base)
Paths under `ref/Mekanism/src/main/java/mekanism/client/`.

| Need | Fact | Where |
| --- | --- | --- |
| Screen base | `protected GuiMekanism(CONTAINER, Inventory, Component)`; `protected boolean dynamicSlots` makes `addGuiElements` add a `GuiSlot` behind every menu slot | `gui/GuiMekanism.java:69, 57, 133` |
| Hooks | `addGuiElements()`, `containerTick()`, `drawForegroundText(GuiGraphics, int, int)`, `renderInventoryText(GuiGraphics)` (uses `inventoryLabelX/Y`) | `gui/GuiMekanism.java:133, 165, 449, 196` |
| Text colour | `IFancyFontRenderer.titleTextColor()` | `render/IFancyFontRenderer.java:36` |
| Energy bar | `GuiVerticalPowerBar(IGuiWrapper, IBarInfoHandler, x, y, desiredHeight)`; `GuiBar.IBarInfoHandler { getLevel(); getTooltip() }` | `gui/element/bar/GuiVerticalPowerBar.java:44`, `gui/element/bar/GuiBar.java:106` |
| Progress arrow | `GuiProgress(IProgressInfoHandler, ProgressType, IGuiWrapper, x, y)`; `ProgressType.LARGE_RIGHT` (48×8); `IProgressInfoHandler { getProgress(); isActive() }` | `gui/element/progress/GuiProgress.java:39`, `ProgressType.java:9`, `IProgressInfoHandler.java:3` |
| Inner screen | `GuiInnerScreen(IGuiWrapper, x, y, w, h, Supplier<List<Component>>)`, `.padding(int)`, `.spacing(int)` | `gui/element/GuiInnerScreen.java:42, 63, 54` |
| Lists | `GuiTextScrollList(IGuiWrapper, x, y, w, h)` (10 px rows), `setText(List<String>)`, `getSelection()`, `clearSelection()` | `gui/element/scroll/GuiTextScrollList.java:17, 45, 36, 41` |
| Buttons | `MekanismButton(IGuiWrapper, x, y, w, h, Component, IClickable)`; `GuiElement.IClickable.onClick(GuiElement, double, double)` | `gui/element/button/MekanismButton.java:23`, `gui/element/GuiElement.java:728` |
| Energy tab | `GuiEnergyTab(IGuiWrapper, IInfoHandler)` at (-26, 137); `GuiTexturedElement.IInfoHandler { List<Component> getInfo() }` | `gui/element/tab/GuiEnergyTab.java:39`, `gui/element/GuiTexturedElement.java:21` |
| Windows need common types | `GuiWindow(IGuiWrapper, x, y, w, h, WindowType)` and `GuiWindowCreatorTab.createWindow(SelectedWindowData)`: both types are in `mekanism.common.inventory.container.SelectedWindowData` (D33) | `gui/element/window/GuiWindow.java:69`, `gui/element/tab/window/GuiWindowCreatorTab.java:59`, `common/inventory/container/SelectedWindowData.java:25, 114` |
| Not used | `GuiDynamicHorizontalRateBar`'s colour parameter is `mekanism.common.lib.Color.ColorFunction` (common, internal) | `gui/element/bar/GuiDynamicHorizontalRateBar.java:6` |
