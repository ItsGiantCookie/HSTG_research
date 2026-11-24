# HSTG Research Labs — Design Doc

Nov 23, 2026 · @Hendrik

## 1. Summary

HSTG Research Labs (working name) is a NeoForge 1.21.1 addon that turns History Stages (HS) stages into Factorio-style technologies. Labs consume science-pack items over time, and when a technology's units are done the addon unlocks the stage through HS's public API. HS keeps owning everything a stage locks; this mod owns only how a stage gets researched.

**Goals**

- **G1 Quick setup in game.** Packmakers turn any HS stage into a lab technology and set its cost, packs and tree position in an in-game dev UI, applied live without a restart (§10).
- **G2 Labs that scale.** A single basic lab block, or a distributed AE2-style network of 1 core controller, 1:n item consumers and 1:n workers/servers. Technologies can require specific labs on the network, such as 1 Chemistry Lab + 1 Simulation Server. Lab tiers are natural gates: the basic lab only researches the way to bigger labs (§5.9). Multiblock labs come later in the same roles (§5).
- **G3 Queue and tree.** A research queue with a view of cost, prerequisites and what each technology unlocks.
- **G4 A public, versioned mod API.** Anything another mod may want to read, extend or replace goes through a documented `api` package; our own KubeJS, Jade and dev-UI code use only that API (§9).
- **G5 Extensible by default.** Ingredient types, reward types, research owners, worker types, network nodes and modifiers are id-keyed registries and capabilities, never enums or `instanceof` checks.
- **G6 Scriptable from KubeJS.** Science packs, technologies, research state and new lab types (blocks with their roles and stats) are all definable from scripts, and templates make each a one-liner: define a preset once, stamp packs, labs and technologies from it.
- **G7 Agent-ready codebase.** A pure-logic core with fast unit tests, one adapter onto HS, headless GameTests.

**Non-goals for v1**

- Reimplementing any lock (items, recipes, dimensions, mobs). HS does that.
- Infinite research and research productivity. The modifier API (§9) leaves room for an addon to add them.
- Shipping multiblock labs. v1 fixes the role capabilities they will plug into; the multiblock itself is milestone M5.
- Creating or editing HS stage contents from our dev UI. The HS API has no save path for that; the HS editor stays the tool for what a stage locks.
- Fabric or 26.2 ports.
- Bundling, patching or redistributing HS (see the licence rule in §7).

## 2. Target environment

Build against HS 6.2.0-1.21.1 (published on Modrinth 2026-11-21, git tag `6.2.0-1.21.1` = commit `1a3b8c5`) and declare `[6.2,7.0)`. HS reports `HistoryStagesAPI.API_VERSION = 6`, `API_MINOR = 2` in that release.

| Component | Pin for dev | Declared range | Notes |
| --- | --- | --- | --- |
| Minecraft | 1.21.1 | `[1.21.1]` |  |
| NeoForge | 21.1.249 | `[21.1,)` | Same build HS 6.2.0 compiles against |
| Java | 21 (Temurin) | — |  |
| ModDevGradle | 2.0.140 | — | Same as HS; Parchment 2024.11.17 |
| History Stages | 6.2.0-1.21.1 | `[6.2,7.0)`, required, `AFTER` | Modrinth Maven: `maven.modrinth:history-stages:6.2.0-1.21.1` |
| Lootr | as HS build uses | — | Required by HS; HS will not load without it, dev env included |
| KubeJS | 2101.7.2-build.374 | `[2101.7,)`, optional | `compileOnly` + `localRuntime`, same as HS; needs the JitPack repo at runtime |
| Mekanism | 1.21.1-10.7.19.85 | `[10.7.19,10.8)`, required, `AFTER` | MIT. ModMaven: `mekanism:Mekanism:1.21.1-10.7.19.85`; the narrow range is there because `mek/client` uses Mekanism's GUI classes (§7.2, D22) |
| Jade | latest 1.21.1 | optional | Lab tooltip only (M3) |
| FTB Teams | latest 2101.x | optional | HS's default team provider; we read its team UUID (D31). Without it every player is a team of one |

Gradle sketch (versions above; verify the Lootr file id before first build):

```gradle
repositories {
    exclusiveContent {
        forRepository { maven { url "https://api.modrinth.com/maven" } }
        filter { includeGroup "maven.modrinth" }
    }
    maven { url "https://maven.latvian.dev/releases" }   // KubeJS + Rhino
    maven { url "https://cursemaven.com" }               // Lootr
    maven { url "https://jitpack.io" }                   // KubeJS runtime dep
    maven { url "https://modmaven.dev/" }                // Mekanism
}

dependencies {
    implementation "maven.modrinth:history-stages:6.2.0-1.21.1"
    runtimeOnly    "curse.maven:lootr-361276:8695784"
    compileOnly    "dev.latvian.mods:kubejs-neoforge:2101.7.2-build.374"
    localRuntime   "dev.latvian.mods:kubejs-neoforge:2101.7.2-build.374"
    implementation "mekanism:Mekanism:1.21.1-10.7.19.85"        // full jar: D22 (hybrid) uses Mekanism's client GUI classes
}
```

`neoforge.mods.toml`:

```toml
[[dependencies.hstg_labs]]
modId="historystages"
type="required"
versionRange="[6.2,7.0)"
ordering="AFTER"
side="BOTH"

[[dependencies.hstg_labs]]
modId="mekanism"
type="required"
versionRange="[10.7.19,10.8)"
ordering="AFTER"
side="BOTH"

[[dependencies.hstg_labs]]
modId="kubejs"
type="optional"
versionRange="[2101.7,)"
ordering="AFTER"
side="BOTH"
```

