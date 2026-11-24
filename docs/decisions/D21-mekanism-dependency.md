# D21 — Mekanism as a dependency

Status: **Decided** (Hendrik, 2026-11-24) · Needed by: WP-01

## Question
Is Mekanism a required dependency or an optional compat module?

## Options
1. Required: lab types, energy (Joules), upgrades and chemical ingredients assume it.
2. Optional compat module: labs work without it, Mekanism adds chemicals/energy/upgrades.

## Decision
Option 1, required, declared `[10.7.19,10.8)` with ordering `AFTER`.

## Consequences
- `build.gradle` uses the full Mekanism jar (`implementation`), because D22 allows internals
  inside `mek/`. A guard test keeps every import outside `mekanism.api..` in `mek/`.
- `core/` stays Mekanism-free and `api/` names only Mekanism API types, so an optional split
  stays possible later (risk table, docs/design.md §15.2).
