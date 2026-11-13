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
3. The tab row is still 32dp at 2.0, because the label's line is 28dp there and the grown glyph is 26dp, and
   no glyph is cut in the screenshot.
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
| Conversion | `base.value * fontScale` · `base.sp.toDp()` through `LocalDensity` | the first overshoots the platform's non-linear curve at 2.0 | `sp.toDp()` of the **label's font size**, applied to the glyph as a factor: a glyph about as large as its label's font (a 14dp tab glyph) may be converted from its own size, but a 24dp glyph beside a 14sp label reached only 0.95 of the label's line at 2.0, because the curve grows 24sp by 1.5 and 14sp by 1.9. T2 measured it |
| Which glyphs | every `Icon(` · only those beside text | a standalone glyph (the Launcher's 40dp, a 48dp `IconButton`'s centre) has no label to keep a ratio to | only an icon in a row with text of the same line. Read each site in T3, not swept |
| Targets | grow with the glyph · stay | ADR-0048 sets 48dp as a minimum, not a size | stay: a touch target is not a glyph, and `read_touch_targets.py` must show none under 48dp |
| The tab row | stay 32dp · be a minimum | the label's line is 28dp at 2.0 (`dev36`, API 36) and the glyph that follows it 26dp, so the row holds both | stay 32dp: no scale the platform offers needs more, so a minimum would be code nothing reaches. [ADR-0142](../adr/0142-the-tab-strips-controls-keep-the-strips-height.md)'s Reload, Save and Up arrow therefore keep `TAB_HEIGHT + ACTIVE_EDGE` |

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

- Closed in T2: Material's `WideNavigationRailItem` places its icon at the size the icon measured and clips
  nothing (`NavigationItem.kt`, material3 1.4.0), so no slot is replaced. The row grows with the glyph: 56.0dp at
  1.0 and 76.7dp at 2.0 on the tablet, where the label alone made it 69.3dp.
- Closed in T2: the tolerance on a More row is 0.05 of the glyph over label ratio at 1.0 (1.200). Measured on
  `dev36`: 1.185, 1.188 and 1.184 at 1.3, 1.5 and 2.0, a spread of 0.016.
- Closed in T1: the tab row does not grow, so Reload, Save and the Up arrow have no taller row to follow.
- Closed in T1: the tolerance for "keeps its ratio" is 0.10 of the glyph over label ratio at 1.0. Measured on
  `dev36`: 0.875 at 1.0 against 0.941, 0.957 and 0.929 at 1.3, 1.5 and 2.0, a spread of at most 0.082, because
  Android 14's curve grows 14sp by 1.86 at 2.0 and the label's 16sp by 1.75. Before: 0.693, 0.609 and 0.500.
- Closed by reading: of the 98 `Icon(` sites, about 17 sit beside text and are the tasks T3 to T7. The rest keep
  their `dp`: an icon inside a `FocusRingIconButton` (a 48dp target), a label-less rail item, `ActiveTabSave` and
  `ActiveTabReload`, the launcher's tile and `LineNumbers`.
- Closed in T6: a Material button's content slot fixes no size and clips nothing, as More's rail did not.
- Open until T7: whether the two Material buttons in the AI extension differ.

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

- [x] T1 — The conversion in `theme/` (`Density.glyphBesideText`) and the tab: the glyph follows the label, the
  tolerance is 0.10 from the measured spread, and the row stays 32dp because it already holds both, so Reload, Save
  and the Up arrow are untouched. The tablet at two panes reads 0 newly overlapping and 0 newly under 48dp.
- [x] T2 — More's rail row (`MoreRailRow`): the glyph follows the label's font size, so `glyphBesideText` takes
  the label's size (default: the glyph's own, which is the tab's T1 behaviour). Red at 0.63 at 2.0, then 0.95 when
  converted from its own 24sp, green at 1.185 to 1.188 against 1.200. The tablet at one pane reads 0 newly
  overlapping and 0 newly under 48dp on every surface; the row is 56.0dp at 1.0 and 76.7dp at 2.0.
