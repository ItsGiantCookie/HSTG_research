# HSTG Research Labs — agent rules

Factorio-style research addon for History Stages (HS). NeoForge 1.21.1, Java 21,
HS 6.2.0–6.x, Mekanism 10.7.19+ (required), KubeJS 2101.x (optional). Design: docs/design.md.
Mod id `hstg_labs`, package `de.germanrangers.hstglabs`, MIT (D1).

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
- Reference source: ref/History-Stages at tag 6.2.0-1.21.1 (read-only, git-ignored;
  `git clone --depth 1 --branch 6.2.0-1.21.1 https://github.com/Flix100000/History-Stages.git ref/History-Stages`).
- Before using any HS method, find it there and record path:line in
  docs/hs-api-facts.md. Never use an HS API from memory.
- Import only net.bananemdnsa.historystages.api.** plus data.StageEntry,
  data.DependencyGroup, data.StageMode. Only hs/ and command/ may import HS.
- HS is All Rights Reserved: never copy its code or assets, never bundle it.
- StageStates.unlockGlobal does NOT check dependency groups. Gate on
  StageGraph.state == REACHABLE (+ checkRequirements) before queuing.
- HS has no api to save stage files: never write HS config files ourselves.
- Never call forceUnlock*/relock* outside command/ and GameTest setup.
- Server-thread only. Register only inside the matching Register...Event listener.

## Mekanism
- Reference source: ref/Mekanism at tag v1.21.1-10.7.19.85 (read-only, git-ignored;
  `git clone --depth 1 --branch v1.21.1-10.7.19.85 https://github.com/mekanism/Mekanism.git ref/Mekanism`).
  Record every signature you use in docs/mek-api-facts.md (path:line).
- Imports outside mekanism.api.** only in mek/. Our api/ may name Mekanism API
  types, never internal ones.
- Get chemical/energy capabilities by name (mekanism:chemical_handler,
  mekanism:strict_energy_handler), not from mekanism.common.
- MIT: copy code only where decision D22 says vendor; keep the MIT header and list
  the file in THIRD_PARTY_NOTICES.md.

## KubeJS
- Reference source: ref/KubeJS, branch 2101 (no release tags upstream), checked out at
  ba1f722 (2026-11-15, mod_version 2101.7.2); we build against 2101.7.2-build.374.
  `git clone --depth 1 --branch 2101 https://github.com/KubeJS-Mods/KubeJS.git ref/KubeJS`
- compat/kubejs imports nothing of ours outside api/ (WP-17).

## Code
- core/ is pure Java (no net.minecraft, net.neoforged, net.bananemdnsa, mekanism). Logic goes
  there first, with JUnit tests written before the code.
- Every dev-UI action is also a TechEditor op and a /hslabs subcommand.
- The client renders server snapshots; it never decides.
- Ids use the hstg_labs: namespace.
- Prototype code from before M0 lives in lab/, grid/, mek/, client/, packet/; docs/backlog.md
  says which WP absorbs each piece. Do not grow it outside those WPs.

## Art & assets
- Textures, models, animated textures and machine GUIs follow the `mekstyle` skill
  (.claude/skills/mekstyle/). Machine GUIs use Mekanism's own textures (mekanism:gui/...).
- Blockbench is reachable through the `blockbench` MCP server when Blockbench desktop runs.
- Generated assets come from art/scripts/*.py; re-run them instead of hand-editing output.
- After touching src/main/resources/assets/, run:
  `python .claude/skills/mekstyle/scripts/lint_textures.py src/main/resources/assets/hstg_labs --provenance art/provenance.yaml`

## Verify (all must pass before a PR)
./gradlew build
./gradlew test
./gradlew runGameTestServer
Screens are "unverified" until Hendrik runs ./gradlew runClient.
