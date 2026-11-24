---
name: mekstyle
description: Mekanism-style art rules for this mod's block/item textures, Blockbench models, animated textures (.mcmeta), BER animations and machine GUIs. Use whenever creating, editing or reviewing any asset under assets/<modid>/ or driving Blockbench through its MCP server.
---

# mekstyle: Mekanism-style asset guide

The full rule set is `style-guide.yaml` in this folder. Each rule has an ID (`COL-10`, `ATX-03`, …), a level and a check.
- **MUST**: a violation is a defect.
- **SHOULD**: the default; deviate only with a stated reason.
- **MAY**: optional.

Read only the sections the task needs (e.g. `colors` + `textures` for a texture job). Don't load the whole file every time.

## Always

1. **Look up the rules.** Before making an asset, read the relevant sections of `style-guide.yaml`: `colors.steel_ramp`, `colors.tiers`, `colors.semantic`, plus the section for the asset type.
2. **Keep casings neutral.** Casings are neutral gray: at least 85% of pixels (COL-01). Use the steel ramp, which is measured from Mekanism. Color goes only into accents, ports and indicators. No #000000 or #FFFFFF outside tint bases (COL-30).
3. **Start from shared templates.** Machines reuse one shared casing: an imported Mekanism `base/` casing or your own template (TEX-20). Only the front face identifies the machine (TEX-30). Tier is shown by an accent only, at most 25% of the pixels (COL-10).
4. **Run the linter** after writing textures or models:
   ```
   python .claude/skills/mekstyle/scripts/lint_textures.py src/main/resources/assets/<modid> --provenance art/provenance.yaml
   ```
   Fix every MUST issue. Report SHOULD issues with their rule ID.
5. **Cite rule IDs.** When reporting or reviewing, name the rule IDs. Don't paraphrase rules without them.

## Reusing Mekanism textures (MIT)

Mekanism is MIT-licensed (Copyright (c) 2017-2025 Aidan C. Brady), and its assets can be reused. Always import through the script so the license notice and provenance are written (LEGAL-01, LEGAL-02):
```
python .claude/skills/mekstyle/scripts/import_mekanism.py --modid <modid> block/base block/crusher block/overlay [--strip-ctm]
```
- Use `SRC:DEST` to rename, e.g. `block/crusher:block/grinder`.
- The script pins the upstream commit in `art/provenance.yaml`.
- If you edit an imported file, set `modified: true` on its provenance entry.
- Imported files go into your own namespace only, never `assets/mekanism/` (LEGAL-03).
- Multiblock textures carry CTM data. Use `--strip-ctm` unless the mod depends on CTM (TEX-80).
- If the mod can run next to Mekanism, change the front face or tint so players can tell the blocks apart (LEGAL-05).
- Assets from other mods: check their own license first (LEGAL-06).

## Blockbench via MCP

The Blockbench MCP server is configured as `blockbench` in `.mcp.json`. It needs the Blockbench desktop app running with the MCP plugin loaded. The exact tool names depend on the plugin version, so list the `mcp__blockbench__*` tools first and map them to these steps:

1. **Check state.** Read the current project. If an unsaved project is open, ask before replacing it (BB-40).
2. **Set up the project** from `blockbench.project`: Java Block/Item format, 16×16 textures, per-face UV, 1 u grid.
3. **Build the casing first.** Then add detail elements within the budget for the model class (MOD-03). Stay on the grid (MOD-01) and use only the allowed rotation angles (MOD-04). Give every element a snake_case name (BB-01).
4. **Split out moving parts.** Every part a BER will move goes into its own model, built around its real pivot (BB-10, BB-11). That part must not also be in the base model (BB-12).
5. **Textures.** Start from imported Mekanism faces or paint with the steel ramp. Overlays and emissive details go in separate textures (BB-22, TEX-41).
6. **Look at it.** After the casing, the details and the textures, take a screenshot or preview if the MCP offers one and check it against principles P1–P4 (BB-42).
7. **Export** to the paths in `blockbench.export`. Save the `.bbmodel` source under `art/blockbench/` (BB-31). Then run the linter.

Don't use script-evaluation tools such as `risky_eval` when a dedicated tool exists. If one is truly needed, say what the script does and get approval first (BB-41).

## Choosing the technique

- **Something 2D blinks, scrolls or glows:** use an animated texture. Pick the preset from `animated_textures.presets`.
- **Geometry moves:** use a BER plus a separate part model. Pick the speed from `animations.timing.presets` and follow ANI-10, ANI-11 and ANI-20.
- **Tier or material variants:** use one grayscale template plus a tint or palette swap (COL-12, TEX-60). Don't paint each variant by hand.
