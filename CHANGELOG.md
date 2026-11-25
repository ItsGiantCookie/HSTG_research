# Changelog

All notable changes to HSTG: Research Labs. API changes (`io.github.itsgiantcookie.hstglabs.api..`) are
always listed here (docs/design.md §9.1). Every API type is `@ApiStatus.Experimental` until 1.0.

## Unreleased

### WP-10: packets and the lab screen

- Network (protocol `2`):
  - `tech_snapshot` (S→C): every technology as the player sees it (state, cost, prerequisites,
    unlock preview), the owner's queue and progress, and the lab tiers.
  - `queue_edit` (C→S): add, remove, move. It checks `queue_permission` and History Stages
    reachability and requirements.
  - `lab_status` (S→C, while a lab screen is open): energy, tier, speed, and per worker slot the
    running unit or the idle reason.
- The basic lab has a screen (right-click):
  - energy bar, energy item slot (Mekanism energy items or FE items), speed and energy upgrade slots;
  - unit progress, the current technology with `done/U` and ETA;
  - the queue (move up, move down, remove) and the technologies the player can add. Blocked
    technologies show in red and can be re-added (D32).
- Side tabs as on Mekanism machines: side configuration (left, a window with Items and Energy tabs,
  six faces, clear sides), energy (left), upgrades (right, a window with the speed and energy slots,
  amount and effect). Item and energy capabilities follow the face config. Every face starts as
  input, so existing hopper and cable setups keep working; input faces no longer hand items out.
- Lab tiers are data (D28): `data/<ns>/hstg_labs/lab_tier/<level>.json` with `name`, `color` and
  `icon`. The defaults are Mekanism's Basic, Advanced, Elite and Ultimate.
- Removed: the prototype `hstg_labs:research_lab` block, the `hstg_labs:research` recipe type, and the
  `research_sample` and `empty_sample_vial` items. Worlds from dev builds lose those blocks and items.
- No API change.

### API: first skeleton, `HSLabsAPI.API_VERSION` 1, `API_MINOR` 0 (WP-06)

- `api.HSLabsAPI`: mod id and API version constants.
- `api.registry.RegisterIdEvent<T>`: base of every mod-bus `Register…Event`. Fired once during
  common setup (client setup for `api.client`), frozen after. A late `register` throws
  `IllegalStateException`.
- Extension points (docs/design.md §9.2), one registry each:
  - `ingredient`: `IngredientType<T, S>`, `IngredientStorage<S>` (`ITEM`, `FLUID`, `CHEMICAL`),
    `RegisterIngredientTypesEvent`
  - `reward`: `RewardType<T>`, `RewardContext`, `RegisterRewardTypesEvent`
  - `owner`: `OwnerId`, `OwnerType`, `RegisterOwnerTypesEvent`
  - `tech`: `TechSource`, `RegisterTechSourcesEvent`
  - `lab`: `LabRequirementType<T>`, `RegisterLabRequirementTypesEvent`
  - `lab`: `LabTypeSource`, `RegisterLabTypeSourcesEvent`
  - `lab`: `LabStatsProvider`, `LabStats`, `RegisterLabStatsProvidersEvent` (keyed by block entity
    type id)
  - `modifier`: `Modifier`, `ModifierTarget`, `ModifierScope`, `ModifierContext`,
    `RegisterModifiersEvent`
  - `lab`: `IdleReasonType`, `RegisterIdleReasonsEvent`. The seven built-in reasons are registered
    through it.
  - `template`: `TemplateKind<T>`, `RegisterTemplateKindsEvent`
  - `client`: `IngredientRenderer<T>`, `TechNodeDecorator`, `DevPanel`, `RequirementEditor<T>` and
    their `Register…Event`s
- Role capabilities (§9.3): `lab.ResearchCapabilities.CONTROLLER / CONSUMER / WORKER / NODE` with
  `ResearchController`, `IngredientConsumer`, `ResearchWorker`, `GridNode`.
- Site view: `lab.ResearchSite`, `SiteMember`, `LabRole`.
- Game-bus events (§9.4), declared but not fired yet:
  - `event.ResearchEvent.BeforeEnqueue / QueueChanged / Started / BeforeUnitClaim / UnitCompleted / Completed / Blocked`
  - `event.GridEvent.Formed / Changed / Fault`
  - `tech.ResolveCostEvent`
  - `tech.TechDefinitionsReloadedEvent`
- Not yet: `RegisterStructurePatternsEvent` (M5) and the §9.5 facades (`ResearchApi`, `Technologies`,
  `GridApi`, `TechEditor`, `ClientResearch`), which arrive with the features they front.

### Technologies and science packs (WP-07)

- **Technology files:** `config/hstg_labs/technologies/**.json` (docs/design.md §8.1).
  - Each file is parsed on its own, and every problem is reported with its JSON path.
  - Unknown types, `addons` and unknown fields survive a save untouched.
  - The file is written back with sorted keys.
- **Built-in types:**
  - ingredients `hstg_labs:item`, `tag`, `pack`, `chemical` (Mekanism chemical or chemical tag)
  - lab requirements `hstg_labs:lab`, `network`, `min_tier`, `all`, `any`
  - reward `hstg_labs:unlock_stage` (wired to History Stages in WP-08)
- **Validation (§8.3):** unknown stage, two technologies on one stage, unknown lab type or tag,
  stage not `external`, network without a controller, and the tier ladder checks (unreachable,
  locked starter, softlock, empty tier). Errors disable only their technology.
