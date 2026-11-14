# JD-087 — The terminal draws its glyphs at the pitch its cells are counted at

Milestone: `M11` · State: open · Depends on: — · Closes: `GAP-092`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

On a full-width line the terminal's last glyph sits inside its own column and the gap to the pane's right edge
matches the left's 12dp; today it is 22.7dp and 28.7dp on the tablet, because a cell is counted at a whole
15 px and drawn at the font's 14.81 px advance.

## Demo

No cell runs here, but the pty's column count is read off the shell,
which only the tablet (`SM-X216B`) has; `dev36` serves the instrumented test.

1. Cold start on the tablet, portrait and landscape, open a terminal, and print a ruler row as wide as the pane
   (`scripts/terminal-margin-driver.sh`). Today: left gap 12.0dp, right gap 22.7dp at 1146 px and 28.7dp at
   1200 px, the pty told 74 and 77 columns where 74 and 78 fit.
2. After: both gaps within a column's width of 12dp, and the pty told the columns that fit at the drawn pitch
   (`scripts/read_terminal_margin.py`).
3. Long-press the word under the last column; Copy returns that word, not the one before it.

The item is not `shipped` until this has run on the tablet and the frames were looked at.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `termux-app@3df69d1d` | FULL | `terminal-view/.../TerminalRenderer.java:47` (`mFontWidth = measureText("X")`), `:189` (`left = startColumn * mFontWidth`), `TerminalView.java:547,990` | the width stays a `float`; origin, column count and press all divide by it |
| `jediterm@377b76e6` | not read | — | its renderer is Swing; not the question here |
| `vscode`, `vscode-jupyter`, `intellij-community`, `jupyterlab`, `winlator`, the shipped rootfs | not read | — | a browser's xterm.js or Swing's own metrics, none ours |

**Count:** 1 of 7 read, and it is the reference our emulator is already transcribed from. Reading the other
six would not change the decision: the defect is in our own type, not a disagreement between references.

## Implementation inventory

### termux-app@3df69d1d

| Concern | Files |
|---|---|
| The pitch | `terminal-view/src/main/java/com/termux/view/TerminalRenderer.java` (`mFontWidth`, `getFontWidth`) |
| Columns counted from it | `terminal-view/src/main/java/com/termux/view/TerminalView.java` (`updateSize`) |
| A press mapped through it | `TerminalView.java` (`getColumnAndRow`) |

### Ours

| Concern | Files |
|---|---|
| The measurement, an `IntSize` | `app/src/main/java/com/jupydroid/app/ui/terminal/TerminalCellMetrics.kt` |
| Columns counted | `ui/terminal/TerminalGridSize.kt` |
| A press mapped | `ui/terminal/TerminalCellPosition.kt`, `TerminalSelectionEnd.kt` |
| Drawn | `ui/terminal/TerminalCanvas.kt` (`drawRun`, `drawCursor`), `TerminalTextSelection.kt` (`boundsIn`) |
| Wired | `ui/terminal/TerminalPaneScreen.kt`, `TerminalPaneViewModel.kt` (`resize`) |
| What the shell is told | `terminal/TerminalReporting.kt` (integer pixels, CSI 14t and 16t) |
| The margin test | `app/src/androidTest/java/com/jupydroid/app/TerminalMarginTest.kt` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the drift is closed | widen the right inset · draw the run scaled into whole cells · keep the cell's width fractional | an inset hides it behind a number that moves with the font size (ADR-0144 says not to); a scale per run is Termux's repair for a font that lies, which ours does not | the cell's width stays fractional, as Termux's `mFontWidth`: the cause is the type |
| The cell's type | a new `TerminalCell` · Compose's `Size` | a new type is one more name to learn for two floats | `Size`; height stays integer-valued, as Termux's `mFontLineSpacing` is an `int` |
| What the shell is told | the rounded width · the fractional one | CSI 14t and 16t report whole pixels | round once, at `TerminalPaneViewModel.resize`'s call |

## Gap analysis

| Concern | Ours today (file) | Best reference | Gap |
|---|---|---|---|
| Cell width | whole pixel from `TextMeasurer.measure("X").size` | `TerminalRenderer.java:47` | present and wrong: 15 px against a 14.81 px advance |
| Proportional-font correction | none | `TerminalRenderer.java:112` | absent, and stays: the shipped font is monospace |

## Deviations

None.

## Open decisions

- The column count may rise (77 to 78 at 1200 px): the shell is now told the columns that fit at the drawn pitch.
  ADR-0144 predicts it and states it does not supersede that decision. Measured, not assumed, in T2.

## Surface

A UI item with no domain, port or adapter.

| Layer | Files |
|---|---|
| `ui/terminal` | the files under "Ours" above |
| unit tests | `TerminalGridSizeTest`, `TerminalCellAtTest`, `TerminalSelectionBoundsTest`, `TerminalSelectionEndTest` |
| instrumented tests | `TerminalMarginTest`, on `dev36` |
| `.maestro/` flow + `scripts/` driver | `terminal-margin.yaml` and `terminal-margin-driver.sh`, `read_terminal_margin.py` |

## Tasks

- [x] T1 — red tests that a fractional cell counts, maps and bounds at its own width, then the `Size` cell
  through the files above.
- [ ] T2 — read both widths on the tablet before and after, look at the frames, run the regression flows that
  read columns or a press, and edit `parity-ledger.md` and ADR-0144's consequences to match.

## Out of scope

Termux's per-run measure-and-scale for a font that lies, the margin's value, and any vertical inset.
