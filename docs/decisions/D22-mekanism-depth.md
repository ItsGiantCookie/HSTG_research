# D22 — How deep we build on Mekanism

Status: **Spike done, waiting for Hendrik to confirm or change** · Needed by: WP-09 (lab base classes) ·
Spike: WP-05a

The design (docs/design.md §15.1) chose (b) "internals inside `mek/`, with (c) vendoring as the
fallback", to be confirmed after this spike. This file is the evidence. Nothing below has been decided
by the agent.

## What was built

The same throwaway lab twice. Each takes one "pack" (paper) and 10 mB of a chemical to start a unit,
then draws energy every tick for 20 ticks.

| | Variant A: Mekanism tile base | Variant B: own base, Mekanism API only |
| --- | --- | --- |
| Where | `mek/spike/` (internals allowed) | `lab/spike/` (outside `mek/`, so the boundary guard test proves it is API only) |
| Base | `TileEntityConfigurableMachine` + `BlockTile` + `Machine` block type, registered with Mekanism's own deferred registers | Vanilla `BaseEntityBlock` + `BlockEntity`, NeoForge `DeferredRegister` |
| Code | 130 lines (tile, registration, screen) | 175 lines (block, block entity, registration); no GUI, no side config, no upgrades |
| GameTest `mekanismBasedLabRunsAUnit` / `apiBasedLabRunsAUnit` | passed | passed |

Both GameTests insert paper through `Capabilities.ItemHandler.BLOCK`, oxygen through
`mekanism:chemical_handler` and Joules through `mekanism:strict_energy_handler`, all fetched by name
from the top face. Each then waits until one unit is done, the paper and 10 mB are used up, and at least
20 × 50 J are drawn. `./gradlew runGameTestServer`: "All 4 required tests passed".

Every signature used is in docs/mek-api-facts.md, section "WP-05a spike".

## Internal classes each variant touches

**Variant A: 34 internal classes** (plus 6 API types):

- Tile: `TileEntityMekanism`, `TileEntityConfigurableMachine`, `TileComponentEjector`, `MekanismUtils`,
  `TransmissionType`.
- Block, item and attributes: `BlockTile`, `ItemBlockTooltip`, `Machine`, `Machine.MachineBuilder`,
  `AttributeSideConfig`.
- Default faces: `AttachedSideConfig`, `AttachedSideConfig.LightConfigInfo`, `AttachedEjector`,
  `MekanismDataComponents`.
- Containers: `MachineEnergyContainer`, `BasicInventorySlot`, `InventorySlotHelper`, `IInventorySlotHolder`,
  `ChemicalTankHelper`, `IChemicalTankHolder`, `EnergyContainerHelper`, `IEnergyContainerHolder`.
- Registration: `BlockDeferredRegister`, `BlockRegistryObject`, `TileEntityTypeDeferredRegister`,
  `TileEntityTypeRegistryObject`, `ContainerTypeDeferredRegister`, `ContainerTypeRegistryObject`.
- Menu: `MekanismTileContainer`.
- Client: `GuiConfigurableTile`, `GuiVerticalPowerBar`, `GuiChemicalGauge`, `GaugeType`, `GuiEnergyTab`.

**Variant B: 0 internal classes.** It uses 8 API types: `Action`, `AutomationType`, `BasicChemicalTank`,
`IChemicalTank`, `IChemicalHandler`, `IMekanismChemicalHandler`, `IEnergyContainer`,
`IMekanismStrictEnergyHandler`.

## What each variant gets

| Feature | A (Mekanism base) | B (own base) |
| --- | --- | --- |
| Item, chemical and energy handlers, sided | Provided by Mekanism's registers | Hand-written: 3 small handlers. `BasicEnergyContainer` is internal, so B has its own `IEnergyContainer` |
| FE accepted next to Joules (D23) | Free (`EnergyCompatUtils` adds FE) | To write |
| Side config with Item / Chemical / Energy tabs, Configurator, Configuration Card | Free | Prototype has its own (items, energy, data; no chemicals): about 190 lines plus 640 for the screen |
| Auto-eject | Free (`TileComponentEjector`) | Prototype has it for items |
| Speed / Energy upgrade slots and the upgrade window | Free (muffling too) | Prototype has its own: about 140 lines |
| Redstone control, security tab, comparator, facing | Free | Not built |
| Persistence of slots, tanks, energy; contents kept on the dropped item | Free | Hand-written (`saveAdditional` / `loadAdditional`); keep-on-drop not built |
| Mekanism GUI look | Mekanism's own GUI classes | Prototype port `mek/client/MekGui` (MIT, about 120 lines) plus our screen |
| Extra fields (progress, units) saved | Still hand-written | Hand-written |

The prototype's own Mekanism-style lab totals about 1,400 lines: block entity, menu, screen, side
config, upgrades, overlay and packets. Variant A gets the same feel for about 130 lines, but on 34
internal classes.

## Findings that matter for the decision

