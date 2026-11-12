# JD-068 — A keyboard focus is a ring, not a pale plate

Milestone: `M11` · State: open · Depends on: — · Closes: GAP-202

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

A person who Tabs to a control sees a 3:1 indicator on it, in Light and in Dark; today they see one
rounded plate at 1.14:1 against what was there.

## Demo

On the arm64 tablet (`SM-X216B`). Nothing here runs a cell, so `dev36` also serves for the tests,
but the flow goes on the tablet with a hardware keyboard.

1. Cold start, app theme Dark, two panes. Press Tab from the rail's first item to the Workspace
   header: ten stops. Each stop shows a ring.
2. Read each stop's ring against the frame before it with `scripts/read_text_contrast.py --region`:
   at least 3:1 where it was 1.14:1 to 1.15:1 (`GAP-202`, `docs/architecture.md`).
3. Repeat in app Light.
4. Repeat Settings' controls, and with `high_text_contrast_enabled` 1: the ring is no fainter.
5. Tap a control with a finger: no ring appears (the platform keeps touch focus mode apart).
6. Kill and relaunch — nothing persists, because focus is not state.

The item is not `shipped` until this has run on the tablet and its frames were looked at.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab@7f0e6906` | FULL | `packages/theme-light-extension/style/variables.css:459-460`, `packages/ui-components/style/base.css:212-214` | `--jp-focus-outline-color` (brand colour) at `--jp-focus-outline-width` 2px, on `:focus-visible` |
| `vscode@3f3380c6` | FULL | `src/vs/workbench/browser/media/style.css:257-272`, `src/vs/platform/theme/common/colors/baseColors.ts:37-46`, `extensions/theme-defaults/themes/2026-dark.json:12`, `2026-light.json:12` | one global rule: `outline: 1px solid focusBorder`, offset -1px (inside the control); `#3994BCB3` Dark, `#0069CCFF` Light, `#F38518`/`#006BBD` in the two high-contrast themes |
| `vscode-jupyter@f01d80ca` | PARTIAL | `src/webviews/webview-side/data-explorer/sliceControl.css:142,181,189`, `reactSlickGrid.css:120` | inherits the host's `focusBorder` and uses it on its own webview controls |
| `intellij-community@406a27a8` | FULL | `platform/platform-impl/src/com/intellij/ide/ui/laf/darcula/DarculaUIUtil.java:149` (`paintFocusBorder`), `platform/platform-resources/src/themes/HighContrast.theme.json:35` | a painted border around the control; `focusColor` per theme, `#1AEBFF` in high contrast |
| `termux-app@3df69d1d` | ABSENT | — | no `state_focused` drawable in any resource file; the platform default shows |
| `winlator@fb66541b` | PARTIAL | `app/app/src/main/res/drawable/button_positive.xml:4`, `edit_text.xml:4` | `button_positive` and `button_neutral` map `state_focused` to their *normal* drawable (no mark); `edit_text` and `combo_box` name a focus drawable |
| the shipped rootfs | ABSENT | — | no UI of its own |

**Count:** 5 of 7 draw a distinct focus mark, and the three desktop products draw it as an outline in
one themeable colour. That is the decision: a ring from one palette token, not a state layer. The two
that do not are Android apps that keep the platform's default, which is what ours is.

## Implementation inventory

### `vscode@3f3380c6`

| Concern | Files |
|---|---|
| The rule | `src/vs/workbench/browser/media/style.css:257-272` |
| The colour, per theme | `extensions/theme-defaults/themes/2026-dark.json:12`, `2026-light.json:12`; read it with `scripts/resolve_vscode_theme.py <theme> --color focusBorder` |
| High-contrast variants | `src/vs/platform/theme/common/colors/baseColors.ts:37-46` |
| Edge case | the alpha: `#3994BCB3` is 70%, so contrast is read after compositing onto the part it sits on (3.13:1 over the side bar) |

### `jupyterlab@7f0e6906`

| Concern | Files |
|---|---|
| The rule | `packages/ui-components/style/base.css:212-214`, `:focus-visible` only |
| Width and colour | `packages/theme-light-extension/style/variables.css:459-460` |

### `intellij-community@406a27a8`