Sources: [History Stages repo](https://github.com/Flix100000/History-Stages) (branch `neoforge-1.21.X`), [Addon Development wiki](https://historystages.github.io/api/addon-development), [Modrinth version list](https://api.modrinth.com/v2/project/history-stages/version).

## 3. Concept: Factorio research on History Stages

Every Factorio research concept maps onto an existing HS piece, so the mod adds only labs, cost, queue and progress. It never writes stage state itself; it only decides when to call HS's unlock.

| Factorio | In this mod | HS piece it rides on |
| --- | --- | --- |
| Technology | Our technology definition, linked to one HS stage by id | `StageEntry`, read-only |
| Prerequisites | Stage requirements in the stage's dependency groups (AND/OR) | `StageGraph.requires`, `StageGraph.state` |
| Cost (count × ingredients × time) | Fields of the technology definition, edited in the dev UI | — |
| Science pack | An ingredient: a tagged item, our data-driven pack item, or any registered ingredient type (fluids etc.) | — |
| Lab | A research site: one basic lab block, or a network of core + consumers + servers, later a multiblock; technologies may require specific labs | — |
| Effects (unlocked recipes, items) | Whatever the stage locks; extra reward types can be registered | HS lock categories, unchanged |
| Force | A research owner: the player's FTB team, or the player alone (D2, D31); a server-wide owner stays available | `StageScope`, `Teams` |
| Research queue | Per-owner queue in our SavedData | — |
| Trigger technology ("craft X to research") | An HS `auto` stage with an `auto_trigger`, shown in the tree, not queueable | `StageMode.AUTO`, `StageProgress.triggers` |
| Research finished | Default reward `unlock_stage` calls `StageStates.unlockGlobal` | `StageEvent.BeforeUnlock` / `Unlocked` |
| Tech tree screen | MVP: list in the lab screen and the dev UI tree; later a player tree screen | `StageGraph` |

Lab technologies use HS mode `external`: HS generates the stage but its Research Pedestal refuses to research it, and unlocking is left to scripts and addons. That keeps the pedestal and the labs from competing for the same stage.

## 4. Gameplay design

A technology costs U units. Each unit needs a fixed set of packs and takes T ticks in a speed-1 lab. A lab claims a unit, consumes one set at the start, and credits the finished unit to the technology it claimed it for.

### 4.1 Technology lifecycle

&#91;embedded content: technology lifecycle · 7 states\]

The mod only drives a technology up to Completing; whether it becomes Researched is HS's answer to the unlock call.

- **Researched is never stored by this mod.** It is read from HS (`StageStates.isUnlockedGlobal`). Our SavedData holds only queue and progress.
- **Unlock refused** (`unlockGlobal` returns false: logic block, lost for good, or a `BeforeUnlock` veto): keep all progress, mark the technology `BLOCKED`, move the queue on, and retry on the next `StageEvent` or every 100 ticks.
- **Unlocked elsewhere** (command, quest, script): drop it from the queue and discard its progress.
- **Relocked** (`StageEvent.Locked` for a lab technology): reset or keep progress per config `on_relock` (default `RESET`, decision D5).
- **Lost for good** (`StageEvent.LostForGood`): shown as lost, not queueable; only an admin unlock brings it back.

### 4.2 Units and labs

- Unit cost = a list of ingredients with amounts, for example `automation_pack × 1, logistic_pack × 1`. Total = U units; T is in ticks (20 per second).
- A worker claims a unit from `ResearchManager`, takes one full ingredient set from its site's consumers, runs T / speed ticks, then credits the unit. Who the worker is and where the packs come from is §5.
- The `done + inFlight < U` cap means a research never overshoots and no pack is wasted at the end.
- A unit started for technology A is credited to A even if the queue head changes mid-unit, because progress is keyed by technology.
- A worker removed mid-unit cancels the unit (`inFlight--`) and returns the consumed set to the consumers it came from, or drops it. Nothing is credited, so this cannot be exploited.
- Workers in unloaded chunks keep their claimed unit reserved; `/hslabs debug` lists reservations so an admin can release stuck ones.
- No per-pack durability as in Factorio; a set is consumed whole at unit start.

### 4.3 Queue

- One queue per research owner; MVP has one owner, the server. Max length `queue_max` (default 7).
- MVP queues only technologies whose `StageGraph.state` is `REACHABLE` and whose non-stage requirements pass `StageStates.checkRequirements`. Auto-queuing missing prerequisites via `StageGraph.missingOnPathTo` is v2 (decision D8), because OR groups make the route ambiguous.
- All labs work on the queue head. Labs that lack a pack type for the head stay idle (Factorio 2.0 behaviour); fall-through to the next item is v2.
- Edit permission: config `queue_permission` = `ANYONE` | `OPS` (default `ANYONE`).
- Empty queue = idle labs. Nothing is picked automatically.

### 4.4 Feedback

- HS already sends chat line, toast and sound on unlock. We optionally cancel `StageAnnounceEvent` for lab technologies and send "Research completed: X" instead (config `announce_style`).
- Lab screen: current technology with icon (`StageEntry.getIcon()`), progress bar `done/U`, ETA, queue, available list, pack slots marked needed / not needed.
- Idle reasons, shown in the screen and in Jade: `NO_RESEARCH`, `MISSING_PACKS`, `NO_POWER`, `CAP_REACHED`, `BLOCKED`.

### 4.5 Tech tree view

- MVP: the lab screen lists available technologies with cost and an unlock preview (first 8 item and recipe ids the stage locks). HS's own stage graph screen stays the full tree.
- v2: a pannable tree screen on a key bind, drawn from the same server snapshot.
- Trigger technologies (`auto` stages) appear with their trigger progress from `StageProgress.triggers` and no pack cost; they cannot be queued.

## 5. Labs: basic, distributed, multiblock-ready

Every lab setup is a **research site** made of three roles: one controller that claims work from the queue, item consumers that take in and use up ingredients, and workers (servers) that supply the compute. A basic lab is all three in one block. A distributed network spreads them over 1 core controller, 1:n consumers and 1:n workers joined by cable, AE2-style. Multiblock labs later fill the same roles, so nothing above this layer changes.

&#91;embedded content: lab topologies · single vs networked\]

Packs go into consumers; workers hold no items. A unit starts only when the core finds a free worker slot and a full ingredient set across the network's consumers.

### 5.1 Roles

| Role | Job | Basic lab | Distributed network | Multiblock (M5) |
| --- | --- | --- | --- | --- |
| Controller | Binds the site to an owner, picks the technology, starts units, enforces capacity, ticks the site | The lab itself | The Research Core, exactly one | The structure controller |
| Item consumer | Accepts ingredients from automation, holds them, uses up one set when a unit starts | The lab's own slots | Each consumer block (1:n), e.g. Pack Consumer, Chemistry Lab | Input hatches |
| Worker / server | Runs units: `parallel` slots at a given `speed` | The lab itself, 1 slot | Each worker block (1:n), e.g. Research Server, Simulation Server | The formed structure |
| Node | Membership in the network graph | None (a site of one) | Core, consumers, workers, cable | The controller joins as one node |

Each role is a NeoForge block capability in the public API (`ResearchCapabilities.``CONTROLLER, CONSUMER, WORKER`, `NODE`), so a block from another mod can take any role without extending our classes.

### 5.2 Basic lab (one block)

- One block that is controller, consumer and worker at once; it ticks itself. The shipped type is `hstg_labs:basic_lab`.
- Slots, speed, tier and lab tags come from its lab type (§5.5). Automation inserts through `Capabilities.ItemHandler.BLOCK`, and `FluidHandler.BLOCK` when its type has tanks.
- It never joins a network (decision D13). It meets only the lab requirements its own single type satisfies, so research gated behind a Chemistry Lab + Simulation Server pair needs a network or a multiblock.
- It is tier 1: it runs only tier-1 technologies, and its job is to research the way to bigger labs (§5.9). This is the MVP lab (WP-09).

### 5.3 Distributed network (AE2-style)

- **Blocks we ship:** Research Core (controller), Pack Consumer (item consumer), Research Server (worker), Research Cable (node). Packmakers and mods add more types per role (§5.5).
- **Forming:** a network is the connected set of blocks with the `NODE` capability. A per-level `GridManager` flood-fills it on place, break and chunk load, caches it, and rebuilds lazily from a dirty flag. Size cap `grid_max_nodes` (default 512).
- **One controller:** no core = network idle (`NO_CONTROLLER`); two or more = all idle (`CONTROLLER_CONFLICT`), as with AE2 controllers.
- **Capacity:** the core's type sets `max_consumers` and `max_workers` (the AE2 channel idea). Members past the cap show `OVER_CAPACITY` and count for nothing, lab requirements included.
- **Throughput:** units running at once = the sum of `parallel` over active workers. A unit takes T divided by the speed of the worker slot running it, times modifiers.
- **Starting a unit:** the core needs one free worker slot and one full ingredient set. It simulates taking each ingredient line from consumers that accept and hold it, then commits all or nothing. One set may be gathered from several consumers (decision D20).
- **Consumers:** each accepts what its type allows (ingredient types and tags, for example items only, or fluids plus `#c:chemistry_packs`) and exposes item and fluid handler capabilities for AE2, pipes and hoppers. Workers hold no items.
- **Ticking:** the core ticks once and drives every worker slot's `LabCycle`; member block entities do not tick, so cost stays O(worker slots) in one place.
- **Fair sharing:** consumers are drained round-robin per ingredient, so one is not emptied while others stay full.
- **Unloading:** core unloaded = the whole network pauses. Unloaded members leave the active set; their claimed units stay reserved.
- **Bonuses:** network-wide modifiers (for example an accelerator node giving +10 % speed) come through the modifier registry (§9), not hard-coded blocks.

### 5.4 Multiblock-ready (built in M5)

- Member stats come from a `LabStatsProvider`: speed, parallel, tier, lab tags, energy per tick, consumer storage. A plain lab block returns its lab type's values; a multiblock computes them from its parts.
- Planned (D16): our own data-driven structure patterns in `data/<ns>/hstg_labs/structure/*.json` (layout, key → block predicate, controller, per-part bonuses), checked on controller use and neighbour changes, then cached.
- A formed structure exposes the same capabilities, so it can be a standalone site (all three roles), a consumer or worker on a network, or a network's core.
- Another mod's machine (for example a Modular Machinery Reborn structure) can fill a role by exposing the capability, without our pattern system.

### 5.5 Lab types: stats in data, blocks from code or KubeJS

A lab type says what a lab block is: its roles, its stats and its lab tags. Stats are data, so they rebalance with `/reload`. Blocks are registered once at startup, by us, by another mod, or by a KubeJS startup script.

- **Definition:** `data/<ns>/hstg_labs/lab_type/<id>.json` holds `roles`, `speed`, `parallel`, `tier`, `energy_per_tick`, `tags` (lab tags such as `hstg_labs:chemistry`), consumer storage (`item_slots`, `fluid_tanks`, accepted ingredient types and tags) and controller capacity (`max_consumers`, `max_workers`). A reload listener loads it, so `/reload` applies changes (decision D17).
- **Base types we ship:** `basic_lab` (all roles), `research_core` (controller), `pack_consumer` (consumer, items), `research_server` (worker), `research_cable` (node only).
- **Blocks:** every lab block names its lab type id, by default its own block id. Our block entity types are built in the `BLOCK_ENTITY_TYPE` register event, after all blocks exist, and collect every block that declares a lab role, so KubeJS and other mods' blocks are included. WP-09 verifies that NeoForge 21.1 accepts blocks added this way.
- **From KubeJS:** block builder types `hstg_labs:basic_lab`, `hstg_labs:controller`, `hstg_labs:consumer` and `hstg_labs:worker` (§11.5). Stats set in the script become a generated lab type entry, which a datapack can still override.
- **From other mods:** ship lab type JSON in the jar and register blocks with our base block classes, or attach our capabilities to their own blocks.
- **Live overrides:** `config/hstg_labs/lab_types/<id>.json` overrides single stat fields. The dev UI writes there, so a packmaker can rebalance a lab on a running server.

### 5.6 Lab requirements on technologies

A technology can demand specific labs, for example 1 networked Chemistry Lab + 1 Simulation Server. Only a site that meets every requirement may claim units of it.

| Requirement type | Fields | Met when |
| --- | --- | --- |
| `hstg_labs:lab` | `lab_type` or `lab_tag`, `count`, optional `role` | The site has at least `count` active members that match |
| `hstg_labs:network` | none | The site is a distributed network, not a basic lab |
| `hstg_labs:min_tier` | `tier` | The worker slot's effective tier (§5.9) is at least this; `labs.min_tier` is the shorthand |
| `hstg_labs:all` / `hstg_labs:any` | `of`: a list of requirements | All, or at least one, of them holds (OR groups) |

```json
"labs": {
  "requires": [
    { "type": "hstg_labs:lab", "lab_tag": "hstg_labs:chemistry", "count": 1 },
    { "type": "hstg_labs:lab", "lab_type": "mypack:simulation_server", "count": 1 }
  ]
}
```

- **Active** = loaded, inside capacity, not faulted, and powered if its type needs energy. A broken cable or an unpowered server drops the site below a requirement at once; running units finish, new ones do not start.
- **Tags before type ids:** `lab_tag: hstg_labs:chemistry` lets any mod's lab carrying that tag count; `lab_type` pins one exact lab.
- **Extensible:** addons register more requirement types through `RegisterLabRequirementTypesEvent` (§9.2), for example "needs a cleanroom multiblock" or "only in dimension X".
- **Visible everywhere:** met or missing icons in the tree and lab screens, the skip reason in the core screen, and a picker over loaded lab types and tags in the dev UI.

### 5.7 Which site works on what

Every site works on the head of the queue, as in Factorio (decision D18). A head that a site cannot run (tier too low, lab requirements missing) leaves that site idle until the queue changes, so queue order is part of the gameplay.

- Each controller claims units only for its owner's queue head, and only while it meets the head's tier, lab requirements and ingredient types and the head's `done + inFlight < U`.
- Several sites may work on the same technology at once; progress is shared per owner, as in Factorio.
- A site that can run nothing idles with a reason: `NO_RESEARCH`, `LAB_TIER` (names the tier needed), `LAB_REQUIREMENTS` (names the missing one), `MISSING_INGREDIENTS`, `NO_POWER`, `CAP_REACHED` or `BLOCKED`.
- Queueing a technology no current site can run is allowed, but the queue marks it "no lab can run this yet". When it reaches the head, the queue screen and a chat line warn that labs are idle and why, so players can reorder.

### 5.8 What labs take from Mekanism

Mekanism is a required dependency, so labs behave like Mekanism machines instead of reinventing tanks, power and upgrades. How much of Mekanism's internal code that relies on is decision D22 (§7.2).

- **Chemicals as ingredients:** ingredient type `hstg_labs:chemical` (a chemical id or chemical tag, amount in mB). Consumers hold Mekanism chemical tanks next to their item slots, so a "liquid" science pack needs no item at all.
- **Energy:** labs, servers and cores store Joules and accept both Mekanism strict energy and FE, as Mekanism machines do. `energy_per_tick` in lab types is in Joules.
- **Upgrades:** Mekanism Speed and Energy upgrade cards fit labs and servers. Each card applies as a worker modifier through our modifier registry (§9), so it stacks with every other modifier the same way.
- **Machine plumbing (our own code, D22 hybrid):** side configuration and auto-eject on consumers, redstone control, the Configurator and Configuration Card. The Mekanism GUI look with gauges and an energy bar comes from Mekanism's own GUI classes.
- **Security:** Mekanism's owner and private/trusted modes on cores and lab blocks; dev-UI test tools respect it.
- **Multiblock (M5):** a Research Facility built on our own data-driven patterns (D16); Mekanism blocks can appear in a pattern like any other block.
- **Pack production:** science packs are made in Mekanism machines with ordinary Mekanism recipes, or the KubeJS Mekanism addon for scripts. Nothing in this mod is needed for that.

### 5.9 Lab tiers as gates

Every lab type has a tier, every technology a minimum tier, and the labs of each next tier are themselves unlocked by research. That makes the lab ladder the spine of a pack: the basic lab cannot run everything, it exists to research the way to bigger labs.

&#91;embedded content: lab tier ladder · 4 tiers, 3 unlock steps\]

With a strict queue head (D18), a technology above the team's best tier simply waits at the head until the next labs are built.

| Tier | Name | Lab setup | Runs | Typical unlock at this tier |
| --- | --- | --- | --- | --- |
| 1 | Basic | Basic Lab, one block | Tier-1 technologies only, one unit at a time, item packs only | "Research Networking": the stage holding the Research Core, Pack Consumer and Research Server recipes |
| 2 | Advanced | Network: Advanced core, Pack Consumers, Research Servers | Tier ≤ 2, parallel units, chemical packs | Specialised labs (Chemistry Lab, Simulation Server) and the Elite core |
| 3 | Elite | Network with specialised members carrying lab tags | Tier ≤ 3, plus tag requirements | Research Facility parts |
| 4 | Ultimate | Research Facility multiblock (M5) | Tier ≤ 4 | Endgame technologies |

This ladder ships as data and is only a default; packs rename, add or drop tiers.

- **Effective tier of a worker slot** = min(worker tier, controller tier). A basic lab is its own controller, so its tier is simply its type's tier. A network with an Advanced core and an Elite server runs at tier 2 until the core is upgraded; workers whose effective tier is below the queue head's minimum idle (decision D29).
- **The gate check comes first:** a technology's `labs.min_tier` (default 1) is compared with the slot's effective tier before any other lab requirement. Too low means the site idles while that technology is the queue head, with idle reason `LAB_TIER`, which names the tier needed.
- **The ladder is built from HS locks, nothing new:** the next tier's lab blocks are items and recipes locked in an HS stage, and that stage is a technology the current tier can run.
- **Basic lab limits are data, not code:** its lab type sets `tier: 1`, `parallel: 1`, ingredient type `item` only and no lab tags. A pack can loosen or tighten any of them.
- **Templates follow tiers:** `hstg_labs:tiered` sets `labs.min_tier` from a `lab_tier` param that defaults to its cost `tier`, so cost and gate move together unless a packmaker splits them.
- **Tiers are data:** `data/<ns>/hstg_labs/lab_tier/<level>.json` gives each integer level a name, colour and icon. Plain integers keep comparisons simple and let a pack insert levels (decision D28). By default the four levels reuse Mekanism's `BaseTier` names and colours: Basic, Advanced, Elite, Ultimate.
- **Upgrading in place:** a core or lab moves up a tier without being broken, keeping its contents and claimed units, with our own tier upgrade items, one per step (decision D30); Mekanism's tier installers do not act on our blocks.
- **Softlock check:** validation walks the ladder (§8.3). For each tier N above 1, some lab type of tier N must be obtainable after researching only technologies below N, or the pack is flagged before a player gets stuck.

### 5.10 Research owners: teams

Research belongs to a team from 0.1.0 (decision D2): each team has its own queue, progress and labs, like separate forces in Factorio.

- **Team identity:** the FTB Teams team UUID through FTB Teams' API, a soft dependency (D31). With another HS team provider, or a player in no team, the player researches as a team of one.
- **Labs belong to a team:** the team of the player who placed the lab or core (its Mekanism security owner). A site only claims units for its own team's queue head.
- **Unlocking:** technologies use HS individual stages. On completion the mod calls `StageStates.unlockIndividual(id, uuid, server)` for every current member, online or offline; HS writes it for offline players and syncs at their next login. Validation warns when a technology's stage is not set to share with the team, because HS's own unlock paths would then treat members separately.
- **Membership changes:** joining a team adopts the team's queue and progress (the player's solo progress is kept but unused); leaving starts again as a team of one. Stages already unlocked stay unlocked, as they are the player's own HS individual stages.
- **Server owner still built in:** config `owner_model = TEAM | SERVER` (default `TEAM`). `SERVER` uses global stages and one shared queue, as in the first draft.
- **Queue permission** (`queue_permission`) applies inside the team; ops can edit any team's queue.

