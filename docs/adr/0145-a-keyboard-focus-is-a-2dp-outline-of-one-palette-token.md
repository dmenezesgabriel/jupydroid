# ADR-0145 — A keyboard focus is a 2dp outline of one palette token

Status: accepted
Date: 2026-11-12

## Context

Tabbing across the shell changed one rounded plate behind the control and nothing else: 1.14:1 to 1.15:1
against what was there (`#191A1B` to `#242526` on a rail item in Dark, `#FAFAFD` to `#EBEBED` in Light), and
the platform's high-contrast text setting did not reach it. [WCAG 1.4.11](https://www.w3.org/TR/WCAG22/#non-text-contrast)
holds a focus indicator to 3:1.

Four questions had to be answered on the tablet and not from a reference, because the references disagree:
the mark, the colour, the width, and whether one hook could draw it for every control.

- **The mark.** VS Code, JupyterLab and IntelliJ draw an outline in one themeable colour; Termux draws nothing
  and winlator maps two button states to their normal drawable. A state layer cannot reach 3:1 without
  becoming a fill that hides the control.
- **The colour.** VS Code's `focusBorder` is `#3994BCB3` in 2026 Dark (70%) and `#0069CC` in 2026 Light
  (`vscode@3f3380c6 extensions/theme-defaults/themes/2026-dark.json:12`, `2026-light.json:12`). It answers for
  presentation here ([ADR-0040](0040-the-presentation-reference-is-vs-code-behaviour-stays-jupyterlab.md)).
- **The width.** VS Code draws 1px inside the control (`src/vs/workbench/browser/media/style.css:257-272`),
  JupyterLab 2px (`jupyterlab@7f0e6906 packages/theme-light-extension/style/variables.css:460`).
- **One hook.** material3 1.4.0 names `ripple()` inside `IconButton`, `Surface` (so `TextButton`),
  `DropdownMenuItem`, `Checkbox`, `Switch`, `RadioButton` and the navigation items, and none of them reads
  `LocalIndication`. `LocalRippleConfiguration` carries a colour and a state-layer alpha, which is the plate again.

## Decision

**The ring is a 2dp stroke inside the control's bounds, in `ChromePalette.focusBorder`, drawn only while the
platform is in keyboard input mode.**

- **Colour.** `focusBorder` is `#B33994BC` in Dark and `#FF0069CC` in Light, as VS Code resolves them. The
  high-contrast variants (`#F38518`, `#006BBD`) are not ported: no high-contrast palette is wanted on these
  numbers (`docs/research/parity-ledger.md`), and the ring is no fainter under the platform's setting.
- **Width: 2dp, not VS Code's 1px.** On the tablet (240dpi) 2dp is three full pixel rows. 1dp is one and a half
  pixels, so one row reaches the colour and the next is a blend: the left and bottom edges of a 1dp ring read as
  a faint line, and a control whose edge falls between pixels may never reach the colour on any row. The
  strongest row of both widths read the same (2.69:1 over the selected rail item's plate in Dark, 3.71:1 in
  Light), so what 1dp costs is the stroke's body, not its peak. VS Code's 1px is a CSS pixel on a desktop; a
  touch tablet is read from further away and at a coarser grid.
- **Dark keeps VS Code's 70% alpha.** Every Dark stop read between 3.02:1 and 3.31:1, over the bare rail,
  the page, the side bar, a card's fill, a menu popup and a dialog's surface. The thinnest is the kernel picker's
  row over its dialog, `#32728E` over `#202122` at 3.02:1, because a dialog's surface is lighter than the page. An
  opaque `#3994BC` would lift all of them and leave the palette no longer VS Code's; the margin meets the floor.
  `ChromePaletteTest` pins that `focusBorder` composited over the side bar and over the page clears 3:1 in both
  themes; the dialog and popup surfaces are read on the tablet, not pinned.
- **Where it is drawn.** There is no single hook, so there is one primitive in `:extension-api`, which an
  extension may not do without because it cannot import `:app`
  ([ADR-0104](0104-extensions-are-compile-time-modules-against-an-extension-api.md)): `Modifier.focusRing()`
  and `drawFocusRing`, with `LocalFocusRingColor` provided by the theme. A control that reads
  `LocalIndication` (a plain `clickable`, `selectable` or `toggleable`) is reached by `FocusRingIndication`
  through the same `drawFocusRing`. A kind with many sites takes a wrapper (`FocusRingIconButton`,
  `FocusRingTextButton`, `FocusRingDropdownMenuItem`) and an architecture guard that refuses the raw material3
  control; a kind with one or two sites takes the modifier directly.
- **Touch.** A finger tap can focus a control, and the platform keeps touch focus apart from a Tab's, so the
  ring follows `InputModeManager` and a tap draws none.

## What was measured

On the arm64 tablet (`SM-X216B`, 240dpi), two panes, by a hardware Tab through `adb`, each stop's frame
compared with the same strip in the frame before the first Tab (`scripts/read_text_contrast.py --region`).

| Where | Dark | Light |
|---|---|---|
| The ten stops from the rail's first item to the Workspace header | 3.17:1 (`#2F708C` over `#191A1B`) | 5.18:1 (`#0069CC` over `#FAFAFD`) |
| The tab strip, the Settings page's controls, a cell's tick | 3.29:1 (`#2D6E8A` over `#121314`) | 5.39:1 (`#0069CC` over `#FFFFFF`) |
| A Files `+` menu item, a More rail row | 3.31:1 (`#33738F` over `#191A1B`) | 5.18:1 |
| The kernel picker's rows, over the dialog | 3.02:1 | 4.77:1 |
| With `high_text_contrast_enabled` 1 | the same 3.17:1 | the same 5.18:1 |

Before, every one of these was 1.14:1 to 1.15:1.

A launcher card already drew a 1dp outline at 3.5:1 over its fill, so the ring replaces an outline and "against
the frame before" understates it: the card's top edge reads 1.27:1 against its own unfocused outline, 4.79:1
against the page outside it and 3.95:1 against the fill inside (Dark; 1.48, 5.39 and 5.18 in Light). A card is
judged by what the ring separates.

A finger tap on a rail item drew no ring pixel in the rail or panel, and a cold launch drew none anywhere.

## Consequences

**The ring sits on the control, not on its touch target.** A node's accessibility bounds are the 48dp target;
the ring is drawn on the visible control inside it. The Workspace header's target is 72px tall and its ring is
the 34px of the label, and a tab's target starts 12px above the 48px tab the ring outlines. A reader that
measures the ring at the dump's edge finds nothing there. It reads the rows the ring is drawn on.

**Over a selected rail item the ring is thinner than elsewhere.** The item's circular plate touches the ring's
top and bottom edges at one point, and there Dark reads 2.69:1 against `#313233` where the bare rail reads
3.17:1; Light stays above 3:1. The ring is a rectangle of the item's bounds and the plate is a circle inside it,
so no colour clears 3:1 over every neighbour the stroke has. This is the ring's geometry, the same as VS Code's
outline over whatever it frames, and it is not changed here.

**A dialog's focus cannot be read by an instrumented test.** A dialog's window takes no input focus under
`am instrument`, so a hardware Tab reaches the window beneath it; what a dialog's button draws is read on the
tablet, and `FocusRingGuardTest` pins that the dialogs go through the wrapper.

The thing a future reader will be tempted to do is thin the ring to VS Code's 1px, or drop Dark's alpha to make
the picker's 3.02:1 look safer. The first loses the stroke's body on a coarse grid, and the second moves the
palette off the reference it is pinned to for a margin that already meets the floor.
