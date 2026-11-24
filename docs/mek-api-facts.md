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