## 6. Architecture

The mod is a pure-logic `core` wrapped by thin Minecraft layers, and exactly one package, `hs`, talks to History Stages. That split lets an agent test most behaviour in seconds without starting Minecraft, and keeps every HS assumption in one reviewable place.

A public `api` package sits above everything: our own features (labs, network, KubeJS, Jade, dev UI) are built on it exactly as another mod would build on it, and guard tests keep it that way.

&#91;embedded content: module layers · what calls what\]

Arrows point from caller to callee; `research` is the only writer of progress, and the client reaches it only through validated packets.

### 6.1 Packages

Base package `io.github.itsgiantcookie.hstglabs` (decision D1).

| Package | Holds | May import |
| --- | --- | --- |
| `api` | The public contract: interfaces, records, capabilities, events, `Register…Event`s, `HSLabsAPI` version constants (§9) | JDK, Minecraft, NeoForge; nothing internal |
| `core` | `TechCost`, `ResearchProgress`, `ResearchQueue`, `LabCycle`, `Arbiter` (round-robin set sharing), `TechState` | JDK only |
| `hs` | `HsBridge` port + `HistoryStagesBridge`, HS event listeners, stage checks, report section | `core`, `api`, HS api allowlist (§7), NeoForge |
| `tech` | `TechDefinition` codec, `TechRepository` (merges file, KubeJS and addon sources), reload, validation | `api`, `core` |
| `research` | `ResearchManager` (one per server), `ResearchSavedData`, frozen registries | `api`, `core`, `tech`, `hs` interface |
| `lab` | Basic lab block, base block classes, block entity types, menu, lab type loader and live overrides | `api`, `research`, `core` |
| `grid` | Core, consumer, worker (server) and cable blocks; `GridManager`; site scheduling and lab requirement checks | `api`, `research`, `core` |
| `packet` | Payload records, `StreamCodec`s, handlers | `api`, `research`, `tech` |
| `client` | `LabScreen`, `CoreScreen`, `ClientResearchCache` | `api`, `packet`, HS `api.client` |
| `devui` | Dev UI screens (client) + `DevEditService` (server, permission-checked) | `api`, `tech`, `packet` |
| `command` | `/hslabs`, including every dev-UI action as a subcommand | `api`, `research`, `tech`, `hs` |
| `compat.kubejs`, `compat.jade` | Plugins, loaded only when the other mod is present; KubeJS lab block builder types | `api` only |
| `demo` | Example addon, registered only with a system property in dev runs | `api` only |
| `mek` | Mekanism adapter: chemical ingredient type, capabilities by name, energy and upgrade glue, side config, GUI pieces (D22 hybrid) | api, core, Mekanism API; `mek/client` alone may also use Mekanism's client GUI classes (`mekanism.client..`) |

### 6.2 Persistence

- **Definitions** (what a technology costs, what packs exist) live in files, not SavedData: `config/hstg_labs/technologies/*.json` and `config/hstg_labs/packs/*.json`, one file per entry, sorted keys, so they diff cleanly in git and ship with a modpack (§8).
- **State** (queue, progress) lives in `ResearchSavedData` on the overworld, `data/hstg_labs_research.dat`, with an integer `schema` for migrations. Shape: `owners: { "<owner id>": { queue, progress: { techId: { done, inFlight } }, pendingUnlock } }`.
- Each worker block entity stores its own claimed unit (tech id, ticks left, consumed set), so a reload resumes mid-unit.
- Networks are never saved; `GridManager` rebuilds them from the blocks on load.
- On a definitions reload or `StageDefinitionsChangedEvent`: rebuild the technology index; if U shrank below `done`, the technology goes to `pendingUnlock`; progress of removed technologies is kept and listed by `/hslabs debug` (decision D7).

### 6.3 Client–server sync

NeoForge 1.21.1 `RegisterPayloadHandlersEvent` with `CustomPacketPayload` + `StreamCodec`, protocol version `1`.

| Payload | Direction | Sent when | Content |
| --- | --- | --- | --- |
| `TechSnapshot` | S → C | Login, definitions changed, any `StageEvent` on a technology, queue change | Per technology: id, display name, icon, cost, prerequisite ids, node state for this player, unlock preview |
| `ResearchProgress` | S → C | Every `progress_sync_interval` ticks (default 20), only to players with a lab or core screen open | Queue head, done, U, units per minute, worker counts by idle reason |
| `QueueEdit` | C → S | Player adds, removes or reorders | `op`, tech id, index. Server re-checks permission and state, then answers with a fresh snapshot |
| `GridStatus` | S → C | Core screen open, every 20 ticks | Members by role and lab type, worker slots active / capacity, consumer contents, met and missing lab requirements per queued technology, faults |
| `DevSnapshot` | S → C | Dev UI opened, after every accepted edit | All definitions with their revision, validation results, HS stage list |
| `DevEdit` | C → S | Dev UI save | Typed edit ops plus the base revision. Server checks permission and revision, applies, writes the file, reloads, broadcasts |

The client never decides anything; it renders the last snapshot.

### 6.4 Ticking and threads

- Single labs tick as block entities; a network core ticks once and drives all its worker slots. Both ask `ResearchManager` for claims; the manager is the only writer of progress.
- Everything runs on the server thread, as the HS API demands. Nothing is async.
- When several technologies complete in one tick, unlock them with the `Collection` overload of `unlockGlobal` so players get one announcement naming all of them (each stage still runs every check and fires its own Unlocked event).

## 7. Dependency contracts: History Stages and Mekanism

### 7.1 History Stages

Everything below was read from the HS source at tag `6.2.0-1.21.1`; an agent must re-verify a signature in the reference checkout before relying on anything not in this table. The one trap to remember: `unlockGlobal` checks logic blocks, losses and vetoes, but **not** the stage's dependency groups, so this mod must gate on prerequisites itself.

| Need | API (package `net.bananemdnsa.historystages`) | Behaviour that matters here |
| --- | --- | --- |
| List stages | `api.stage.StageStates.loadedStages()` → `List<StageEntry>` | Read-only snapshot, global first. `StageEntry` lives in `data` but the api docs call it as stable as the api |
| Find one stage | `StageStates.findStage(id)`, `scopeOf(id)`; `StageEntry.getMode()` | Dev UI picker and validation |
| Has the world got it | `StageStates.isUnlockedGlobal(id, level)`, `isUnlockedIndividual(id, player)`, `isUnlockedFor(id, player)` |  |
| Unlock | `StageStates.unlockGlobal(id, level)` → `boolean`; `unlockGlobal(Collection, level)` → newly unlocked ids; `unlockIndividual(id, player)` | Refused while logic-blocked, lost for good, or vetoed. Does the SavedData, sync, recipe reload, caches, event, toast. Never replicate any of it |
| Admin unlock | `StageStates.forceUnlockGlobal(id, level)` → `UnlockOutcome` | Skips blocks and vetoes. Only for our admin command and GameTest setup |
| Relock | `StageStates.relockGlobal(id, level)` | Only for tests and the admin command |
| Prerequisites | `api.stage.StageGraph.requires(StageRef)`, `requiredBy`, `state(StageRef, ServerPlayer)` → `UNLOCKED / REACHABLE / BLOCKED / LOCKED`, `missingOnPathTo` | Only stage requirements make edges. Server-side only (needs a `ServerPlayer`) |
| Other requirements | `StageStates.checkRequirements(id, player)` → `RequirementResult` | Items or XP deposited at a pedestal always read as unmet here, so validation warns about them |
| Blocked / lost | `StageStates.isBlocked(id, player)`, `isLostForGood(id, player)` |  |
| React to changes | `api.stage.StageEvent.Unlocked / Locked / IndividualUnlocked / IndividualLocked / LostForGood`, `getCause()` | NeoForge game bus. Our own unlocks carry cause `ADDON` |
| Veto | `StageEvent.BeforeUnlock` (cancellable) | Fired for normal unlocks, ours included; another addon can refuse us |
| Definitions reloaded | `api.stage.StageDefinitionsChangedEvent` | Game bus, once at end of the tick, several changes coalesced |
| Announcements | `api.stage.StageAnnounceEvent` (cancellable) | Cancel to send our own "Research completed" line |
| Display | `StageStates.displayNameFor(id, player)`, `StageEntry.getIcon()`, `getItems()`, `getRecipes()`; client `api.client.ClientStages` |  |
| Validation | `api.diagnostics.RegisterStageChecksEvent`, `ctx.entry()`, `ctx.scope()`, `ctx.problems().warn(…)` | Our stage-related warnings land in the HS debug report |
| Report / config | `RegisterReportSectionsEvent`, `api.config.RegisterConfigSectionsEvent` |  |
| Teams | `api.team.Teams.teammates(player)`, `isSharedWithTeam(id)` | For the team owner type |
| Per-stage settings | `api.settings.RegisterStageSettingsGroupsEvent`, `StageSettingsGroup.store(stage, values)` | Not used for cost: no public save path (§8). Upstream ask: a save call would let the HS editor show a read-only research card |
| Version | `api.HistoryStagesAPI.API_VERSION` (6), `API_MINOR` (2) | Log both at startup |

**Rules for code that touches HS**

1. Import only `net.bananemdnsa.historystages.api..` plus the allowlist `data.StageEntry`, `data.DependencyGroup`, `data.StageMode`. A guard test enforces it (§9).
2. Only the `hs` package (and `command` for admin calls) imports HS.
3. Call the server api from the server thread only; nothing in HS locks.
4. Register only inside the matching mod-bus `Register…Event` listener; registries freeze after dispatch.
5. Namespace every id `hstg_labs:`; `historystages:` is reserved and `build()` rejects it.
6. **Licence:** HS is All Rights Reserved. Depending on it and reading its source are fine; copying its code, jar-in-jar bundling or redistributing it are not.
7. **Version drift:** HS's javadoc says a 6.x release may raise `API_VERSION` to 7 on its own. CI builds against the newest 6.x; if the generation moves, narrow the declared range.

