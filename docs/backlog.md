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
| Research lab block, block entity, menu (`lab/ResearchLab*`) | `lab/` | WP-09 basic lab (`hstg_labs:basic_lab`) | Consumes science-pack sets per unit instead of samples; speed, slots, tier, energy from its lab type; ticks a `LabCycle` claimed from `ResearchManager` |
| Lab screen (`client/ResearchLabScreen`) | `client/` | WP-09 / WP-10 `LabScreen` | Keeps the Mekanism look; adds current technology, progress `done/U`, ETA, queue, idle reason (§4.4) |
| Mekanism GUI port (`mek/client/MekGui`) | `mek/client/` | WP-05a spike, WP-09, WP-16 core screen | Shared GUI pieces (9-slice, tabs, windows, bars, digital buttons). Port of Mekanism GuiUtils/GuiElement drawing, MIT header kept (§7.2 rule 4) |
| Side config with Items / Energy / Data tabs, Configurator, auto-eject (`mek/config/*`, `client/PortOverlayRenderer`) | `mek/` | Kept and grown (D22 hybrid: own side config); WP-09 for basic lab, WP-15 for consumers; add a chemical tab | Data tab goes away on the basic lab (D13: it never joins a network); kinds become id-keyed (§9.6) |
| Upgrade window + `mek/UpgradeComponent` (Speed, Energy, Mekanism formulas) | `mek/` | WP-09 | Effects move into the modifier registry (§5.8, §9.2) instead of being applied directly |
| Joule buffer, energy slot, Mekanism strict-energy capability by name | `lab/`, `ModCapabilities` | WP-09 (D6, D23) | `energy_per_tick` from the lab type; FE accepted too |
| Tiered data cables, `grid/DataNetwork`, `grid/DataNetworkManager` (join, merge, BFS split, unload handling) | `grid/` | WP-15 Research Cable + `GridManager` | Network becomes a membership graph of `NODE` capabilities, no data buffer; size cap `grid_max_nodes`; open question for WP-15: keep cable tiers or one cable |
| `grid/IDataHandler`, `hstg_labs:data_handler` capability | `grid/`, `ModCapabilities` | WP-15 (the api's `ResearchCapabilities` exist since WP-06) | Replaced by CONTROLLER / CONSUMER / WORKER / NODE when the grid is rebuilt |
| Data consumer block (dummy sink) | `grid/` | WP-15 | Deleted; Pack Consumer is a different role |
| `lab/ResearchRecipe` (sample + energy + time → data + vial), research sample / empty vial items | `lab/` | WP-07 / WP-20 | Replaced by technology cost and ingredient types; the items may stay as testpack content or go |
| Art pipeline (`art/scripts`, provenance, mekstyle linter) | `art/` | all asset work | Kept as is |

## Other

- Ask the History Stages authors whether the "HSTG" prefix is welcome (D1 follow-up).
- `run/eula.txt` is not created by the build; set `eula=true` yourself before `./gradlew runServer`.
- WP-07: `core.TechCost.MAX_AMOUNT` is 1,000,000 per ingredient per unit. §8.1/§8.3 say "amounts out of
  range" but give no range; confirm or lower it when the codec and validation are written.
- WP-08: §4.1 says a `BLOCKED` technology "moves the queue on". `ResearchQueue` supports both
  readings (`move(id, size)` to send it to the back, or `remove`); WP-08 must pick one. Under D18 a
  blocked head that stays at the head would idle every lab.
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
- WP-10: the built-in idle reasons point at `hstg_labs:textures/gui/idle/<reason>.png`, which do not
  exist yet. Draw them per the mekstyle skill when the lab screen shows idle reasons.
- WP-21: `docs/api.md` (a how-to per extension point) is not written yet; the javadoc and the demo
  addon are the reference until then.