1. **Default faces live on the item.** A Mekanism machine's default side config is a data component
   (`mekanism:side_config`) on its **item**. It reaches the tile only through
   `applyImplicitComponents` when placed from that item (`TileComponentConfig.java:258`). Any other
   placement leaves every face closed: `setBlock`, a structure template, worldgen, a multiblock or our
   own code. The first run of variant A failed for exactly this reason ("no item handler on the top face").
   Our code would have to apply defaults itself wherever it places lab blocks.
2. **One block per block entity type, registered at mod construction.** `TileEntityTypeDeferredRegister.mekBuilder`
   takes one block holder, and it registers capabilities itself
   (`TileEntityTypeDeferredRegister.java:46, 79`).
   - **Conflict with the design:** docs/design.md §5.5 builds our block entity types in the
     `BLOCK_ENTITY_TYPE` register event from every block that declares a lab role, so KubeJS and other
     mods' blocks are included.
   - **Workaround (not tried in the spike):** build the type ourselves and register Mekanism's capability
     providers ourselves (`CapabilityTileEntity.*_PROVIDER`, `EnergyCompatUtils`). That reaches deeper
     into internals.
3. **Behaviour comes from block attributes.** The tile reads redstone, security, upgrades, GUI, energy
   and side config from attributes on a Mekanism `BlockType` (`TileEntityMekanism.java:343-356`).
   - **KubeJS lab blocks (§11.5):** would have to be `BlockTile`s with a Mekanism block type.
   - **Data-driven stats:** fit, because the energy attribute takes a `LongSupplier`.
4. **Construction-order trap.** `TileEntityMekanism` calls the `getInitial*` hooks from its
   constructor, so a subclass field with an initialiser is silently reset afterwards. This is easy to
   get wrong and invisible until a slot is null.
5. **GUI without the tile base, checked by signature only.** `GuiMekanism` and the gauges, bars and
   energy tab accept any menu (`GuiMekanism.java:51`). The side-config and upgrade tabs require
   `TileEntityMekanism` (`GuiSideConfigurationTab.java:18`, `GuiUpgradeWindowTab.java:21`). This was not
   built.
6. **Stability exposure.**
   - Variant A depends on 34 classes across tile, attribute, registration, attachment, container and
     client packages. Any 10.7.x patch may change them, and the design's mitigations (narrow range, CI
     against the newest 10.7.x, vendor the broken class) all apply.
   - Variant B depends only on 8 API types.

## Options

### (b) Mekanism tile base for every lab block (the design's current choice)
Lab, core, consumer and server blocks are `TileEntityConfigurableMachine` subclasses inside `mek/`.
The prototype's own side config, upgrade component, configurator overlay and most of `ResearchLabScreen`
are deleted.
- **For:** Mekanism parity for free (side config incl. chemicals, ejector, upgrades, redstone, security,
  comparator, FE, persistence, keep-on-drop, GUI). Least code. Players see real Mekanism machines.
- **Against:**
  - 34+ internal classes.
  - Finding 2 conflicts with §5.5. KubeJS and other mods' lab blocks need the deeper workaround, or §5.5
    changes to "one block entity type per lab block, registered at startup".
  - Our placement code must apply item-borne defaults (finding 1).
- **Unverified:** that the §5.5 workaround works. Hendrik's client check of the variant A screen is
  also still outstanding.

### (a) Own base on Mekanism API types only
Labs stay on our own block entity (variant B), and the prototype's side config, upgrades, eject and GUI
port grow into the real thing.
- **For:**
  - Zero internals.
  - §5.5 works as designed: one block entity type collecting every lab block, KubeJS and other mods
    included.
  - No item-borne defaults.
  - Nothing breaks on a Mekanism patch.
- **Against:** about 1,400 lines to own and keep looking like Mekanism. Still to build: chemical side
  config, FE, redstone, security tab, comparator and keep-on-drop.

### (hybrid) Own base, Mekanism GUI classes for drawing only
Logic and registration as in (a). Screens extend `GuiMekanism` and use its gauges, bars and energy tab.
Side-config and upgrade windows stay ours, because Mekanism's need its tile base.
- **For:** §5.5 as designed, and logic free of internals. Gauges and bars look exactly like Mekanism
  and replace most of `MekGui`.
- **Against:**
  - Internals remain, client-side only: about 6 GUI classes, which can break on a patch.
  - Two GUI systems, Mekanism's elements and our own windows, have to match visually.
- **Unverified:** not built in the spike; only the signatures were checked.

## Spike code

Committed once so it can be checked out and looked at, then deleted in the next commit of this branch
(WP-05a acceptance). The facts in docs/mek-api-facts.md stay.

To see variant A's screen: `git checkout 998da48`, `./gradlew runClient`,
`/give @s hstg_labs:spike_mek_lab`. The block has no model or lang, so it renders as the missing
texture and shows a raw name.

## Decision

_Waiting for Hendrik._