### 7.2 Mekanism

Mekanism is MIT-licensed (Aidan C. Brady), so unlike HS we may use and copy anything; the real constraint is stability. Its API jar covers chemicals, energy, upgrades, security and recipes. The pieces that make a block feel like a Mekanism machine (tile base, side config, GUI, multiblock framework) are internal and can change in any 10.7.x release. Facts below come from branch 1.21.x (commit 4255aa7, mod version 10.7.19); the reference checkout pins tag `v1.21.1-10.7.19.85``.` `MekanismAPI.API_VERSION` there is `10.7.15`.

| Need | Mekanism piece | API or internal | Notes |
| --- | --- | --- | --- |
| Chemical ingredients and tanks | `mekanism.api.chemical`: `Chemical`, `ChemicalStack`, `IChemicalTank`, `BasicChemicalTank`, `IChemicalHandler` | API | Gas, infuse type, pigment and slurry are one `Chemical` type on 1.21 |
| Chemical capability | `mekanism:chemical_handler`, sided, type `IChemicalHandler` | Defined internally (`Capabilities.CHEMICAL`) | Reach it without internals: `BlockCapability.createSided(id, IChemicalHandler.class)` returns the same instance, because NeoForge's `CapabilityRegistry.create` hands back an existing capability with that name |
| Energy | `mekanism.api.energy`: `IStrictEnergyHandler`, `IEnergyContainer`; FE via NeoForge `EnergyStorage` | API | Same capability-by-name trick for `mekanism:strict_energy_handler` |
| Upgrades | `mekanism.api.Upgrade` (`SPEED`, `ENERGY`, …) | Enum is API; the upgrade slot component `TileComponentUpgrade` is internal | Effects go through our modifier registry |
| Security | `mekanism.api.security.IBlockSecurityUtils` | API | Owner and security capabilities |
| Recipes | `mekanism.api.recipes`, `MekanismRecipeTypes` | API | Pack production chains |
| Tile base, side config, ejector, redstone | `TileEntityMekanism`, `TileComponentConfig`, `TileComponentEjector` | Internal | The "Mekanism machine" behaviour |
| GUI | `GuiMekanism` and its elements (gauges, energy bar, side-config tab) | Internal |  |
| Multiblock (M5) | `mekanism.common.lib.multiblock`: `FormationProtocol`, `MultiblockData`, `CuboidStructureValidator`, `MultiblockManager` | Internal | Not used: the Research Facility uses our own patterns (D16) |

**Rules for code that touches Mekanism**

1. D22 (hybrid): outside `mekanism.api..` only Mekanism's client GUI classes (`mekanism.client..`), and only in `mek/client`; tile base, components, registration and attachments are not used. A guard test enforces it, as for HS. `core` imports no Mekanism at all.
2. Facts before code: `ref/Mekanism` at the pinned tag, every signature recorded with `path:line` in `docs/mek-api-facts.md`.
3. Our public `api` may name Mekanism API types (`ChemicalStack`), because Mekanism is required. It never names Mekanism internal types.
4. Copying Mekanism code is allowed only where decision D22 says "vendor". A copied file keeps the MIT copyright notice in its header and is listed in `THIRD_PARTY_NOTICES`.
5. Declared range `[10.7.19,10.8)`. CI also builds against the newest 10.7.x on ModMaven; a break in internals is fixed inside `mek` only.
6. Mekanism's `1.21.11` branch is out of scope, like other ports.

## 8. Data model and configuration

Technologies and science packs are this mod's own JSON files that reference HS stages by id. The dev UI writes them, packmakers commit them with the pack, and a reload applies them live. HS stays the source of truth for prerequisites and for what a stage locks.

Why not HS's per-stage settings group, as the first draft proposed: the HS API can set a settings group on an in-memory `StageEntry`, but has no public call that saves the stage file and syncs it. A dev UI could not persist through it without reaching into HS internals.

### 8.1 Technology definition

`config/hstg_labs/technologies/<id>.json`, one technology per file. Addons and KubeJS can contribute more through tech sources (§9); merge order is files, then addon sources, then KubeJS (decision D3).

```json
{
  "stage": "polymer_science",
  "cost": {
    "units": 120,
    "unit_time": 600,
    "ingredients": [
      { "type": "hstg_labs:item", "item": "kubejs:automation_pack", "amount": 1 },
      { "type": "hstg_labs:pack", "pack": "hstg_labs:chemical", "amount": 1 }
    ]
  },
  "labs": {
    "requires": [
      { "type": "hstg_labs:lab", "lab_tag": "hstg_labs:chemistry", "count": 1 },
      { "type": "hstg_labs:lab", "lab_tag": "hstg_labs:simulation", "count": 1 }
    ]
  },
  "rewards": [ { "type": "hstg_labs:unlock_stage" } ],
  "tree": { "category": "chemistry", "x": 6, "y": 2 },
  "addons": {}
}
```

| Field | Default | Meaning |
| --- | --- | --- |
| `stage` | required | HS stage id; its scope decides global or individual unlock |
| `cost.units` | required, 1 – 1,000,000 | U |
| `cost.unit_time` | required, 1 – 72,000 ticks | T at speed 1 |
| `cost.ingredients` | at least one | Each entry names a `type` from the ingredient registry plus that type's fields |
| `labs.require``s` | 0, none | Lab requirements a site must meet to run it (§5.6); empty = any site |
| `labs.min_tier` | 1 | Lowest effective lab tier that may run it (§5.9); checked before labs.requires |
| `rewards` | `[unlock_stage]` | Reward registry; addons add types (run a function, grant an advancement, a KubeJS callback) |
| `tree.category`, `tree.x`, `tree.y` | none | Layout for the dev UI and tree view only |
| `addons` | empty | Raw per-addon data, kept untouched when that addon is missing |

### 8.2 Science packs

Two kinds, usable side by side (decision D11):

- **Any item**, through ingredient types `hstg_labs:item` and `hstg_labs:tag`: KubeJS items, modded items, vanilla items.
- **Our data-driven pack item:** one item `hstg_labs:science_pack` whose data component `hstg_labs:pack` names a pack defined in `config/hstg_labs/packs/<id>.json` (`name`, `color`, `tier`, `model`). New packs appear without a restart, which ordinary item registration cannot do, and that is what makes setup from the dev UI quick. A recipe outputs one as `hstg_labs:science_pack[hstg_labs:pack="hstg_labs:logistic"]`.

A third kind comes from Mekanism: ingredient type `hstg_labs:chemical` takes any Mekanism chemical or chemical tag, so a science pack can be a chemical held in consumer tanks (§5.8).

Lab and core slots accept any item that some loaded technology uses as an ingredient, computed on reload; the optional tag `#hstg_labs:science_packs` adds more.

### 8.3 Validation

Runs on every load and every dev-UI edit. Results show in the dev UI and the log; stage-related ones also go into HS's debug report through `RegisterStageChecksEvent`.

- **Error** (technology disabled): unknown stage; two technologies on one stage; unknown ingredient, item, pack, reward or lab requirement type; a requirement naming an unknown lab type or a lab tag no loaded type carries; amounts, units, counts or time out of range.
- **Error** (lab type disabled): unknown role; a consumer with no storage; a controller with `max_workers` 0; `parallel` or `speed` out of range.
- **Warning:** stage not in mode `external`; stage scope the active owner model cannot serve; stage carries pedestal-deposited requirements labs can never meet; requirements only a network can meet while no controller type is loaded; an ingredient no loaded consumer type accepts.

**Tier ladder checks** (warnings, shown in the dev UI as a ladder view):

- **Softlock:** for each tier N above 1, no lab type of tier N is obtainable after researching only technologies below tier N. "Obtainable" means its block item and recipe are not locked by any HS stage, or are locked only by stages whose technologies need a lower tier.
- **Unreachable technology:** its `labs.min_tier` is higher than every loaded lab type's tier, or needs a lab tag no lab type of that tier carries.
- **Locked starter:** the basic lab itself, or its recipe, sits behind a technology.
- **Empty tier:** a technology tier with no technologies, or a lab tier with no lab types, between two used tiers.

### 8.4 Server config `hstg_labs-server.toml`

| Key | Default | Meaning |
| --- | --- | --- |
| `global_speed` | 1.0 | Multiplier on every worker |
| `queue_max` | 7 | Queue length |
| `queue_permission` | `ANYONE` | `ANYONE` or `OPS` |
| `on_relock` | `RESET` | `RESET` or `KEEP` progress when a technology is relocked |
| `announce_style` | `HS` | `HS` keeps HS's message; `RESEARCH` sends "Research completed" |
| `progress_sync_interval` | 20 | Ticks between progress packets |
| `grid_max_nodes` | 512 | Largest network |
| `dev_ui_enabled` | true | Dev UI available at all |
| `dev_ui_permission_level` | 2 | Op level needed to open and save the dev UI |

Per-lab values (slots, speed, energy, tier, capacity) live in lab types (§5.5), not here.

### 8.5 Recipes

Default recipes for basic lab, core, pack consumer, research server and cable ship as ordinary datapack recipes, so KubeJS or a datapack can replace them.

### 8.6 Templates

A template is a named, parameterised preset for one kind of content: a science pack, a lab block, a lab type, a technology or a test network. Packmakers define a look and a cost curve once, then stamp content from it in one line. Changing the template rebalances every instance on the next reload.

**Kinds and where they apply**

| Kind | Makes | Defined in | Change needs |
| --- | --- | --- | --- |
| `hstg_labs:pack_item` | A real pack item (model, tint, rarity, tooltip) | KubeJS startup, code | Restart |
| `hstg_labs:pack` | A data-driven pack (`hstg_labs:science_pack` entry) | JSON, KubeJS server, dev UI | `/reload` |
| `hstg_labs:chemical_pack` | A Mekanism chemical used as a pack | KubeJS startup, code | Restart |
| `hstg_labs:lab_block` | A lab block for one role, with generated model, lang and lab type | KubeJS startup, code | Restart (stats: `/reload`) |
| `hstg_labs:lab_type` | Lab stats only (speed, parallel, tanks, tags, energy) | JSON, KubeJS server, dev UI | `/reload` |
| `hstg_labs:technology` | A technology: cost, ingredients, lab requirements, rewards, tree category | JSON, KubeJS server, dev UI | `/reload` |
| `hstg_labs:network_preset` | A test network layout for dev-UI test tools and the balance panel | JSON, dev UI | `/reload` |

Addons add kinds through `RegisterTemplateKindsEvent` (§9.2).

**Built-in templates we ship**

| Template | Kind | Params |
| --- | --- | --- |
| `hstg_labs:flask` | `pack_item` | `color`, `tier`, `rarity` |
| `hstg_labs:data_flask` | `pack` | `color`, `tier` |
| `hstg_labs:chemical_pack` | `chemical_pack` | `color` |
| `hstg_labs:basic_lab`, `core`, `consumer`, `server`, `cable` | `lab_block` | `color` (accent), `tags`, role stats |
| `hstg_labs:tiered` | `technology` | `tier`, `packs`, `base_units` (25), `factor` (2), `unit_time` (200), `category` |
| `hstg_labs:starter_network` | `network_preset` | consumers, workers |

`hstg_labs:tiered` sets units = base\_units × factor^(tier − 1) and unit time = unit\_time × tier. It also sets `labs.min_tier` from the param `lab_tier`, which defaults to `tier`, so cost and lab gate move together.

**JSON form** (`config/hstg_labs/templates/<kind>/<id>.json`; datapacks may ship them too):