| Concern | Files |
|---|---|
| The paint | `platform/platform-impl/src/com/intellij/ide/ui/laf/darcula/DarculaUIUtil.java:149` |
| Colour per theme | `platform/platform-resources/src/themes/HighContrast.theme.json:35`, `intellijlaf.theme.json:239`, `darcula.theme.json:76` |

Disagreement: VS Code draws 1px *inside* the control, JupyterLab 2px, IntelliJ a painted border
outside it. Width is a decision below.

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Mark | state layer (ours today, winlator buttons) · outline (VS Code, JupyterLab, IntelliJ) | a state layer cannot reach 3:1 without becoming a fill that hides the control | an outline; three mature references and WCAG 1.4.11 agree |
| Colour | VS Code `focusBorder` · JupyterLab brand colour · IntelliJ `focusColor` | VS Code answers for presentation here (ADR-0040) | `focusBorder` from the resolved 2026 files, a `ChromePalette` token pinned by equality as `ChromePaletteTest` pins the others |
| Width | 1px inside · 2px | 1px at 160dpi on a touch tablet may fall under what a reader can see | settled by reading both on the tablet, recorded in the ADR |
| Where it is drawn | one `Indication` in `LocalIndication` · a modifier at each site | about 50 `clickable`/`selectable` sites and the Material components all read `LocalIndication` | one `Indication`, one home; sites that bypass it are found by Tab and repaired in T2 |
| When | every focus · keyboard focus only | a finger tap also focuses some controls | the platform's focus mode decides; the ring follows `FocusInteraction`, which is what the indication receives |

## Gap analysis

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| The indicator on a control | Material3 1.4.0's default ripple, a state layer at 1.14:1; nothing sets `LocalIndication` (`theme/Theme.kt`) | `vscode@3f3380c6` `style.css:257-272` | absent |
| The colour | no token; `ChromePalette` has the parts' surfaces and borders only | `2026-dark.json:12`, `2026-light.json:12` | absent |
| A text field's focus | three cell fields draw a border (`RawCellField.kt:69`, `CodeCellField.kt:147`, `MarkdownCellField.kt:78`), 3.13:1 Dark and 5.18:1 Light | same | complete |

## Deviations

- High-contrast variants (`#F38518`, `#006BBD`) are not ported: AUD-014 found a named high-contrast
  palette is not wanted on these numbers (`docs/research/parity-ledger.md`).
- Focus order and traversal are not touched.

## Open decisions

- Whether Material's navigation rail item draws its own pill from `interactionSource` and so bypasses
  `LocalIndication`. Read in T2 by Tab on the tablet, not assumed.
- Width, as above. Settled by reading frames; goes into the ADR.

## Surface

A theme item, so it has no domain, port or adapter layer; the user-visible half is the ring on every
control that takes focus.

| Layer | Files |
|---|---|
| `theme/` | `ChromePalette.kt`, `ChromePalettes.kt` (the `focusBorder` token), a new `FocusRingIndication.kt`, `Theme.kt` (provides it as `LocalIndication`) |
| unit tests | `ChromePaletteTest.kt` (the token equals the resolved value and clears 3:1 over each surface it sits on) |
| instrumented tests | a focused `clickable` draws a ring at 3:1 against its unfocused pixels, in both palettes |
| `.maestro/` flow + `scripts/` driver | Tab across the rail and Settings, a frame per stop, read by `read_text_contrast.py --region` |

`scripts/check_palette_consumers.py` refuses a token nothing draws, so the token and its indication
ship in one task.

## Tasks

- [ ] T1 — A `focusBorder` token on both chrome palettes and a `FocusRingIndication` that draws it,
  provided as `LocalIndication`: red at 1.14:1 on a focused `clickable`, green at 3:1 in both themes.
- [ ] T2 — Controls that bypass `LocalIndication`, found by Tab across the rail and Settings on the
  tablet, each repaired with a test.
- [ ] T3 — The before and after on the tablet for all ten stops in both themes and under the
  high-contrast setting; the ADR for the mark, colour and width; close `GAP-202`; one backlog line;
  delete this file.

## Out of scope

A high-contrast palette, focus order and traversal, and the cell fields' own border, which exists.
