# JD-076 — A line's breakpoint and its diagnostic can each be tapped, and removed, on a phone

Milestone: `M11` · State: open · Depends on: — · Closes: `GAP-194`

> Delete this file when the item ships ([ADR-0001](../adr/0001-split-durable-decisions-from-ephemeral-build-evidence.md)).

## Outcome

On a code cell's line that carries both a breakpoint and a language-server finding, one tap removes the breakpoint and
a different tap reads the finding, at a phone's width. Today the two share one target, so each tap does the other's job.

## Demo

The tap geometry needs no kernel, so `dev36` serves the phone's width and the instrumented tests. The finding
itself comes from `pylsp`, which runs only in an arm64 Space, so the flow that reads a real finding goes on the tablet
(`SM-X216B`) at both of its widths.

1. Cold start, open `Diagnostics.ipynb`, run the cell, wait for the finding on line 1 (`diagnostics.yaml`'s gate).
2. Arm line 1's breakpoint. The line now holds both: say what the frame shows (today, one circle drawn over the
   diagnostic's icon).
3. Tap where the breakpoint is drawn: it is removed and no card opens. Today the card opens and the breakpoint stays.
4. Tap where the finding is shown: its card opens and no breakpoint is set or removed. Today a finger that misses the
   mark by a few dp lands on the numbers and sets one.
5. Repeat on a line with only a breakpoint and on a line with only a finding: each still does one thing.

Not `shipped` until this has run on both devices and the frames were looked at.

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `vscode@3f3380c6` | FULL | `src/vs/workbench/contrib/debug/browser/breakpointEditorContribution.ts:55-56` (`glyphMargin: { position: GlyphMarginLane.Right }`), `src/vs/editor/contrib/hover/browser/markerHoverParticipant.ts` | breakpoint in the glyph margin; a diagnostic is a squiggle in the text with its message on hover. `glyphMargin` finds nothing under `src/vs/workbench/contrib/markers` |
| `jupyterlab@7f0e6906` | PARTIAL | `packages/debugger/src/handlers/editor.ts:228-236` (`cm-breakpoint-gutter`, `mousedown`) | the breakpoint has a gutter of its own; the debugger does not draw diagnostics |
| `intellij-community@406a27a8` | PARTIAL | `platform/lang-impl/src/com/intellij/codeInsight/daemon/impl/DaemonTooltipRendererProvider.java` | a finding's tooltip hangs off the error stripe and the text range, not the breakpoint gutter; not read further |
| `vscode-jupyter@f01d80ca` | PARTIAL | `src/interactive-window/debugger/jupyter/kernelDebugAdapter.ts` | debugging goes through VS Code's editor; no gutter code of its own |
| `termux-app`, `winlator`, the shipped rootfs | ABSENT | — | a terminal, a runtime, an image: none draws an editor gutter |

**Count:** 2 of 7 own a gutter and both give a breakpoint a column to itself; the one that also marks findings
(VS Code) puts them in the text, not the gutter. None shares one cell between the two.

## Implementation inventory

### Ours (`app/src/main/java/com/jupydroid/app/ui/editor/`)

| Concern | Files |
|---|---|
| The column's size | `LineNumbers.kt` `rememberGutterMetrics`: `breakpointColumnPx` is one text row |
| Where both glyphs are drawn | `LineNumbers.kt` `gutterDrawing` calls `drawDiagnosticMark` and then `drawBreakpointMark`, both centred at `breakpointColumnPx / 2` (`DiagnosticMarks.kt`) |
| Which target a press hits | `GutterPress.kt` `gutterPress`: `onMark && diagnosed` is `SHOW_DIAGNOSTICS`, never a toggle; `LineNumbers.kt` `detectGutterTap` passes `x <= breakpointColumnPx` as `onMark` |
| The finding's text target | `DiagnosticSquiggles.kt` draws the underline only; it takes no press |
| The card | `DiagnosticMarks.kt` `DiagnosticDetailPopup`, driven by `detailLine` in `CodeCellField.kt` (two call sites, `lineNumbers` and `GutterOverlay`) |
| Not a precedent | `HoverPopup.kt` is the "Symbol info" popup, opened by an action on the caret, not by a tap on text |
| Tests today | `GutterPressActionTest` (JVM, pins the current table), `DiagnosticMarkTapTest` (androidTest), `.maestro/diagnostic-detail.yaml` with `scripts/diagnostic-detail-driver.sh` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where a finding is read | the gutter mark kept and given a column of its own · the squiggle's text range · both | a column adds about one row of width per cell on a phone and both targets stay under 48dp; the text range costs no width and is larger, but a tap there also places the caret | **Recommendation, to be settled on T1's measurement:** the squiggle's range is the target and the gutter is the breakpoint's alone, as VS Code. If a tap on a range cannot both show the card and place the caret, a column of its own |
| Where a breakpoint is toggled | the dot's column only · the whole gutter | the whole gutter is the larger target and is what a diagnosed line's numbers already do | the whole gutter, as every gutter tap always did |

## Gap analysis

| Concern | Ours today | Best reference | Gap |
|---|---|---|---|
| One meaning per target | one cell, two meanings (`gutterPress`) | `breakpointEditorContribution.ts:55-56` | present and wrong: the overlap is code, found by a person, not yet measured |
| A target a finger can hit | about one text row wide | Material's 48dp minimum | present and partial; T1 measures it at 320, 360 and 412dp |

## Deviations

A phone has no hover, so VS Code's hover on a marker is a tap here. It becomes an ADR before this file is deleted.

## Open decisions

- Option 1 or a column of its own, settled on T1's measurement.
- If the squiggle: whether the tap also places the caret, and how a hint (three dots) is hit.

## Surface

A UI item with no domain, port or adapter.

| Layer | Files |
|---|---|
| `ui/editor` | `LineNumbers.kt`, `GutterOverlay.kt`, `GutterPress.kt`, `DiagnosticMarks.kt`, `DiagnosticSquiggles.kt`, `CodeCellField.kt` |
| unit tests | `GutterPressActionTest`, rewritten to the chosen meaning |
| instrumented tests | `DiagnosticMarkTapTest`: a line holding both, tap each, at 320, 360 and 412dp |
| `.maestro/` flow + `scripts/` driver | `diagnostic-detail.yaml` and its driver, extended with the line holding both |

## Tasks

- [ ] T1 — reproduce on a line holding both at 320, 360 and 412dp on `dev36`: the mark's and the dot's pixel rects, what
  each tap does, and what a tap on a squiggled range does to the caret; settle the choice in this file.
- [ ] T2 — red tests for the chosen meaning, then the minimal change so no two glyphs share a cell.
- [ ] T3 — run the flow on both devices at both widths, look at the frames, record the ADR.

## Out of scope

The condition editor's long press, the card's content and style, and the diagnostics' mapping from the server.