```json
{
  "kind": "hstg_labs:technology",
  "extends": "hstg_labs:tiered",
  "params": { "base_units": 30, "factor": 3 },
  "defaults": { "tree": { "category": "${category}" }, "rewards": [ { "type": "hstg_labs:unlock_stage" } ] }
}
```

An instance in a technology file keeps the link instead of a copy:

```json
{
  "stage": "steam_power",
  "template": "mypack:main",
  "params": { "tier": 1, "packs": ["kubejs:automation_pack"], "category": "energy" },
  "overrides": { "cost": { "unit_time": 400 } }
}
```

**Rules**

- Resolution order: root template → each `extends` child → `params` → `overrides` → KubeJS `modify`. Later wins, field by field.
- JSON templates know `${param}` substitution and the curves built into Java templates such as `hstg_labs:tiered`, and a small expression language (decision D24): any string value starting with = is evaluated, e.g. "units": "=base\_units \* factor^(tier - 1)". It knows numbers, params, + − × ÷ ^, min, max, round, floor, ceil and if(cond, a, b); no loops, no side effects, a length limit, evaluated once per resolution. The parser lives in core (pure Java, fuzz-tested) unless WP-07a finds a bundled library such as EvalEx (Apache-2.0) cheaper. KubeJS templates remain plain JavaScript.
- Params are typed (`int`, `number`, `color`, `id`, `id_list`, `string`) with defaults. A missing required param is an error; an unknown one is a warning.
- `extends` cycles, unknown templates and a kind mismatch are errors that disable only the affected instances.
- Instances stay linked to their template (decision D27). The dev UI shows each value's origin (template, param, override) and can detach an instance into a plain definition.
- Validation (§8.3) runs on the resolved result, so a template can never produce what a hand-written file could not.

## 9. Public mod API

Other mods build on HSTG Research Labs through one `api` package with a written stability promise, modelled on HS's own: mod-bus `Register…Event`s to extend, game-bus events to react, block capabilities to take a role in the lab system, and static facades to query and command. Our KubeJS, Jade and dev-UI code are the first clients of that API.

### 9.1 Contract

- `io.github.itsgiantcookie.hstglabs.api` and `api.client` are public; everything else is internal and may change without notice.
- `HSLabsAPI.API_VERSION` and `API_MINOR`. Breaking changes wait for a new `API_VERSION`; every API change is in the changelog.
- Guard tests: no public signature in `api` names an internal type; `compat`, `devui` and `demo` import nothing of ours outside `api`; every `Register…Event` refuses registration after dispatch.
- An in-repo `demo` addon exercises every extension point and is test-enforced to use only `api`, so whatever it needs, a real addon gets too.
- Javadoc on every public type, a how-to per extension point in `docs/api.md`, sources and javadoc jars published with each release (decision D14).
- Server API on the server thread, client API on the client thread; nothing in it locks.

### 9.2 Extension points (mod bus, frozen after dispatch)

| Event | Registers | Example |
| --- | --- | --- |
| `RegisterIngredientTypesEvent` | `IngredientType<T>`: codec, stream codec, matcher, display, storage kind (item, fluid, custom) | Fluid packs; Mekanism chemicals in a compat addon |
| `RegisterRewardTypesEvent` | `RewardType<T>`: codec, `apply(owner, tech)` | Grant an advancement, run a function |
| `RegisterOwnerTypesEvent` | `OwnerType`: player → owner id, members, which stage scope it unlocks | Server (built in), team via HS `Teams`, single player |
| `RegisterTechSourcesEvent` | `TechSource`: supplies technology definitions on every reload | A mod that ships its own tech tree in code |
| `RegisterLabRequirementTypesEvent` | `LabRequirementType<T>`: codec, `test(ResearchSite)`, display | "Needs a cleanroom multiblock", "only in the Nether" |
| `RegisterLabTypeSourcesEvent` | `LabTypeSource`: lab types from code, merged after data files and before config overrides | A mod whose lab stats follow its own config |
| `RegisterLabStatsProvidersEvent` | `LabStatsProvider` factories per block entity type | Tiered labs, multiblock controllers |
| `RegisterModifiersEvent` | Modifier on speed, cost, energy or parallel, scoped to worker, site, owner or technology | A "research speed +10 %" reward; an accelerator node |
| `RegisterIdleReasonsEvent` | Id, lang key, icon | A modded worker reporting "no mana" |
| `RegisterStructurePatternsEvent` (M5) | Code-defined multiblock patterns (data patterns need no code) | Patterns with custom part logic |
| Client: `RegisterIngredientRenderersEvent`, `RegisterTechNodeDecoratorsEvent`, `RegisterDevPanelsEvent`, `RegisterRequirementEditorsEvent` | How ingredients and nodes draw; extra dev-UI panels; editors for addon requirement types | An editor for an addon's own lab requirement |
| `RegisterTemplateKindsEvent` | `TemplateKind<T>`: target codec, typed param schema, resolver, dev-UI editor hint; plus code templates of existing kinds | A reward-template kind for an addon's own reward type; a Mekanism-addon pack template |

The four lab roles (controller, consumer, worker, node) are the one fixed set, because the scheduler is built on them. Everything inside a role is open: lab types, stats, tags, requirement types and modifiers.

### 9.3 Capabilities

| Capability | Interface | Implemented by |
| --- | --- | --- |
| `ResearchCapabilities.CONTROLLER` | `ResearchController`: owner, site view, current claims, idle reason | Basic lab, Research Core, multiblock controller |
| `ResearchCapabilities.CONSUMER` | `IngredientConsumer`: `accepts`, `available`, `take(line, simulate)`, `giveBack(set)` | Basic lab, Pack Consumer, Chemistry Lab, multiblock hatches |
| `ResearchCapabilities.WORKER` | `ResearchWorker`: stats (speed, parallel, tier, lab tags, energy), slots, running cycles | Basic lab, Research Server, multiblock, other mods' machines |
| `ResearchCapabilities.NODE` | `GridNode`: connectable sides, lab type id | Core, consumers, workers, cable |

`ResearchSite` is the read-only view lab requirements test: members by role, lab type and tag, which are active, and whether the site is a network.

### 9.4 Events (game bus)

| Event | Cancellable | Fired when |
| --- | --- | --- |
| `ResearchEvent.BeforeEnqueue` | yes | A player, script or API call adds to a queue |
| `ResearchEvent.QueueChanged` | no | Any queue edit |
| `ResearchEvent.Started` | no | First unit claimed for a technology |
| `ResearchEvent.BeforeUnitClaim` | yes | A worker is about to consume a set (gate by dimension, weather, power) |
| `ResearchEvent.UnitCompleted` | no | A unit is credited |
| `ResearchEvent.Completed` | no | Rewards applied and HS confirmed the unlock |
| `ResearchEvent.Blocked` | no | HS refused the unlock |
| `ResolveCostEvent` | no, mutable | Technology index rebuilt (difficulty scaling) |
| `GridEvent.Formed / Changed / Fault` | no | Network topology or fault changes |
| `TechDefinitionsReloadedEvent` | no | After every technology or lab type reload |

KubeJS events (§11) are forwarded from these, not fired separately.

### 9.5 Facades

- `ResearchApi` (server): owners, queue read and edit, progress, `addProgress`, effective cost, technology state for a player.
- `Technologies` (server and client): read definitions. `GridApi.networkAt(level, pos)`.
- `TechEditor` (server): the same typed edit operations the dev UI sends. Callers check permission themselves; lets other tools (a web panel, a quest mod) edit definitions.
- `ClientResearch` (client): snapshot reads for HUDs and quest books.

### 9.6 Extensibility rules (our own code included)

- Id-keyed registries, never enums, for anything a mod could add.
- Data first: lab types, packs, technologies and structures are JSON with codecs; code registration only for behaviour.
- Capabilities instead of `instanceof`; events instead of direct calls between features.
- Unknown data survives: `addons` blocks and entries of unknown types are kept and re-saved untouched, the pattern HS uses for addon data.
- When our compat or dev UI needs something the API lacks, the API grows; they never reach past it.

## 10. In-game dev UI

An op-only screen (`/hslabs dev` or a key bind) takes a packmaker from "an HS stage exists" to "a working, balanced technology" on a running server, with no restart. Every action in it is also a command and an API call, so nothing is UI-only and everything is testable headless.

### 10.1 Panels

| Panel | What it does |
| --- | --- |
| Technologies | Every HS stage with filters (is technology, mode, scope, has warnings); one click "Make technology" with defaults from a template; multi-select for bulk edits |
| Technology editor | Units, unit time (shown in seconds), ingredient list with a picker over all items, our packs and tags, lab requirements with a picker over loaded lab types and lab tags, rewards, category; a live summary of packs per type and research time |
| Tree | Pannable canvas laid out by `tree.x/y`, with HS prerequisite edges; drag to place; auto-layout by prerequisite depth; nodes coloured by validation state; lab requirement and tier icons on each node; a ladder view groups technologies and lab types by tier and marks softlocks |
| Packs | Create and edit data-driven packs (name, colour, tier, model) live; shows which technologies use each pack |
| Labs | Every loaded lab type with roles, stats, lab tags and source (data, KubeJS, mod, override); edit stats as live overrides; shows which technologies need each type or tag |
| Balance | Bill of materials to reach a chosen technology from the current state: packs per type and total time for a chosen setup (basic labs, or a network of n consumers and m workers); "scale a category's costs by ×f" |
| Validation | Every error and warning from §8.3, each linked to the field that causes it |
| Test tools | For the developer's own world: give packs, set progress, complete or relock a technology, place a stocked test lab or a test network from a template. Admin paths, visibly marked |
| Templates | Built-in, data and KubeJS templates with their params and source; create and edit JSON templates live; stamp technologies, packs or lab type overrides from one; list every instance, show each value's origin, detach an instance |

Addons add panels through `RegisterDevPanelsEvent` and editor widgets for their own ingredient and reward types through the client registries (§9.2).

### 10.2 How an edit travels

1. The client edits a local working copy with undo and redo; nothing is sent before Save.
2. Save sends `DevEdit`: typed operations plus the base revision of each definition touched.
3. The server checks `dev_ui_enabled` and `dev_ui_permission_level`, then the revisions. If someone else saved in between, the edit is refused and the client offers a reload.
4. It validates, writes each file atomically (temp file, then move), reloads the index and broadcasts `DevSnapshot` and `TechSnapshot`.
5. Files keep a stable key order, one technology per file, so git diffs stay small.

### 10.3 Limits

- **Stage contents and prerequisites are read-only here.** HS's API has no save path for stage files, so the dev UI shows them and points to HS's own editor. Upstream ask to the HS authors: an API to save a stage or to open the HS editor on one.
- **Stage mode cannot be switched to `external` from here,** for the same reason. Validation warns; the fix happens in the HS editor.
- **New real items still need a restart.** Data-driven packs (§8.2) are the no-restart path; KubeJS items work as usual after a restart.

### 10.4 Headless parity

Every dev-UI action maps to a `TechEditor` operation (§9.5) and a `/hslabs tech …` subcommand, for example `/hslabs tech create bronze_working units=30 time=300 ingredient=pack:hstg_labs:logistic*1`. GameTests and agents drive those; the screen is one client among several.

## 11. KubeJS integration

KubeJS stays optional and is wired exactly the way HS wires it: a `KubeJSPlugin` named only in `kubejs.plugins.txt`, so its classes never load without KubeJS. Packmakers make science packs with KubeJS; scripts read and steer research through one binding and one event group.

The plugin is a client of the public API (§9) and nothing else: every binding calls a facade, every KubeJS event forwards an API event, and a guard test keeps `compat.kubejs` inside `api`.

**Plugin shape** (same interfaces HS 6.2.0 implements against KubeJS 2101.7.2): `registerEvents(EventGroupRegistry)` adds `HSLabsEvents`; `registerBindings(BindingRegistry)` adds `HSLabs` for server scripts and a small read-only `HSLabs` for client scripts. Startup scripts get nothing, as in HS, because no world exists yet.

