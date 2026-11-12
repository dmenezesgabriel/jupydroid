# JD-084 — An icon beside text follows the text's scale

Milestone: `M11` · State: open · Depends on: — · Closes: GAP-203

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A person who sets the system's font size to 200% sees a glyph keep its proportion to the label beside it;
today the glyph stays the same `dp` while the label grows, and falls from 1.20 of the label's line to 0.64.

## Demo

On the arm64 tablet (`SM-X216B`) at one pane, and on `dev36` for the phone. Nothing here runs a cell, so
`dev36` also serves for the instrumented tests; the flow goes on the tablet.

1. Cold start, System theme, font scale 1.0 (`scripts/font-scale.sh`). Open More's rail and a notebook with
   three tabs. Read More's rows and a tab off `uiautomator dump`: the icon's side over its label's height.
2. Set font scale 2.0 and read the same nodes. The ratio is within the tolerance T2 names of the 1.0 value
   (1.20 for More's rail, 0.88 for a tab), where today it is 0.64 and 0.44 (`GAP-203`).
3. The tab row is taller at 2.0 by what its label grew by, and no glyph or label is cut in the screenshot.
4. Every control that was 48dp or more is still so, and `read_font_scale_fit.py` reports no overlap that was
   not there at 1.0 and no target newly under 48dp.
5. Repeat 1 to 4 on `dev36` at 320dp wide, where a tab is already squeezed to its 80dp floor.
6. Put the font scale back to the device's own. Nothing persists: the scale is the user's setting.

The item is not `shipped` until this has run on both devices and its frames were looked at.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `intellij-community@406a27a8` | FULL | `platform/platform-impl/src/com/intellij/ide/ui/laf/LafManagerImpl.kt` (`patchLafFonts`, line 851), `platform/util/ui/src/com/intellij/ui/scale/JBUIScale.kt` | the UI font size is the one input to `userScaleFactor`, which sizes icons and geometry alike, so a glyph can never fall behind its label |
| `jupyterlab@7f0e6906` | PARTIAL | `packages/launcher/style/base.css:13-15`, `:147` | the launcher's icons are `em`: `--jp-private-launcher-large-icon-size: 4em` follows the card's font. Its toolbar and tab icons are not read here |
| `vscode@3f3380c6` | ABSENT | `src/vs/base/browser/ui/codicons/codicon/codicon.css:13` | `font: normal normal normal 16px/1 codicon`: a fixed 16px glyph. No setting scales text alone (`window.zoomLevel` moves both) |
| `vscode-jupyter@f01d80ca` | ABSENT | `src/webviews/webview-side/react-common/imageButton.css:2` | `--button-size: 18px`, fixed |
| `termux-app@3df69d1d` | ABSENT | `app/src/main/res/layout/item_terminal_sessions_list.xml` | a text row in `14sp` with no icon beside it |
| `winlator@fb66541b` | ABSENT | `app/app/src/main/res/layout/container_list_item.xml:13-14` | a 36dp icon beside text, in `dp` |
| the shipped rootfs | ABSENT | — | no UI of its own |

**Count:** 2 of 7 follow the text (IntelliJ for all its icons, JupyterLab for its launcher), and the
three that are Android or a browser keep a fixed glyph. Android's own documentation says the same of its
default: font scaling "only affects text elements" and leaves "containers, padding, and icons" at a fixed
size, and its alternative, density scaling, moves everything at once
([Compose, Support user-scalable content](https://developer.android.com/develop/ui/compose/accessibility/scalable-content)).
So this is a deliberate departure from the platform's default, toward the one reference that decided the
question, and the PRD does not call it parity.

Which reference answers which question is in [`../architecture/cross-cutting.md`](../architecture/cross-cutting.md)
§8.3: the Compose and Android docs answer how a component behaves, VS Code answers presentation, IntelliJ the
editor and its geometry.

## Implementation inventory

### `intellij-community@406a27a8`

| Concern | Files |
|---|---|
| The one input | `platform/platform-impl/src/com/intellij/ide/ui/laf/LafManagerImpl.kt` (`patchLafFonts`) |
| The scale factor | `platform/util/ui/src/com/intellij/ui/scale/JBUIScale.kt` (`userScaleFactor`) |
| Edge case it handles | one factor for font and geometry, so there is no ratio to keep: that is why it cannot arrive at this gap and why a Compose port needs a helper |

### `jupyterlab@7f0e6906`

| Concern | Files |
|---|---|
| Icon sized in `em` | `packages/launcher/style/base.css:13-15` (`5.231em`, `4em`, `2.462em`) and `:147` |

### Android, Compose

| Concern | Files |
|---|---|
| Text-only scaling leaves icons fixed | [Support user-scalable content](https://developer.android.com/develop/ui/compose/accessibility/scalable-content) |
| Non-linear conversion at large scales | `TextUnit.toDp()` through `LocalDensity`; `FontScaleConverter` in the platform. Android advises against hand-written `fontScale` arithmetic because Android 14's curve is not linear: read the converted size, as T1 measures |

Disagreement: IntelliJ scales everything together, JupyterLab scales its launcher only, and VS Code, the
extension and both Android references scale nothing. The decision is which of our glyphs sit *beside text*.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Mechanism | an `Icon` wrapper · a `Modifier` that sizes from `LocalDensity` · a `Dp` helper called at each site | the app has 98 `Icon(` sites, 11 with a fixed `Modifier.size` | one function in `theme/` beside `Spacing.kt` returning a `Dp` from an `sp`, so a site keeps its `Modifier.size(...)` and the diff is the argument. Settled in T1 by the smaller diff |
| Conversion | `base.value * fontScale` · `base.sp.toDp()` through `LocalDensity` | the first overshoots the platform's non-linear curve at 2.0 | `sp.toDp()`, because it follows the same curve the label's size does |
| Which glyphs | every `Icon(` · only those beside text | a standalone glyph (the Launcher's 40dp, a 48dp `IconButton`'s centre) has no label to keep a ratio to | only an icon in a row with text of the same line. Read each site in T3, not swept |
| Targets | grow with the glyph · stay | ADR-0048 sets 48dp as a minimum, not a size | stay: a touch target is not a glyph, and `read_touch_targets.py` must show none under 48dp |
| The tab row | stay 32dp · be a minimum | the label already fills the row at 2.0 and the row holds the glyph, the label and the close | `TAB_HEIGHT` becomes a minimum, so the row is as tall as its tallest child. [ADR-0142](../adr/0142-the-tab-strips-controls-keep-the-strips-height.md) has Reload and Save and the Up arrow reserve `TAB_HEIGHT + ACTIVE_EDGE`, so T1 reads whether they follow the row |

Anything here that outlives the item becomes an ADR before the PRD is deleted.

## Gap analysis

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| A tab's glyph | `TAB_ICON` 14dp, `MainAreaTab.kt:270`, in a `TAB_HEIGHT` 32dp row (`MainAreaTabStrip.kt:432`, `MainAreaTab.kt:122`) | `intellij-community@406a27a8` `JBUIScale.kt` | absent: 0.88 of the label at 1.0, 0.44 at 2.0 |
| More's row | `Icon(icon, contentDescription = null)` in a Material `WideNavigationRailItem`, `MoreRail.kt:96`: Material's 24dp | same | absent: 1.20 at 1.0, 0.64 at 2.0 |
| The other 87 unsized sites | `Icon(` with no `Modifier.size` within four lines, across `ui/` and `extensions/ai` | same | absent; which of them sit beside text is not yet read |
| The 11 sized sites | `TAB_ICON`, `TRAILING_ICON`, `UTILITY_ICON`, `ROW_ICON` (`RunningSidePanel.kt:433`), `DISCLOSURE_ICON_SIZE`, `STATUS_GLYPH_SIZE`, `DETAIL_GLYPH`, `CRUMB_SEPARATOR_SIZE` and Material's `SegmentedButtonDefaults.IconSize` | same | absent |
| Test and measure tools | `AtFontScale` (`app/src/androidTest/java/com/jupydroid/app/ui/FontScale.kt`), `scripts/font-scale.sh`, `scripts/read_font_scale_fit.py`, `scripts/read_touch_targets.py` | — | complete: this item adds no tool |

Ours is absent, not present-but-unreachable. A label already grows (`sp`), so only the glyph's size changes.

## Deviations

- Not ported: IntelliJ's one scale factor for everything. A phone's density already belongs to the
  platform, and moving spacing, padding and targets with the font is the density strategy Android's
  documentation names as the other choice, which would reflow every screen this audit just measured.
- A glyph with no label beside it keeps its `dp` (the Launcher's 40dp).
- No cap is invented: Android's font scale stops at the platform's largest (2.0), so the glyph is bounded by
  the label it follows.

## Open decisions

- Whether Material's `WideNavigationRailItem` lays out a larger icon in its slot or clips it at 24dp.
  Read on the tablet in T2, not assumed.
- Whether Reload, Save and the Up arrow follow a taller tab row (ADR-0142 reserves one height for them).
  Read on both devices in T1.
- The tolerance for "keeps its ratio". The conversion is non-linear and a label's line height is not its
  `sp`, so equality is wrong; T1 measures the spread at 1.0, 1.3, 1.5 and 2.0 and states it.
- Which of the 87 unsized sites sit beside text. Read one by one in T3.

## Surface

A theme item with no domain, port or adapter layer.

| Layer | Files |
|---|---|
| `theme/` | the conversion, beside `Spacing.kt`; its `Dp` is named and documented as `check_ui_dimensions.py` asks |
| `ui/shell/` | `MainAreaTab.kt`, `MainAreaTabStrip.kt`, `MoreRail.kt`; then `RunningSidePanel.kt` and `SidePanelRail.kt` as T3 reads |
| `ui/editor/` | `NotebookBar.kt`, `CellHeader.kt`, `DebuggerControlBar.kt` as T3 reads |
| unit tests | the conversion at 1.0, 1.5 and 2.0 against the label's converted size |
| instrumented tests | a tab and a More row at 2.0 under `AtFontScale`: glyph over label stays within the tolerance, the row holds both |
| `.maestro/` flow + `scripts/` driver | the existing font-scale drivers, extended with the two nodes read; `read_font_scale_fit.py` for overlap and targets |

## Tasks

- [ ] T1 — The conversion in `theme/` and the tab: red at a tab at 2.0 whose 14dp glyph is 0.44 of its
  label, green when it follows the label and the row is a minimum, not a fixed 32dp; the tolerance stated
  from the measured spread, and whether Reload, Save and the Up arrow follow the row read.
- [ ] T2 — More's rail row: red at 0.64 at 2.0, green within the tolerance; a Material slot that will not
  take the size is replaced rather than worked around.
- [ ] T3 — The other sites that sit beside text, each read before it is touched, each with a test.
- [ ] T4 — The before and after on the tablet at one pane and on `dev36` at 1.0 and 2.0, the ADR for the
  mechanism and the deviation, `GAP-203` closed, one edited backlog line, this file deleted.

## Out of scope

- Touch targets: they stay at 48dp, as [ADR-0048](../adr/0048-touch-rules-measured-on-device.md) sets.
- A glyph with no label beside it.
- Density scaling of the app, and a setting to choose between the two strategies.
- Truncation of a label at 2.0, which `AUD-014` records and `JD-084` does not touch.