- [x] T3 — The Running panel's row icon (`ROW_ICON`) and section caret (`SECTION_CARET`), each with a test. Both
  16dp glyphs follow their 14sp label's font size: red at 0.59, 0.50 and 0.42 at 1.3, 1.5 and 2.0 against 0.80 at
  1.0, green at 0.78 to 0.79, so the tolerance is 0.05. The row (64dp) and header (48dp, 54dp at 2.0) did not grow.
- [x] T4 — The list rows with a leading glyph: Explorer (`ExplorerScreen`), Recents (`RecentsSection`) and the
  `Twistie` callers, each with a test (`ListGlyphFontScaleTest`). Each 24dp glyph follows its label's font size
  (`bodyLarge` for a row, `labelLarge` for the Recent heading, `bodyMedium` for a search file header): red at 0.77,
  0.69 and 0.57 for a row against 1.00 at 1.0, and 0.89, 0.75 and 0.63 for a chevron against 1.20, green at 0.97 to
  1.00 and 1.18 to 1.19, so the tolerance is 0.05. `Twistie` takes the label's size. The Explorer row (84dp at 1.3) and
  the Recent heading (48dp) did not grow; a search file header does, 51dp to 61dp at 2.0, because its chevron is now 45dp.
- [x] T5 — The editor rows: the debugger's disclosure, a diagnostic's detail glyph, the kernel status glyph and the
  breadcrumb's separator, each with a test (`EditorGlyphFontScaleTest`, and `DiagnosticGlyphFontScaleTest` because the
  detail card is a popup window that `AtFontScale` does not reach). Each glyph follows its label's font size: red at
  0.42 (separator, 0.80 at 1.0), 0.63 (kernel, 1.20), 0.47 (disclosure, 0.89) and 0.375 (detail, 0.75) at 2.0, green at
  0.78 to 0.79, 1.18 to 1.19, 0.84 to 0.88 and 0.75 to 0.76, so the tolerance is 0.06 (0.05 for the card). A kernel
  glyph with no name beside it keeps 24dp. The breadcrumb row (48dp) did not grow; the kernel button does, 40dp to 61dp
  at 2.0, because its glyph is now 45dp.
- [x] T6 — The glyphs inside a Material button beside its label in `:app` (the drop-down arrows of
  `CellTypeControl` and `SpaceDefaultKernelField`, `ChoiceSegments`' check, `SpaceVariablesEditor`'s add), each with
  a test (`ButtonGlyphFontScaleTest`). Material's button content slot fixes no size and clips nothing, so no slot was
  replaced: each glyph follows `labelLarge`'s font size. Red at 0.89, 0.75 and 0.63 (the three 24dp glyphs, 1.20 at 1.0)
  and 0.67, 0.56 and 0.47 (the check, 0.90) at 1.3, 1.5 and 2.0, green at 1.184 to 1.188 and 0.868 to 0.889, so the
  tolerance is 0.05. The cell type button grows 54dp to 61dp at 2.0, because its caret is now 45dp.
- [ ] T7 — The same in the AI extension (`AgentControls`' drop-down arrow, `ChatList`'s button icon). An extension is
  built against `:extension-api` and never against `:app` ([ADR-0104](../adr/0104-extensions-are-compile-time-modules-against-an-extension-api.md)),
  so `glyphBesideText` moves into `:extension-api` first, as the focus ring did, and its callers in `:app` follow.
- [ ] T8 — The before and after on the tablet at one pane and on `dev36` at 1.0 and 2.0, the ADR for the
  mechanism and the deviation, `GAP-203` closed, one edited backlog line, this file deleted.

## Out of scope

- Touch targets: they stay at 48dp, as [ADR-0048](../adr/0048-touch-rules-measured-on-device.md) sets.
- A glyph with no label beside it.
- Density scaling of the app, and a setting to choose between the two strategies.
- Truncation of a label at 2.0, which `AUD-014` records and `JD-084` does not touch.
