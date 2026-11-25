# Backlog

Ideas and carried-over work outside the current work package. Each entry names the WP that should
pick it up (docs/design.md §13).

## Prototype integration (pre-M0 code, renamed in WP-01)

Before M0 there was a working prototype (`hstg_research`): a Mekanism-style lab that turned
"research samples" into data, tiered data cables and a dummy data consumer. WP-01 moved it into the
§6.1 packages unchanged. Nothing in it is part of the design's API yet; each piece below is absorbed,
reshaped or deleted by the WP named.

| Prototype piece | Now at | Absorbed by | What changes |
| --- | --- | --- | --- |
| Research lab block, block entity, menu (`lab/ResearchLab*`) | removed in WP-10 | WP-09 basic lab (`hstg_labs:basic_lab`) | Done. Its block models and textures stay as the basic lab's shared casing |
| Lab screen (`client/ResearchLabScreen`) | removed in WP-10 | WP-10 `mek/client/LabScreen` on `GuiMekanism` | Done: energy, unit progress, current technology, `done/U`, ETA, queue, idle reason |
| Mekanism GUI port (`mek/client/MekGui`) | `mek/client/` | WP-05a spike, WP-09, WP-16 core screen | Shared GUI pieces (9-slice, tabs, windows, bars, digital buttons). Port of Mekanism GuiUtils/GuiElement drawing, MIT header kept (§7.2 rule 4) |
| Side config with Items / Energy / Data tabs, Configurator, auto-eject (`mek/config/*`; `client/PortOverlayRenderer` and `packet/CycleFacePayload` were removed with the prototype in WP-10, restore them from git history) | `mek/` | Kept and grown (D22 hybrid: own side config); WP-09 for basic lab, WP-15 for consumers; add a chemical tab | Data tab goes away on the basic lab (D13: it never joins a network); kinds become id-keyed (§9.6) |
| Upgrade window + `mek/UpgradeComponent` (Speed, Energy, Mekanism formulas) | `mek/` | WP-09 | Effects move into the modifier registry (§5.8, §9.2) instead of being applied directly |
| Joule buffer, energy slot, Mekanism strict-energy capability by name | `lab/`, `ModCapabilities` | WP-09 (D6, D23), energy slot WP-10 | Done |
| Tiered data cables, `grid/DataNetwork`, `grid/DataNetworkManager` (join, merge, BFS split, unload handling) | `grid/` | WP-15 Research Cable + `GridManager` | Network becomes a membership graph of `NODE` capabilities, no data buffer; size cap `grid_max_nodes`; open question for WP-15: keep cable tiers or one cable |
| `grid/IDataHandler`, `hstg_labs:data_handler` capability | `grid/`, `ModCapabilities` | WP-15 (the api's `ResearchCapabilities` exist since WP-06) | Replaced by CONTROLLER / CONSUMER / WORKER / NODE when the grid is rebuilt |
| Data consumer block (dummy sink) | `grid/` | WP-15 | Deleted; Pack Consumer is a different role |
| `lab/ResearchRecipe` (sample + energy + time → data + vial), research sample / empty vial items | removed in WP-10 | WP-07 | Replaced by technology cost and ingredient types |
| Art pipeline (`art/scripts`, provenance, mekstyle linter) | `art/` | all asset work | Kept as is |

## Other

- Ask the History Stages authors whether the "HSTG" prefix is welcome (D1 follow-up).
- `run/eula.txt` is not created by the build; set `eula=true` yourself before `./gradlew runServer`.
- WP-07: `core.TechCost.MAX_AMOUNT` is 1,000,000 per ingredient per unit. §8.1/§8.3 say "amounts out of
  range" but give no range; confirm or lower it when the codec and validation are written.
- ~~WP-08: blocked technology and the queue~~ decided as D32 (remove from the queue).
- WP-05 chose the order of idle reasons per slot (§5.7 lists them, it does not order them):
  `NO_RESEARCH`, `BLOCKED`, `LAB_TIER`, `LAB_REQUIREMENTS`, `NO_POWER`, `CAP_REACHED`,
  `MISSING_INGREDIENTS`, so the first reason shown is the one the player has to fix first. Change in
  `core.SiteScheduler` if playtests say otherwise.
- WP-07 validation: warn when two ingredient lines of one technology can match the same stock (an
  item and a tag holding it). `core.Arbiter` handles it safely (commit shortfall, roll back), but such
  a technology needs both lines in separate stacks to ever start.
- WP-08/09: `ResearchEvent.BeforeUnitClaim` (§9.4) is not in `core.SiteScheduler`. Fire it in the lab
  layer before calling the scheduler, or add a claim veto callback to the scheduler then.
- WP-09: `core.PackSource` counts amounts per ingredient key. Giving a tag-matched set back exactly
  (which item, which NBT) is the consumer adapter's job; it should log what it took per unit.
- WP-09/16: a unit's tick count is fixed when it starts (T / speed). A modifier or upgrade that changes
  speed mid-unit applies from the next unit on.
- WP-06 left out `RegisterStructurePatternsEvent` (M5) and the §9.5 facades (`ResearchApi`,
  `Technologies`, `GridApi`, `TechEditor`, `ClientResearch`). Each arrives with the WP that implements
  what it fronts (WP-08, WP-07, WP-15, WP-12, WP-10).
- WP-10 follow-up / WP-19: the built-in idle reasons point at `hstg_labs:textures/gui/idle/<reason>.png`,
  which do not exist yet. The lab screen shows idle reasons as text (their `IdleReasonType` lang key);
  draw the icons per the mekstyle skill when the screen or Jade shows them.
- WP-21: `docs/api.md` (a how-to per extension point) is not written yet; the javadoc and the demo
  addon are the reference until then.
- WP-07 left for later (docs/design.md §8.3), each needs data that does not exist yet:
  - **WP-09:** lab types are empty, so every reload warns `no_lab_types` until the loader exists. WP-09 also
    has to fill `LabTypeModel.lockedBy` from HS (`StageEntry.getItems()` / `getRecipes()` naming the lab's
    block item or recipe) so the softlock check runs on real data.
  - **WP-09:** the warning "an ingredient no loaded consumer type accepts".
  - **WP-09:** addon lab requirement types. They fail closed with an error until the scheduler gets an api
    `ResearchSite` view.
  - **WP-08:** the warnings "stage scope the owner model cannot serve" and "pedestal-deposited requirements".
  - **WP-08:** `unlock_stage` is registered but only logs until the HS bridge calls
    `BuiltInTypes.setStageUnlocker`.
  - **WP-11:** stage-related problems should also go into HS's debug report (`RegisterStageChecksEvent`).
  - **WP-13:** a pack's `model` field is parsed and kept but not used. All packs use the tinted flask.
  - **WP-13:** the creative tab only lists newly synced packs after the tab is rebuilt (rejoin or a
    change of operator status). A refresh on `PackSyncPayload` would fix that. (Not done in WP-10.)
- WP-07 decision to confirm: when a tech source supplies an id that a file already defines, the later one
  wins (files, then addon sources, then KubeJS) with an `overridden` warning. D3 gives the order but not
  whether later sources replace or only add.
- WP-07a left for later (docs/design.md §8.6):
  - **WP-09 / WP-14 / WP-18:** the template kinds `pack_item`, `chemical_pack`, `lab_block`, `lab_type`
    and `network_preset`, and the built-in templates of those kinds.
  - **WP-17/18:** KubeJS `modify` as the last resolution step, and JS templates.
  - **WP-12/13:** origin badges and "detach" in the dev UI. `TechRepository.Snapshot.origins()` already
    holds the data, by technology or pack id.
  - **WP-07a design addition to confirm:** templates declare typed params in a `declare` block. §8.6
    named typed params but showed no place to declare them.
- WP-08 left for later:
  - **WP-10, done:** queue editing by players (`QueueEditPayload`, `ResearchManager.enqueueBy`):
    `StageGraph.state == REACHABLE` plus `checkRequirements`, `queue_permission`, BLOCKED technologies
    listed in red so players re-add them (D32).
  - **WP-11:** `/hslabs` commands for admins: forget an owner (`ResearchManager.forgetOwner`), add
    progress, list pending and blocked, and release reserved units (§4.2).
  - **WP-07 validation, now possible:** warn when a technology's stage scope does not fit
    `owner_model` (an individual stage under SERVER, a global one under TEAM), and when an individual
    stage lacks `share_with_team` (§5.10).
  - **Client check (Hendrik, two accounts or a LAN friend):** with FTB Teams, create a party, research
    as one member and check that the other gets the stage. GameTests cover our team logic with
    `gametest/TestTeams`; a real FTB party needs logged-in players.
  - **GameTests:** mock players fire join events that KubeJS answers with a client packet, so tests
    avoid them.
- WP-09 left for later:
  - **WP-10, done:** the lab screen on Mekanism's GUI classes, the energy item slot, and removal of the
    prototype lab, recipe and sample items.
  - **WP-10, done (D33):** side configuration (Items / Energy) and upgrade windows on the basic lab.
  - **Still open:** the Mekanism Configurator in the world (cycle faces by clicking the block, port
    overlay) and the Configuration Card. Restore `PortOverlayRenderer` from git history (before WP-10
    (4/n)) and register `mekanism:configurable` / `config_card` for the basic lab. Also
    `UpgradeComponent`'s install timer: the basic lab applies its upgrade cards at once.
  - The upgrade window does not show whether the lab type supports an upgrade; `LabStatusPayload`
    would need the lab type's `upgrades`.
  - **WP-15:** the basic lab does not expose the api role capabilities (CONTROLLER, CONSUMER, WORKER)
    yet; networks need them.
  - **WP-16:** Mekanism upgrade cards apply inside the lab; §5.8 wants them as registered modifiers like
    any other.
  - **Not handled yet:** a lab type reload that changes `item_slots` reaches placed labs only when they
    load; until then they keep their slot count.
- D28 tier data is done (WP-10): `data/<ns>/hstg_labs/lab_tier/<level>.json`, synced in the tech
  snapshot, shown in the lab screen. WP-13's dev UI ladder view should show the same labels, and
  validation could warn about a `min_tier` or lab type tier that has no `lab_tier` file.
- WP-10 left for later:
  - The lab screen lists technologies as text rows (D9: a list, not a tree). The unlock preview
    (first 8 items) is in the snapshot but not drawn yet; a hover tooltip over the list needs a custom
    `GuiScrollList` subclass.
  - A player sees their own owner's queue in any lab. In a lab of another owner the screen says
    "Researches for another owner" and disables editing; it does not show that owner's queue.
  - `LabStatusPayload` goes out every 5 ticks per open screen even when nothing changed.
