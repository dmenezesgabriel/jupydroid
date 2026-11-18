# JD-094 — A press on the middle of a tab never closes it on a phone

Milestone: `M11` · State: open · Depends on: — · Closes: `GAP-210`

## Outcome

On a phone, with a strip so crowded that every tab is at its minimum width, a tap or a long press on a tab's
middle activates it or opens its menu, and only the ✕ closes it.

## Demo

On `dev36` (nothing here runs a cell, so the emulator is the device `AGENTS.md` rule 2 allows), at 320x640 and
at 360x640.

1. Cold start: install, launch, open a Space, open four files at 320dp and five at 360dp, so every tab is at
   the floor.
2. Tap the middle of an inactive tab: it becomes the active tab and none is removed.
3. Long press the middle of any tab: its menu opens and the stack keeps every tab.
4. Tap the ✕ glyph of a tab: that tab closes.

Each step is a screenshot and a UI dump kept locally (ADR-0132), and the frames are
looked at, not only the dumps.

## Who else implements this

None of the references is a touch UI; the question is how each lays a tab's close beside its label and what
it floors a tab at.

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `jupyterlab@7f0e690 packages/application/style/tabs.css:42-53` | A horizontal tab is `flex: 0 1 216px; min-width: 0`: no floor. The 80px floors on `:36`, `:150` are the vertical bar's and a left/right current tab's. The ✕ is shown on hover, which a touch screen lacks. |
| `vscode` | PARTIAL | `vscode@3f3380c src/vs/workbench/browser/parts/editor/media/multieditortabscontrol.css:423-437` | The action slot is 28px wide, pushed out of view in shrink sizing to make room; `min-width: 80px` only where the close shows on the left (`:289`). |
| `vscode-jupyter` | ABSENT | — | No tab strip: it uses the editor's. |
| `intellij-community` | PARTIAL | `intellij-community@406a27a platform/platform-api/src/com/intellij/ui/tabs/impl/TabLabel.kt` | The close is an action in the tab label, hover-revealed; mouse-sized. Its sizing was not read beyond the file's existence. |
| `termux-app` | ABSENT | — | Sessions are a drawer list, not tabs. |
| `winlator` | ABSENT | — | No tabs. |
| the shipped rootfs | ABSENT | — | No UI. |

**Count:** 3 of 7 have a tab close at all, and none of them is touch. They say what a floor is for (a tab stays
legible) and that JupyterLab has none on a horizontal tab, so our 80dp is our own number, set in
`MainAreaTabStrip.kt` to keep a tab a touch target (ADR-0142), not one we ported.

## Gap analysis

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| A tab's floor | `TAB_MIN_WIDTH = 80.dp`, `ui/shell/MainAreaTabStrip.kt` | `jupyterlab@7f0e690 tabs.css:53` (none) | Present and partial: the floor is below the width at which a tab's middle is clear of the close |
| The close's reach | 24dp slot (`TAB_CLOSE_TOUCH`, `ui/shell/MainAreaTab.kt`), Compose widens a press to 48dp, 12dp into the label to the tab's right edge (ADR-0142) | `vscode@3f3380c` 28px slot | Complete as stated; crosses the centre of a tab narrower than 80dp plus the margin |
| The test of it | `TabCloseTouchAreaTest` presses by dp from the close's corner on a 107dp tab | — | No test presses the middle of a minimum-width tab |

The arithmetic: the slot's left edge is `w - 28`, the reach starts 12dp before it, at `w - 40`, and a tab's
middle is `w / 2`. The middle is the close's while `w / 2 >= w - 40`, that is `w <= 80`. At the floor it sits on
the boundary, which is what `GAP-210` read (x=40 closed, x=39 opened the menu). `share = maxWidth / tabs.size`
is floored at the 80dp, so this is reached whenever `tabs.size > maxWidth / 80`: four tabs at 320dp, five at
360dp, not only the four of `GAP-210`'s sentence on 360dp.

## Open decisions

| Axis | Options | Trade-off | Choice |
|---|---|---|---|
| Where to repair | (A) raise `TAB_MIN_WIDTH` above 80dp so the middle clears the reach by `(w - 80) / 2`; (B) narrow the close's reach with a smaller minimum touch target around it | A keeps ADR-0142's stated reach and costs the label the dp the floor grows by, on a tab already at the floor; B takes the close under what ADR-0142 states and supersedes it | **A**, settled on the numbers T1 reads, and stated in an ADR amending ADR-0142 |
| The floor's value | A number picked, or derived | A magic number rots | derived from the reach: the middle must clear it by a stated margin, so the floor is `2 x (reach + margin)` and the test presses the middle at it |

## Tasks

- [ ] T1 — red: a test that presses the middle of a minimum-width tab and finds `closed` empty (tap and long
  press), at 320dp with four tabs and 360dp with five; then raise the floor so it is green; the strip's other
  tests (`TabCloseTouchAreaTest`, `TabCloseSiblingsTest`, `MainAreaTabStripRevealTest`,
  `MainAreaTabFontScaleTest`) stay green
- [ ] T2 — the demo above on `dev36` at 320 and 360, frames looked at, tab width and label room read before and
  after
- [ ] T3 — an ADR amending ADR-0142's statement of the reach for a minimum-width tab, `GAP-210` corrected in
  `docs/architecture.md`, and this PRD deleted when T2 has run

## Out of scope

`JD-095` (SystemUI's dialog), `JD-093` (the tablet flows) and the strip's 32dp height, which ADR-0142 says is a
decision about every control in the strip.