### 11.1 Science packs from KubeJS

```js
// kubejs/startup_scripts/science_packs.js
StartupEvents.registry('item', event => {
  event.create('automation_pack').displayName('Automation Science Pack')
  event.create('logistic_pack').displayName('Logistic Science Pack')
})

// kubejs/server_scripts/tags.js
ServerEvents.tags('item', event => {
  event.add('hstg_labs:science_packs', ['kubejs:automation_pack', 'kubejs:logistic_pack'])
})
```

Technologies reference these items directly through the `hstg_labs:item` ingredient type, so the tag is optional; it only widens what lab slots accept. Pack recipes are ordinary KubeJS recipes, so a GT-lite or Mekanism chain can produce them. Data-driven packs (§8.2) need no startup script at all.

### 11.2 Server binding `HSLabs`

| Call | Returns | Purpose |
| --- | --- | --- |
| `HSLabs.isTech(stageId)` | boolean | Stage is a lab technology |
| `HSLabs.cost(stageId)` | `{units, unitTime, ingredients}` or null | Effective cost |
| `HSLabs.current()` | stage id or null | Queue head |
| `HSLabs.queue()` | list of stage ids | Whole queue |
| `HSLabs.progress(stageId)` | `{done, inFlight, total}` | Progress |
| `HSLabs.enqueue(stageId)` / `dequeue(stageId)` | boolean | Same checks as a player edit, without the permission check |
| `HSLabs.addProgress(stageId, units)` | int units credited | Quest rewards, "eureka" moments; capped at U |

### 11.3 Event group `HSLabsEvents`

All server-side; each `supportsTarget` the stage id, as HS's own events do, so `HSLabsEvents.researchCompleted('bronze_working', e => …)` hears one technology and the untargeted form hears all.

| Event | Cancellable | Fired when |
| --- | --- | --- |
| `researchStarted` | no | A technology becomes queue head and a lab claims its first unit |
| `unitCompleted` | no | A lab credits a unit |
| `researchCompleted` | no | HS confirmed the unlock |
| `beforeEnqueue` | yes | A player or script tries to queue a technology |
| `modifyCost` | no | `TechIndex` rebuilds; a script may override units, unit time or ingredients (forwarded ResolveCostEvent) |

```js
// kubejs/server_scripts/research.js
HSLabsEvents.researchCompleted('bronze_working', event => {
  event.server.tell(Text.gold(`Bronze Working researched`))
})
```

Script errors are caught and logged with the script line, never thrown into the lab tick.

### 11.4 Technologies from scripts

Server scripts are a tech source (§9.2), applied after the definition files on every reload. A script can add technologies or adjust ones from files; the dev UI shows script-owned fields as read-only so the two never fight.

```js
// kubejs/server_scripts/technologies.js
HSLabsEvents.technologies(event => {
  event.create('steam_power')
    .stage('steam_power')
    .units(50).unitTime(400)
    .ingredient('kubejs:automation_pack', 1)
    .ingredient('kubejs:logistic_pack', 1)
    .category('energy')

  event.modify('bronze_working', tech => tech.units(tech.units * 2))
})
```

### 11.5 Lab types from scripts

A startup script defines new lab blocks for any role; stats set there become a lab type entry that a datapack or the dev UI can still override.

```js
// kubejs/startup_scripts/labs.js
StartupEvents.registry('block', event => {
  event.create('chemistry_lab', 'hstg_labs:consumer')
    .displayName('Chemistry Lab')
    .labTags('hstg_labs:chemistry')
    .itemSlots(4)
    .fluidTanks(2, 16000)
    .accepts('#c:chemistry_packs')

  event.create('simulation_server', 'hstg_labs:worker')
    .displayName('Simulation Server')
    .labTags('hstg_labs:simulation')
    .speed(2.0)
    .parallel(4)
    .energyPerTick(256)
})

// kubejs/server_scripts/lab_balance.js
HSLabsEvents.labTypes(event => {
  event.modify('kubejs:simulation_server', type => type.parallel(8))
})
```

- The plugin adds the builder types `hstg_labs:basic_lab`, `hstg_labs:controller`, `hstg_labs:consumer` and `hstg_labs:worker` through KubeJS 2101's `registerBuilderTypes` hook. They extend KubeJS's normal block builder, so models, textures, hardness and loot work as for any KubeJS block.
- Stats from the builder become a generated lab type through KubeJS's `generateData` hook. Both hooks exist on `KubeJSPlugin` in the 2101 branch (checked 2026-11-23).
- New blocks need a restart, as every KubeJS block does. Stats, lab tags and lab requirements do not.
- The builders call only our public API (`LabBlocks`, lab type records), so a Java mod can make the same blocks without KubeJS.

### 11.6 Templates in KubeJS

In KubeJS all research content is declared in one startup event, `HSLabsStartupEvents.content`: templates first, then one line per pack or lab (decision D25). A template is a named preset of builder calls; calls written after it still override.

```js
// kubejs/startup_scripts/research_content.js
HSLabsStartupEvents.content(event => {
  // 1) Define your own templates once
  event.packTemplate('mypack:flask', t => t
    .extend('hstg_labs:flask')
    .rarity('uncommon')
    .tooltip(p => Text.gray(`Tier ${p.tier} science`)))

  event.labTemplate('mypack:chem_consumer', t => t
    .extend('hstg_labs:consumer')
    .tags('hstg_labs:chemistry')
    .itemSlots(2)
    .chemicalTanks(2, 16000))

  // 2) Stamp content from them, one line each
  event.pack('automation_pack', 'mypack:flask', { color: 0xd04040, tier: 1 })
  event.pack('logistic_pack',   'mypack:flask', { color: 0x40c040, tier: 2 })
  event.chemicalPack('chemical_science', 'hstg_labs:chemical_pack', { color: 0x40a0ff })

  event.lab('chemistry_lab', 'mypack:chem_consumer', { color: 0x40a0ff })
  event.lab('simulation_server', 'hstg_labs:server', { color: 0x9040ff, tags: ['hstg_labs:simulation'] })
    .speed(2.0).parallel(4)              // plain calls override the template
})
```

```js
// kubejs/server_scripts/research_tree.js
HSLabsEvents.technologies(event => {
  // A JS template: any maths you like
  event.template('mypack:main', (tech, p) => tech
    .units(25 * 2 ** (p.tier - 1))
    .unitTime(200 + 100 * p.tier)
    .ingredients(p.packs.map(id => [id, 1]))
    .category(p.category ?? 'main'))

  event.create('steam_power', 'mypack:main', { stage: 'steam_power', tier: 1, packs: ['kubejs:automation_pack'] })

  event.create('polymer_science', 'mypack:main', {
      stage: 'polymer_science', tier: 3, category: 'chemistry',
      packs: ['kubejs:automation_pack', 'kubejs:logistic_pack'] })
    .chemical('kubejs:chemical_science', 10)
    .requiresLab({ tag: 'hstg_labs:chemistry' })
    .requiresLab({ tag: 'hstg_labs:simulation' })

  // Plain JS loops cover whole chains
  ;[['logistics_1', 1], ['logistics_2', 2], ['logistics_3', 3]].forEach(([id, tier]) =>
    event.create(id, 'mypack:main', { stage: id, tier, packs: ['kubejs:logistic_pack'] }))
})
```

- **How it hooks in:** `HSLabsStartupEvents.content` runs when startup scripts load, before any registry event. Each declaration becomes one of our KubeJS builder types (`hstg_labs:pack`, `basic_lab`, `controller`, `consumer`, `worker`) that our plugin hands to KubeJS during its item and block registration, so models, loot and lang behave exactly like a hand-made KubeJS builder (decision D25). The builder types stay public for anyone who prefers `StartupEvents.registry`. WP-18 first verifies which KubeJS 2101 hook accepts builders from Java (fallback: the event records declarations and a bundled startup script replays them through `StartupEvents.registry`).
- **Generated assets, exportable:** each builder overrides KubeJS's `generateAssets`, `generateData` and `generateLang`. Models come from our base models; textures are recoloured from shipped stencil textures with KubeJS's `stencil(target, stencil, colours)`; display names come from the id. `/hslabs assets export` writes every generated model, texture and lang entry into `kubejs/assets/` as editable files, and an exported file then replaces the generated one (decision D26).
- **Lab stats stay live:** a lab builder writes its stats as a generated lab type. Restart-free changes go through a server-script template or `HSLabsEvents.labTypes(...).modify(...)`.
- **JSON and JS templates mix:** a JS technology can `extend` a JSON template and the other way round; the dev UI lists both and marks JS ones read-only.
- **Errors point at the script line:** a bad template or param is reported with the KubeJS source line, and only that instance is disabled.

## 12. Agent-aided development workflow

Claude Code works one work package at a time against this doc and `CLAUDE.md`, proves each change with commands it can run headless, and stops at named decision gates so Hendrik decides. The agent works with him, not for him: it proposes options and waits.

### 12.1 Repository layout

```text
hstg-labs/
  CLAUDE.md                 # agent rules (appendix)
  docs/design.md            # export of this doc
  docs/api.md               # how-to per extension point (§9)
  docs/hs-api-facts.md      # verified HS signatures, path:line at tag 6.2.0-1.21.1
  docs/decisions/D01-*.md   # one ADR per decision gate
  docs/backlog.md           # ideas outside the current WP
  ref/History-Stages/       # read-only checkout of the tag, git-ignored
  src/main/java/io/github/itsgiantcookie/hstglabs/
    api/ api/client/        # public contract
    core/ hs/ tech/ research/ lab/ grid/ packet/ client/ devui/ command/
    compat/kubejs/ compat/jade/ demo/
  src/main/resources/kubejs.plugins.txt
  src/test/java/...         # JUnit: core + guard tests
  testpack/                 # KubeJS scripts, HS stage fixtures, technology + pack JSON
```

### 12.2 Guardrails

1. **HS boundary test.** Fails on any `net.bananemdnsa.historystages.` import outside `api..` plus the allowlist, and on any HS import outside `hs` and `command`.
2. **Own API boundary tests.** `compat`, `devui` and `demo` import nothing of ours outside `api`; no public signature in `api` names an internal type. Mirrors HS's `ApiSurfaceGuardTest` idea.
3. **Core purity test.** `core` may not import `net.minecraft`, `net.neoforged` or `net.bananemdnsa`.
4. **Mekanism boundary test.** Fails on any import from `mekanism` outside `mekanism.api..` unless it sits in `mek`, and on any copied Mekanism file without its MIT header. Facts go to `docs/mek-api-facts.md`, as for HS.
5. **Facts before code.** Before using an HS method, the agent finds it in `ref/` and records `path:line` in `docs/hs-api-facts.md`. No API from memory.
6. **No copied HS code.** Read it for patterns, write our own (licence, §7).
7. **Decision gates.** At each open decision (§15) the agent writes 2–3 options with trade-offs into an ADR stub and stops. Hendrik picks; the agent records the choice.
8. **API changes are deliberate.** Any change under `api/` is listed in the PR description and the changelog, and the demo addon is updated in the same PR.
9. **Honest verification.** Each WP ends with the actual results of `./gradlew build`, `test` and `runGameTestServer`. Anything visual is reported as "unverified, needs a client check" with a checklist.
10. **Scope and git.** Only the current WP; side ideas go to `docs/backlog.md`. One WP = one branch and one PR; the agent never pushes to `main`.

### 12.3 Session loop

1. Read `CLAUDE.md`, this doc's relevant sections, and the WP card.
2. Check the gate: if a needed decision is open, stop and ask.
3. Verify every HS signature the WP needs in `ref/`.
4. Write the tests first for `core` logic; write the GameTest skeleton for integration work.
5. Implement in the smallest steps that keep the build green.
6. Run the WP's verify commands.
7. Report: what changed, command results, client checklist if any, open questions.