- **Science packs:** `config/hstg_labs/packs/*.json` and the item `hstg_labs:science_pack` with the
  `hstg_labs:pack` component. Its name, tier and tint come from the pack file, synced to clients.
- **When definitions load:** at server start, on `/reload` and when History Stages reloads its stages.
  `TechDefinitionsReloadedEvent` is fired afterwards.
- **Creative tab** `hstg_labs:main` with every item, one stack per loaded science pack.

### Templates (WP-07a)

- **Template files:** `config/hstg_labs/templates/**.json` with `kind`, `extends`, `declare` (typed
  params: `int`, `number`, `color`, `id`, `id_list`, `string`), `params` and `defaults`
  (docs/design.md §8.6).
- **Linked instances (D27):** a technology or pack file with `template`, `params` and `overrides`
  stays linked to its template. Changing the template rebalances every instance on the next
  `/reload`, the file keeps its link when written back, and every resolved value records where it
  came from (template, param, the file, or overrides).
- **Resolution order:** root template, each `extends` child, params, the instance's own fields, then
  overrides. Extends cycles, unknown templates and kind mismatches disable only that instance;
  unknown params are warnings.
- **Expressions (D24):** `=` expressions in templates (`+ - * / ^`, comparisons, `min`, `max`,
  `round`, `floor`, `ceil`, `if`), with a length and nesting limit, in `core/expr`. I wrote our own
  small parser (fuzz-tested) instead of adding EvalEx, which keeps dependencies unchanged.
- **Built-in templates:** `hstg_labs:tiered` (technology) and `hstg_labs:data_flask` (pack). Template
  kinds `hstg_labs:technology` and `hstg_labs:pack` are registered through `RegisterTemplateKindsEvent`.
- **Fix:** two `/reload`s finishing in the same tick no longer skip the second load.

### Research manager, saved progress, teams (WP-08)

- **History Stages:** the research manager is the only code that writes progress, and it reaches History
  Stages only through the `hs/` bridge.
  - **Unlocking:** a completed technology is unlocked through HS with cause `ADDON`. A global stage gets
    one unlock; an individual stage gets one per team member, offline members included (HS syncs at
    their next login).
  - **Refused unlock:** the technology is marked BLOCKED, leaves the queue and keeps its progress;
    re-adding it retries (D32).
  - **Unlocked elsewhere** (command, quest, script): its progress is discarded.
  - **Relock:** follows `on_relock` (D5; `KEEP` re-unlocks on re-queue).
  - **Lost for good:** it leaves the queue.
  - **Cost changed by a reload:** done units are kept (D7).
- **Saved data:** `data/hstg_labs_research.dat` (schema 1) holds queue, progress, blocked and pending per
  owner. Unreadable entries survive.
- **Server config:** `hstg_labs-server.toml` with `queue_max`, `queue_permission`, `on_relock`,
  `owner_model`.
- **Owners:** `hstg_labs:server` and `hstg_labs:team`.
  - With FTB Teams installed (optional, D31), the team is the player's FTB team.
  - Without it, each player is a team of one.
  - The keys are the same in both cases, so adding FTB Teams later keeps everyone's progress.
- **Events:** `ResearchEvent.Started`, `UnitCompleted`, `Completed`, `Blocked`, `BeforeEnqueue` and
  `QueueChanged` are now fired.

### Lab types and the basic lab (WP-09)

- **Lab types as data (D17):** `data/<ns>/hstg_labs/lab_type/<id>.json` with roles, tier, speed,
  parallel, `energy_per_tick` / `energy_capacity` (Joules), `item_slots`, `accepts`, `tags`,
  `upgrades` and controller capacity.
  - Lab type sources follow the data files.
  - `config/hstg_labs/lab_types/` overrides single fields, and `/reload` applies them.
  - Technology validation now checks against the real lab types, including which HS stages lock each
    lab block.
- **The basic lab** (`hstg_labs:basic_lab`, tier 1): one block that is controller, consumer and
  worker.
  - Works on its owner's queue head (D18) through the core scheduler.
  - Takes one pack set at the start of a unit and remembers the exact stacks.
  - Draws 20 J/t; accepts Mekanism Joules and FE at 2.5 J/FE.
  - Mekanism speed and energy upgrade cards use Mekanism's maths; registered modifiers apply.
  - Breaking it mid-unit credits nothing and drops every pack.
  - Running units are saved and resume after a restart.
  - One block entity type collects every block of the lab class, so other mods' and KubeJS lab blocks
    are included.
- **Demo:** the "+10 % speed" modifier now applies only to the demo's own technologies.

### Development

- Demo addon (`demo/`): registers one of every extension point using only `api`. Enabled with
  `-Dhstglabs.demo=true`, which the dev and GameTest runs set.
- Guard tests:
  - `api` names no internal type.
  - `compat`, `devui` and `demo` use only `api`.
  - Every `api` type has javadoc and is marked experimental.
  - D22: Mekanism internals only as GUI classes in `mek/client`.
- `core`: `TechCost`, `ResearchProgress`, `ResearchQueue`, `LabCycle`, `Arbiter`, `SiteScheduler`,
  lab requirements, `FreezableRegistry` (WP-04, WP-05, WP-06). Branch coverage on `core` is enforced
  at 90 % or more.
