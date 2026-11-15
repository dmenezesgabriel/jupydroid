# ADR-0146 — An icon beside text takes its size from the text's font size

Status: accepted
Date: 2026-11-13

## Context

At the system's 200% font size a label grows and the icon beside it does not. Read on the arm64 tablet
(`SM-X216B`, 240dpi, one pane), the icon in a More rail row was 1.20 of its label's line at 1.0 and 0.64 at 2.0,
and a tab's icon 0.88 and 0.44; the notebook's `Code` label went from 20dp to 37.3dp tall while its icon stayed
put. Nothing was lost (every control kept its name and a 48dp target), but the proportion the screen was drawn in
was gone. None of the 98 `Icon(` sites in `:app` derived its size from the font.

Android does this on purpose. Its documentation says font scaling "only affects text elements" and leaves
"containers, padding, and icons" at a fixed size, and names density scaling, which moves everything at once, as
the alternative ([Support user-scalable content](https://developer.android.com/develop/ui/compose/accessibility/scalable-content)).
The references split:

- **IntelliJ** (`intellij-community@406a27a8`) cannot arrive at the gap: the UI font size is the one input to
  `userScaleFactor`, which sizes icons and geometry alike (`LafManagerImpl.kt` `patchLafFonts`,
  `JBUIScale.kt`).
- **JupyterLab** (`jupyterlab@7f0e6906`) sizes the launcher's icons in `em` (`packages/launcher/style/base.css:13-15`).
  Its toolbar and tab icons were not read.
- **VS Code, vscode-jupyter, Termux and winlator** keep a fixed glyph. In VS Code no setting scales text alone:
  `window.zoomLevel` moves both.

Two of seven follow the text, so this is a deliberate departure from the platform's default, toward the one
reference that decided the question. It is not parity.

## Decision

**A glyph in the same row as a text is sized from that text's font size, through the density.**
`Density.glyphBesideText(size, labelSize)` in `:extension-api` (`GlyphScale.kt`) returns
`size * (labelSize.toDp() / labelSize.value.dp)`: the glyph grows by what its label's font grows by. The call
site keeps its `Modifier.size(...)`, so the change at a site is its argument.

- **Converted from the label's size, not the glyph's, and not by `fontScale`.** Android 14's conversion is a
  curve. Multiplying by `fontScale` overshoots it, and converting a 24dp glyph from its own 24sp fell short of a
  14sp label: 0.95 of the label's line at 2.0, because the curve grows 24sp by 1.5 and 14sp by 1.9. A glyph about
  as large as its label's font (a 14dp tab glyph) may leave `labelSize` at its default.
- **It lives in `:extension-api`**, as the focus ring does, because an extension cannot import `:app`
  ([ADR-0104](0104-extensions-are-compile-time-modules-against-an-extension-api.md)) and the Chat panel's glyphs
  need it.
- **Which glyphs.** Those in a row with text: a tab's, More's rail rows, the Running panel's row icon and section
  caret, the Explorer, Recents and `Twistie` rows, the debugger's disclosure, a diagnostic's detail glyph, the
  kernel status glyph, the breadcrumb's separator, the drop-down arrows, check and add glyphs inside a Material
  button, and in the AI extension the model chip's arrow, the Chat list's two button icons and a chat row's icon.
  Each was read at its site and not swept. Four classes keep their `dp`: an icon inside a 48dp `IconButton`
  or `FocusRingIconButton`, a rail item with no label, the launcher's tile and the editor's line numbers, because
  none has a label whose ratio to keep. A kernel glyph with no name beside it keeps 24dp. The model menu's check
  and the search field's icon keep theirs too: Material's menu item and text field set those slots.
- **Material's slots needed no replacing.** `WideNavigationRailItem` places its icon at the size the icon
  measured, and a Material button's content slot fixes no size and clips nothing (material3 1.4.0), so a larger
  glyph is passed in and the control holds it.
- **Targets stay at 48dp** ([ADR-0048](0048-touch-rules-measured-on-device.md)): a touch target is a minimum, not
  a size to scale with a glyph.
- **The tab row stays 32dp.** At 2.0 the label's line is 28dp and the grown glyph 26dp, so the row already holds
  both, and no scale the platform offers needs more. A minimum would be code nothing reaches, and Reload, Save and
  the Up arrow keep the strip's height ([ADR-0142](0142-the-tab-strips-controls-keep-the-strips-height.md)).
- **No cap.** The platform's largest font scale is 2.0, so a glyph is bounded by the label it follows.

**Deviation from IntelliJ: one factor for everything is not ported.** Moving spacing, padding and targets with
the font is the density strategy Android's documentation names as the other choice. It would reflow every screen
measured in the visual audit, and a phone's density already belongs to the platform. Only the glyph moves.

## What was measured

The tolerance on "keeps its ratio" is the measured spread of the glyph over its label's line across 1.0, 1.3,
1.5 and 2.0 on `dev36` (API 36), asserted in the instrumented test of each site: 0.10 for a tab, 0.06 for the
editor rows (0.05 for the diagnostic card) and 0.05 for the rest. Red before, green after:

| Site | Ratio at 1.0 | Before, at 2.0 | After, at 1.3 to 2.0 |
|---|---|---|---|
| A tab, 14dp | 0.875 | 0.500 | 0.929 to 0.957 |
| More rail row, 24dp | 1.200 | 0.632 | 1.184 to 1.188 |
| Running panel row icon and caret, 16dp | 0.800 | 0.421 | 0.778 to 0.789 |
| Explorer, Recents rows, 24dp | 1.000 | 0.57 | 0.97 to 1.00 |
| A chevron beside a label, 24dp | 1.200 | 0.63 | 1.18 to 1.19 |
| Kernel status glyph, 24dp | 1.200 | 0.63 | 1.18 to 1.19 |
| Breadcrumb separator | 0.800 | 0.42 | 0.78 to 0.79 |
| A Material button's drop-down arrow, 24dp | 1.200 | 0.63 | 1.184 to 1.188 |
| The AI chip's arrow, 24dp | 1.200 | 0.632 | 1.184 to 1.188 |

The tablet at one pane, System theme, night off, the whole shell at 1.0 against 2.0
(`scripts/theme-six-combinations-driver.sh`, then `scripts/read_font_scale_fit.py` at 240dpi): **0 newly
overlapping and 0 newly under 48dp on all 13 surfaces** (Settings, a file, a notebook, a terminal, the launcher,
Files, Sessions and Tabs, Search, More's rail, Source Control, Chat, the table of contents overlay, the Spaces
home). Read off the frames, since More's icon carries no description and is not a node of its own:

| Where | 1.0 | 2.0 | Reads as |
|---|---|---|---|
| More row `Source Control`, glyph ink width over label box height | 30px over 30px | 55px over 56px | the glyph grew ×1.83 against the label's ×1.87, 98% of its 1.0 ratio; fixed it would be 53% |
| Tab `notes.py`, glyph ink height | 17px | 33px | ×1.94 against the label ink's ×2.0 (16px to 32px); 1.06 and 1.03 of the label |
| Tab row, accessibility bounds | 48px (32dp) | 48px (32dp) | unchanged |

The same on `dev36` (320dp, density 160, 12 surfaces: the terminal is not drawn there), after a 180s idle on a
host with every `devjob` slot free: 11 surfaces read 0 and 0, and More's rail reads 0 newly overlapping and
**1 newly under 48dp, the `Close rail` scrim** (see Consequences). Read off the frames and the dump:

| Where | 1.0 | 2.0 | Reads as |
|---|---|---|---|
| More row `Source Control`, glyph ink width over label box height | 20px over 20dp | 37px over 38dp | ×1.85 against ×1.9, 97% of its 1.0 ratio |
| Tab, glyph ink | 12 x 9px | 24 x 18px | ×2.0, with the label |
| Tab row, accessibility bounds | 32dp | 32dp | unchanged; no glyph cut in the 2.0 frame, in a strip already at its floor |

## Consequences

**A container that held its glyph by a margin grows with it.** Where a row's height was set by the glyph and
not by the label, the larger glyph makes it taller at 2.0, as measured: the cell type button 54dp to 61dp, the
kernel button 40dp to 61dp, the AI chip to 61dp (a 45dp arrow), a search result's file header 51dp to 61dp, and
the Chat list's buttons 48.0dp to 53.3dp on the tablet. Rows set by their label did not grow: a Running row
(64dp), the Explorer row, the Recent heading (48dp), the breadcrumb row (48dp) and a chat row (56dp). More's rows
grow 56.0dp to 76.7dp, where the label alone made them 69.3dp.

**On a 320dp phone at 2.0, More's tap-outside scrim is narrower than it was.** The rail is as wide as its
widest row, and a larger glyph widens the row: the rail takes 303 of 320dp and `Close rail` is 17 x 640dp, where
it was 100dp at 1.0 and 38dp at 2.0 before this change. It was already under 48dp at 2.0, so the reader reports it
as newly so only against 1.0, and this change takes it from 38dp to 17dp. It is the same kind of thing the
parity ledger already read and called `present`: a scrim is not a drawn control, no label or icon is cut, and
Back closes the rail too. This decision does not fix it, and the item's demo did not meet its own "no target newly
under 48dp" on the phone because of it. The tablet, where the rail is a fraction of 800dp, reads none. What would
fix it is a cap on the modal rail's width, which at 2.0 on 320dp would make a label such as `Source Control` wrap
or truncate; that is a decision about the rail and not about a glyph, so it was not made here.

**A label still truncates at 2.0.** The notebook's tab reads "theme-ba…" in the 2.0 frame, as the visual audit
recorded before this change. Whether the larger glyph costs a name any of its width was not measured, because the
tab strip's labels were not read at both scales with the old glyph beside them; this decision does not touch
truncation.

**A new icon beside text owes the call.** An `Icon(` placed in a row with a label and sized in `dp` falls behind
it again at 2.0. Nothing yet refuses one; the instrumented tests pin the sites that exist.

The thing a future reader will be tempted to do is move to IntelliJ's one factor so the question cannot arise, or
write `size * fontScale`. The first reflows every screen for a problem that is only the glyph's, and the second
overshoots Android 14's curve at 2.0.