### 12.4 Work package card

```markdown
WP-xx <title>
Goal:        one sentence
Depends on:  WP-..
Gate:        decisions that must be settled first
Touches:     packages / files
Do:          numbered steps
Acceptance:  testable bullets
Verify:      exact commands
Out of scope:
```

### 12.5 Where things run

- Build, unit tests and GameTests run headless: in Claude Code's sandbox, in a Linux container and in GitHub Actions (Ubuntu, Temurin 21).
- `runGameTestServer` sets `neoforge.enabledGameTestNamespaces=hstg_labs`, as HS does for its own namespace.
- `runClient` runs on Hendrik's Windows machine with the GPU. The agent never claims a screen works without that check.

## 13. Work packages and milestones

Twenty-three work packages (WP-05a and WP-07a keep the old numbers stable) reach a releasable 0.1.0 in five milestones; each WP is sized for one or two agent sessions and ends in a PR. The API skeleton comes in M1, before any feature, so every later feature is built on it. Two playtests by Hendrik gate the milestones after them.

Overview on one page: [HSTG Research Labs — Overview whiteboard](https://claude.ai/artifact/AHeznsDf9SKThiST5T4Hjh) (architecture, lab tier ladder, roadmap).

&#91;embedded content: roadmap · 6 milestones, 4 gates\]

Decision gates inside a milestone block only the WP that needs them; the playtest and release gates block the whole next milestone.

| WP | Deliverable | Acceptance (verified by command unless marked) | Gate |
| --- | --- | --- | --- |
| M0 WP-01 | Gradle skeleton: MDG, NeoForge 21.1.249, HS + Lootr + KubeJS deps, `neoforge.mods.toml` | `./gradlew build` green; `runServer` reaches "Done" with HS loaded; log line `HS API 6.2` | D1 |
| WP-02 | `ref/` checkouts (HS tag, KubeJS 2101), `docs/hs-api-facts.md` for every §7 row, HS boundary + core purity tests | Guard tests pass clean and fail on a planted bad import | — |
| WP-03 | GitHub Actions: build, test, GameTests | Green pipeline on the WP-03 PR | — |
| M1 WP-04 | `core`: `TechCost`, `ResearchProgress`, `ResearchQueue` | Queue respects max and duplicates; ≥ 90 % branch coverage on `core` | — |
| WP-05 | `core`: `LabCycle` (claim, consume, finish, cancel, refund), `Arbiter` (round-robin per ingredient across consumers), `SiteScheduler` (strict queue head), lab requirement and effective-tier evaluation over a plain site model | Randomised sequences never exceed U, never lose an uncredited pack, never starve a consumer; a gated head idles every site that cannot run it, with the right idle reason, and is never skipped; `all` / `any` / count requirements table-tested | D18, D19, D20 |
| WP-05a | Mekanism spike: the same throwaway lab block built twice, once on TileEntityMekanism (upgrade slots, side config, Mekanism GUI) and once on our own base with Mekanism API types only; chemical and energy capabilities fetched by name | Both variants build and pass one GameTest (packs and a chemical in, energy drawn); ADR for D22 listing every internal class each variant touches; spike code deleted afterwards | D21 |
| WP-06 | `api` skeleton: version constants, the four role capabilities, `ResearchSite` view, all §9.2 registries and events, demo stub, API guard tests | Demo registers one of each extension point, a lab requirement type included; registering after dispatch throws; guard tests green | D14 |
| M2 WP-07 | `tech`: definition codec with `labs.requires`, file repository, ingredient types item / tag / pack / chemical, built-in requirement types, reward `unlock_stage`, pack item + pack files, validation | Codec round-trip and unknown-data survival tests; a broken file disables only its technology with a clear error; an unknown lab tag is reported; a ladder whose tier-2 labs are only unlockable by tier-2 research is flagged as a softlock | D3, D4, D10, D11 |
| WP-07a | Template engine: kinds registry, JSON templates with extends, typed params, ${param} substitution, the = expression language (core parser, fuzz-tested), built-in curve hstg\_labs:tiered, instance resolution (template → params → overrides) with origin tracking, built-in data templates | Unit tests: resolution order, extends cycles refused, missing param error, unknown param warning; GameTest: editing a template file and /reload changes the cost of every linked technology | D24, D27 |
| WP-08 | `hs` bridge + `ResearchManager` + `ResearchSavedData` | GameTests: units done → stage unlocked with cause `ADDON`; vetoed unlock keeps progress, marks `BLOCKED`; relock follows `on_relock`; progress survives save and reload; two FTB teams research independently; a joining member adopts the team's progress; an offline member gets the stage on login | D2, D5, D7, D31 |
| WP-09 | Lab type loader + config overrides, our own base block classes on Mekanism API types with screens on Mekanism's GUI classes (D22 hybrid), Joule energy and upgrade slots, block entity types that collect every role block, basic lab | GameTests: hopper feeds packs; one speed-1 lab finishes U units in U × T ticks; breaking mid-unit credits nothing; `/reload` changes lab speed; a basic lab skips a gated technology with `LAB_REQUIREMENTS` | D6, D17, D22, D23 |
| WP-10 | Packets + `LabScreen` | Codec tests; GameTest: `QueueEdit` refuses a locked technology and a player without permission; client checklist (manual) | D9 |
| WP-11 | `/hslabs` commands incl. `/hslabs tech …` and `/hslabs lab …` (headless parity), HS report section | GameTests run every command through the dispatcher | — |
| M3 WP-12 | `DevEditService` + `TechEditor` facade, `DevEdit` / `DevSnapshot`, lab type overrides | GameTests: edit writes the file atomically and reloads; stale revision refused; non-op refused; a lab override changes speed live | D15 |
| WP-13 | Dev UI: technologies list, technology editor with lab requirement picker, packs, labs, templates, validation panels | Client checklist (manual): stage → working technology in under a minute | — |
| WP-14 | Dev UI: tree, balance, test tools | Unit tests for bill-of-materials maths; client checklist | — |
| WP-15 | `grid`: `GridManager`, Research Core, Pack Consumer, Research Server, cable, capacity, faults, site view | GameTests: 1 core + 2 consumers + 2 servers at parallel 2 run 4 units at once; one set gathered from two consumers; two cores → `CONTROLLER_CONFLICT`; members over capacity ignored; a chemistry + simulation requirement is met only while both are active and drops when a cable breaks | D12, D13, D19, D20 |
| WP-16 | Core screen, `GridStatus`, accelerator node as the first modifier | GameTest: accelerator raises throughput by its configured factor; client checklist | — |
| M4 WP-17 | KubeJS plugin: bindings, events, tech source | GameTest with script fixtures sees `researchCompleted` and a script-defined technology; a throwing script is logged, labs keep ticking | — |
| WP-18 | KubeJS content and templates: the HSLabsStartupEvents.content event on top of pack and lab builder types, JS technology templates, generated lab type data, stencil-tinted models and lang, /hslabs assets export, `HSLabsEvents.labTypes` | GameTest with startup-script fixtures: a KubeJS Chemistry Lab and Simulation Server join a network, meet a gated technology's requirements and unlock it; a server-script stat change applies on `/reload` | — |
| WP-19 | Jade provider, `StageAnnounceEvent` handling | GameTest for `announce_style`; Jade tooltip (manual) | — |
| WP-20 | `testpack/`: packs, 6–8 technologies incl. one gated behind two KubeJS lab types, every pack, lab and technology stamped from templates, and a full tier 1 → 3 ladder from basic lab to specialised network, HS stages made in the HS editor; `prepareTestpack` task | Fresh `runServer` loads with zero validation warnings | — |
| WP-21 | `docs/api.md`, `en_us` + `de_de`, HS config section, 0.1.0 release | Lang parity test; javadoc builds without warnings; release checklist signed off | Release |

**M5, after 0.1.0** (each gets a WP card then): a multiblock Research Facility on our own data-driven patterns (D16); owners for team providers other than FTB Teams; a player-facing tech tree screen; auto-queue of prerequisites (D8); wireless grid links.

## 14. Testing and verification

Three automated layers cover everything except how screens look; the agent runs all three before every PR, and Hendrik runs the client checklist at milestone gates.

| Layer | Covers | Tool | Command | Runs in |
| --- | --- | --- | --- | --- |
| Unit | `core` logic, parsers, codecs | JUnit 5, no Minecraft | `./gradlew test` | Seconds, anywhere |
| Guard | HS boundary, own API boundary, core purity, lang parity | JUnit source scans | `./gradlew test` | Seconds, anywhere |
| Integration | HS bridge, unlock paths, single labs, networks, dev-UI edits, commands, KubeJS hooks, demo addon | NeoForge GameTests, namespace `hstg_labs`, HS + Lootr + KubeJS loaded | `./gradlew runGameTestServer` | Headless, minutes, CI |
| Client | Screens, tooltips, toasts, GUI scale, `de_de` | Manual checklist | `./gradlew runClient` | Hendrik's Windows PC |

**GameTest rules**

- Fixtures are HS stage files exported from the editor, copied into the GameTest run's `config/historystages/global/` by a Gradle task before the server starts.
- Setup may use admin paths (`forceUnlockGlobal`, `relockGlobal`) to place the world in a known state; the code under test may not.
- Every test resets `ResearchSavedData` and relocks its stages in teardown, so tests do not leak into each other.
- HS's own GameTest helpers are internal classes; we write our own.
- Time-based tests use small numbers (U = 3, T = 20) and `helper.runAfterDelay` / `succeedWhen`, never real sleeps.

**Client checklist (per milestone)**

- [ ] Lab screen opens; slots accept only ingredient items; shift-click works
- [ ] Progress bar moves and the ETA is plausible
- [ ] Queue add, remove and reorder; a locked technology cannot be added
- [ ] Unlock toast or "Research completed" line appears once
- [ ] Dev UI: an HS stage becomes a working technology in under a minute, without a restart
- [ ] Dev UI: a new data-driven pack shows up with its colour and can be used at once
- [ ] Core screen shows members by role, worker slots, capacity, faults and missing lab requirements correctly; a KubeJS Chemistry Lab + Simulation Server pair unlocks a gated technology
- [ ] GUI scale 2, 3, 4 and auto; English and German text fits
- [ ] Two players see the same queue and progress on a dedicated server

## 15. Decisions and risks

All 31 decisions were settled with Hendrik on 2026-11-24; the Decision column holds the chosen option. Seven differ from the first recommendation and reshaped the design: D2 (teams from 0.1.0), D16 (own multiblock patterns), D18 (strict queue head), D24 (expression language in JSON templates), D25 (own KubeJS content event), D26 (asset export) and D30 (own upgrade items only). New questions get a new D number and the same treatment: options written down, Hendrik decides.

### 15.1 Decision gates

| ID | Question | Options | Decision | Needed by | Status |
| --- | --- | --- | --- | --- | --- |
| D1 | Name, mod id, package, licence | `hstg_labs` / "HSTG: Research Labs" (matches the existing HSTG: Permissions addon) vs own brand; licence MIT vs ARR | `hstg_labs`, `io.github.itsgiantcookie.hstglabs`, MIT; ask the HS authors whether the HSTG prefix is welcome | WP-01 | Decided |
| D2 | Research owner | Server only vs team (HS `Teams`) vs player | Chosen: HS teams from 0.1.0, keyed by FTB team id with a team-of-one fallback (D31); server owner stays selectable | WP-08 | Decided |
| D3 | Source of truth for cost | Own JSON files written by the dev UI · HS settings group (no API save path) · datapack only | Own files; addon and KubeJS sources merged after them | WP-07 | Decided |
| D4 | Where definition files live | `config/hstg_labs/` (like HS) vs datapacks `data/<ns>/hstg_labs/` | Config folder, because the dev UI writes there live; datapacks as a read-only source later | WP-07 | Decided |
| D5 | Progress on relock | Reset vs keep | Reset | WP-08 | Decided |
| D6 | Lab power | None vs optional energy per lab type vs required energy | Optional per lab type, in Joules (D23), default 0 | WP-09 | Decided |
| D7 | Cost changed on reload | Keep done units vs keep fraction vs reset | Keep done units; finish if done ≥ new U | WP-08 | Decided |
| D8 | Queue missing prerequisites | Refuse vs auto-add (OR groups ambiguous) | Refuse until M5 | M5 | Decided |
| D9 | Tree view in 0.1.0 | List in lab screen + dev-UI tree + HS graph vs a player tree screen | List + dev-UI tree + HS graph | WP-10 | Decided |
| D10 | Stage mode for technologies | Require `external` (warn otherwise) vs allow any mode | Warn on anything but `external` | WP-07 | Decided |
| D11 | Science packs | Tagged items only vs data-driven pack item only vs both (+ Mekanism chemicals) | Both, plus chemicals | WP-07 | Decided |
| D12 | Network connections | Cables + adjacency vs adjacency only vs wireless links | Cables + adjacency; wireless in M5 | WP-15 | Decided |
| D13 | Basic labs and networks | Basic labs stay standalone vs can join a network as a consumer + worker | Standalone; a network always has its own core | WP-15 | Decided |
| D14 | API publishing | Modrinth Maven with sources + javadoc vs own Maven with a separate `-api` artifact | Modrinth Maven first; own Maven once a second addon exists | WP-06 | Decided |
| D15 | Dev UI access | Op level 2 vs op level 4 vs config flag only | Op level 2 plus `dev_ui_enabled`; shipped packs set it false | WP-12 | Decided |
| D16 | Multiblock base | Mekanism's multiblock framework vs own data-driven patterns vs only other mods' machines via the role capabilities | Chosen: own data-driven patterns; Mekanism blocks can be pattern parts like any block | M5 | Decided |
| D17 | How lab type stats load | Reload listener over `data/` + config overrides (works with `/reload`) vs a NeoForge datapack registry (world load only, but KubeJS `ServerEvents.registry` for free) | Reload listener + overrides; KubeJS through builder-generated data and `HSLabsEvents.labTypes` | WP-09 | Decided |
| D18 | Which technology a site works on | Strict queue head for all sites (Factorio) vs first technology each site can run | Chosen: strict queue head, as in Factorio; a gated head idles the labs that cannot run it and the queue warns | WP-05 | Decided |
| D19 | What lab requirements count | Members present on the network vs active members (loaded, powered, inside capacity) | Active members | WP-15 | Decided |
| D20 | Where one ingredient set comes from | One consumer must hold the whole set vs gathered across consumers | Across consumers, simulate then commit | WP-15 | Decided |
| D21 | Mekanism as a dependency | Required vs optional compat module | Required, as Hendrik asked; lab types, energy and chemicals assume it | WP-01 | Decided |
| D22 | How deep we build on Mekanism | (a) API jar only, own tile and GUI code · (b) internals (`TileEntityMekanism`, components, GUI, multiblock lib), all inside `mek/` · (c) vendor the needed internals as MIT copies | Chosen after the WP-05a spike: hybrid. Own block entities on Mekanism API types; screens on Mekanism's client GUI classes (`GuiMekanism`, gauges, bars) in `mek/client` only; (c) as the fallback for a GUI class that breaks in a 10.7.x update (docs/decisions/D22-mekanism-depth.md) | WP-05a | Decided |
| D23 | Energy unit | Joules like Mekanism, FE accepted vs FE only | Joules | WP-09 | Decided |
| D24 | Logic in JSON templates | `${param}` substitution + built-in Java curves · a small expression language · no JSON templates, KubeJS only | Chosen: a small sandboxed expression language in JSON templates (values starting with `=`) | WP-07a | Decided |
| D25 | How KubeJS templates attach | `.template(id, params)` on our builder types · our own content event that registers objects itself | Chosen: own content event `HSLabsStartupEvents.content`, built on our public builder types | WP-18 | Decided |
| D26 | Assets for template content | Generated (base models + stencil-recoloured textures + lang) with overrides · packmaker supplies all assets | Chosen: generated by default, any part overridable, plus `/hslabs assets export` to editable files | WP-18 | Decided |
| D27 | Instances and their template | Stay linked (template changes rebalance every instance) · copied once at creation | Linked, with origin badges and a detach action in the dev UI | WP-07a | Decided |
| D28 | How lab tiers are represented | Integers + data names · Mekanism BaseTier enum · ordered id registry · bare integers | Integer levels with names, colours and icons from lab\_tier data; defaults reuse Mekanism's Basic/Advanced/Elite/Ultimate | WP-09 | Decided |
| D29 | Effective tier of a network worker slot | min(worker, core) · core tier only · worker tier only · lowest member | min(worker, core): the core caps the network, each server still needs its own tier | WP-05 | Decided |
| D30 | Upgrading labs in place | Own items + Mekanism installers · own items only · Mekanism installers only · rebuild | Chosen: own tier upgrade items only; contents and claimed units survive | WP-15 | Decided |
| D31 | How team progress and queues are keyed (HS's team API has no team id) | FTB team id with solo fallback · per player, mirrored to teammates · member-set key with merge rules · ask HS for a team id | FTB Teams team UUID via its API (soft dependency); otherwise a team of one | WP-08 | Decided |

### 15.2 Risks

| Risk | Effect | Mitigation |
| --- | --- | --- |
| HS raises `API_VERSION` within 6.x | Addon breaks on a 6.x update despite the range | CI against newest 6.x; startup log; narrow range on a bump |
| Our own API grows too big too early | Every early mistake becomes a compatibility promise | Mark young extension points `@ApiStatus.Experimental` until 1.0; the demo addon must use each one |
| `external` stages still generate a research scroll | Scrolls could appear in loot or JEI and confuse players | Check HS's hidden-scroll handling in WP-07; document or hide |
| Each global unlock triggers an HS lock-change reload | Lag spike when many technologies finish together | Batch unlocks per tick; measure in WP-08 |
| Large networks | Flood fill and per-tick arbitration cost TPS | Node cap, lazy rebuild from a dirty flag, core-driven ticking; GameTest with 500 nodes |
| Dev UI writes files on a live server | Lost edits or half-written files | Op-only, revision check per definition, atomic writes, config flag to disable |
| KubeJS 2101 plugin API drifts | Compat package fails to load | `compileOnly` pin, plugin isolated, GameTest in CI |
| Agent uses an HS method that does not exist or behaves differently | Wasted sessions, subtle bugs | Facts file with `path:line`, boundary guard, GameTests on every unlock path |
| Workers in unloaded chunks hold reserved units | Research looks stuck near the end | Reservations listed in `/hslabs debug`, admin release |
| HS licence (All Rights Reserved) | Copied code or bundling would breach it | No copying, Modrinth Maven dependency only, rule in `CLAUDE.md` |
| KubeJS or modded lab blocks are not covered by our block entity types | Their blocks place but never work | Build block entity types in the BLOCK\_ENTITY\_TYPE register event from every role block; GameTest with a KubeJS lab in WP-18 |
| Lab requirements make research look stuck | Players think labs are broken | Queue warning when no site can run the head (D18); LAB\_TIER and LAB\_REQUIREMENTS idle reason naming the missing lab; met and missing icons in queue and tree |
| Mekanism internals change in a 10.7.x patch release | Build or runtime break after a Mekanism update | Narrow range, CI against the newest 10.7.x, internals limited to GUI classes in mek/client, vendor the broken class as fallback (D22) |
| Two required tech mods | Packs without Mekanism cannot use this mod | Accepted by D21; core stays Mekanism-free and api uses only Mekanism API types, so an optional split stays possible later |
| Templates hide where a value comes from | Packmakers change a template and rebalance more than they meant to | Origin badges and an instance list in the dev UI; /hslabs template diff shows which technologies a template change touches before /reload |
| A pack's tier ladder softlocks | Players can never build the next lab tier | Ladder checks on every reload and dev-UI edit (§8.3); the testpack ships a complete ladder as the worked example |
| The JSON expression language grows into a programming language | Hard-to-debug templates, security and performance surprises | Fixed function list, no loops or side effects, length limit, fuzz tests; anything more belongs in KubeJS (D24) |
| Our content event cannot hand builders to KubeJS cleanly | Content declared in HSLabsStartupEvents.content never registers | WP-18 verifies the hook first; fallback replays declarations through StartupEvents.registry (D25) |

## Appendix: `CLAUDE.md` draft

Drop this into the repository root at WP-01 and keep it short; the design doc carries the detail.

```markdown
# HSTG Research Labs — agent rules

Factorio-style research addon for History Stages (HS). NeoForge 1.21.1, Java 21,
HS 6.2.0–6.x, Mekanism 10.7.19+ (required), KubeJS 2101.x (optional). Design: docs/design.md.

## How we work
- With Hendrik, not for him. At any open decision (docs/design.md §15) write
  2–3 options with trade-offs into docs/decisions/Dxx-*.md and STOP. Do not pick.
- One work package per branch/PR. Never push to main. Ideas outside the WP go to
  docs/backlog.md.
- Report at the end of every session: what changed, real command output
  (build/test/gametest), client checklist items that still need Hendrik, open questions.

## Our public API (api/, api/client/)
- Features are built ON the api: compat/, devui/ and demo/ import nothing of ours
  outside api/. If they need more, grow the api deliberately.
- No public api signature may name an internal type.
- Anything another mod could add is an id-keyed registry, capability or event,
  never an enum or instanceof check.
- Every api change: listed in the PR description and changelog, demo/ updated
  in the same PR, new extension points marked @ApiStatus.Experimental.
- Unknown data (addons blocks, unknown types) must survive load + save untouched.

## History Stages
- Reference source: ref/History-Stages at tag 6.2.0-1.21.1 (read-only).
- Before using any HS method, find it there and record path:line in
  docs/hs-api-facts.md. Never use an HS API from memory.
- Import only net.bananemdnsa.historystages.api.** plus data.StageEntry,
  data.DependencyGroup, data.StageMode. Only hs/ and command/ may import HS.
- HS is All Rights Reserved: never copy its code, never bundle it.
- StageStates.unlockGlobal does NOT check dependency groups. Gate on
  StageGraph.state == REACHABLE (+ checkRequirements) before queuing.
- HS has no api to save stage files: never write HS config files ourselves.
- Never call forceUnlock*/relock* outside command/ and GameTest setup.
- Server-thread only. Register only inside the matching Register...Event listener.

## Mekanism
- Reference source: ref/Mekanism at tag v1.21.1-10.7.19.85. Record every signature
  you use in docs/mek-api-facts.md (path:line).
- D22 = hybrid: outside mekanism.api.** only Mekanism's client GUI classes
  (mekanism.client.**), and only in mek/client. No tile base, components,
  registration or attachments. Our api/ may name Mekanism API types, never internal ones.
- Get chemical/energy capabilities by name (mekanism:chemical_handler,
  mekanism:strict_energy_handler), not from mekanism.common.
- MIT: copy code only where decision D22 says vendor; keep the MIT header and list
  the file in THIRD_PARTY_NOTICES.

## Code
- core/ is pure Java (no net.minecraft, net.neoforged, net.bananemdnsa, mekanism). Logic goes
  there first, with JUnit tests written before the code.
- Every dev-UI action is also a TechEditor op and a /hslabs subcommand.
- The client renders server snapshots; it never decides.
- Ids use the hstg_labs: namespace.

## Verify (all must pass before a PR)
./gradlew build
./gradlew test
./gradlew runGameTestServer
Screens are "unverified" until Hendrik runs ./gradlew runClient.
```
