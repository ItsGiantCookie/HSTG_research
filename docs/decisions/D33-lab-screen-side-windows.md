# D33: How the lab screen gets its side-config and upgrade windows

Status: **Decided** (Hendrik, 2026-11-25) · Needed by: WP-10 follow-up (side config, upgrades window)

## Question
Mekanism machines have side tabs. Side configuration and Upgrades open a window; Energy shows a
tooltip. The basic lab screen (`mek/client/LabScreen`, on `GuiMekanism`) has the energy tab since
WP-10. The two window tabs are missing.

Mekanism's own tabs for them (`GuiSideConfigurationTab`, `GuiUpgradeWindowTab`) need its tile base,
which D22 rules out. We could subclass Mekanism's window classes instead, but `GuiWindow` and
`GuiWindowCreatorTab` take a `SelectedWindowData` / `WindowType`. Both are in
`mekanism.common.inventory.container` (docs/mek-api-facts.md, "Windows need common types"). D22
allows only `mekanism.api.**` plus `mekanism.client.**` in `mek/client`, and `MekanismBoundaryTest`
enforces that.

Either way, the server side is the same work and is not part of this decision:
- per-face config on `BasicLabBlockEntity` (`mek/config`);
- face-aware capabilities and auto-eject;
- a face-cycle packet (`CycleFacePayload` from git history);
- the upgrade window moving the two upgrade slots out of the main screen.

## Options
1. **Narrow D22 amendment: allow `SelectedWindowData` in `mek/client`.** We subclass `GuiWindow` and
   `GuiWindowCreatorTab` and pass `WindowType.UNSPECIFIED`. Nothing else from `mekanism.common`.
   - Pros:
     - Real Mekanism windows: dragging, close button, layering, focus, and slots hidden under the
       window, exactly like every Mekanism machine.
     - Least code, roughly 300 lines for both windows.
   - Cons:
     - One internal type crosses the boundary. A Mekanism rename breaks the build; D22's MIT-vendor
       fallback still applies.
     - `MekanismBoundaryTest` gets a one-type allowlist.
2. **Own windows, drawn with our `MekGui` port (the prototype's approach).** We restore the
   prototype's side-config and upgrade windows (about 600 lines in git history before WP-10 (4/n)) and
   adapt them to `LabScreen`. They are drawn in `drawForegroundText`, with their own input handling.
   - Pros:
     - No new Mekanism dependency; D22 stays as it is.
     - The look is already proven against Mekanism's textures.
   - Cons:
     - More code to own.
     - The windows don't drag, and they sit beside `GuiMekanism`'s own window system, so we handle
       layering and slot blocking ourselves (the prototype already did).
3. **No windows: tabs swap a panel inside the screen.** The side-config and upgrade tabs replace the
   right column (queue / technologies) with a side-config or upgrade panel built from the Mekanism
   elements we already use (buttons, inner screen, slots).
   - Pros:
     - No boundary change.
     - Least risk and the smallest amount of code.
   - Cons:
     - Not how Mekanism machines behave.
     - The queue is hidden while a panel is open.

## Decision
Option 1: allow `SelectedWindowData` (with its nested `WindowType`) in `mek/client`, and nothing else
from `mekanism.common`.

## Consequences
- `MekanismBoundaryTest` allows exactly `mekanism.common.inventory.container.SelectedWindowData` (and
  its nested types) in `mek/client`; elsewhere it is still a violation, as is every other common type.
- The windows pass `SelectedWindowData.UNSPECIFIED`: Mekanism stores no position for them and shows
  no pin button.
- `LabSideConfigWindow` (Items and Energy tabs, six faces, clear sides) is a normal draggable window.
- `LabUpgradeWindow` does not move. Its two slots are plain menu slots at fixed positions, active on
  the client only while it is open; `LabScreen` overrides `findSlot` / `isMouseOverSlot` so they take
  clicks under the window. Mekanism's movable virtual slots are in its common code, so we do not use
  them.
- Auto-eject is shown as unsupported: the lab has nothing to eject.
