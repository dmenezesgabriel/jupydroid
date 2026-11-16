# JD-091 — A finding's card does not cover the line it describes

Milestone: `M11` · State: open · Depends on: — · Closes: GAP-207

## Outcome

With a finding's card open on the tablet, the line it describes, that line's breakpoint circle and its
squiggle are fully visible at 1280dp and at 800dp. Today the card's top edge is 12px above the line's bottom
edge in both widths, so it covers the lower 12px of a 31px line: the lower part of the glyphs, the squiggle
and the lower half of the breakpoint's circle.

## What was measured while writing this

Read from the frames the earlier tablet demo of `diagnostic-detail` saved (kept locally, never committed),
by the pixel rows of one column, at density 240 (`wm density`, 1.5 px/dp) on `SM-X216B`:

| Width | Cell box (blue border) | Card | Card top below box top | Card height |
|---|---|---|---|---|
| 800dp | rows 472–527 (55px) | rows 503–608 | 31px | 105px |
| 1280dp | top at row 451 (bottom not read) | rows 482–587 | 31px | 105px |

**`GAP-207` took the cell's box for the line.** Its "53px line" is the outlined box. The box holds
`Spacing.sm` (8dp = 12px) of padding on every side (`cellEditorBox`, `CellEditorBox.kt`), so the text line
inside it is 55 − 24 = 31px, from row 484 to 515. The card's top is at 503, which is **12px above the line's
bottom** — exactly the padding, not 45px. A card that starts at the line's bottom (515) clears the line, the
squiggle and the circle.

**The cause, as far as the code says (T1 proves it):** `DiagnosticDetailPopup` hands
`CompletionPopupPosition` the line's rect, taken from the text layout (`diagnosticLineRect`,
`DiagnosticMarks.kt`), whose origin is the text's own top-left. The provider adds it to `anchorBounds`, the
bounds of the composable the `Popup` sits in: the `Box` in `CodeCellField.kt`, which wraps a
`BasicTextField` that wears the caller's `Modifier.fillMaxWidth().cellEditorBox(editing)` (`EditorCell.kt`),
so the padding lies between that bounds' origin and the text's. Rects are in one coordinate space and
`anchorBounds` in another. 12px above equals the padding on the tablet, and the same argument says the card is
12px too far left.

**Not a defect of the card alone:** `CompletionPopup`, `HoverPopup` and `SignatureHelpPopup` anchor on
`caretRect` through the same provider and the same `Box`, so each is expected to start 12px high and 12px left
of the caret too. T1 reads that rather than assuming it.

**What a correct card still covers:** the next cell's header. A card of 105px below a 31px line reaches about
80px past the cell's box, over the next cell's header row, and no placement under the line can avoid that. The
backlog row asks for "clear of the line and the breakpoint beside it", which is what this item delivers; the
header is covered by any popup below its anchor, VS Code's hover and IntelliJ's hint included.

## Demo

Runs a cell to open a session, so it goes on the arm64 tablet (`SM-X216B`). The agent runs it and says so.

1. Run `scripts/diagnostic-detail-driver.sh` at 1280dp landscape, then 800dp portrait (it does both and puts
   the rotation back). It opens `Diagnostics.ipynb`, runs the warm-up cell so `pylsp` publishes, then taps the
   squiggle under `never_defined_lsp_name`.
2. Frames `02` and `05` (the second on the line that also holds a breakpoint) show the card's top at or below
   the line's bottom, the whole line readable and the circle whole. The driver prints the measured gap in px
   from a UI dump, so the claim is a number a reader can rerun, and the frame is looked at as well.
3. Frame `07` (a warning, two characters wide) shows the same for a second severity.

Not `shipped` until this has run on the tablet at both widths, its frames, dumps and `maestro.log` kept in
the local evidence directory (ADR-0132).

## Who else implements this

| Reference | Has it | Entry points | Approach in one line |
|---|---|---|---|
| `jupyterlab` | PARTIAL | `jupyterlab@7f0e6906 packages/codemirror/src/extension.ts` | Names `@codemirror/lint` in its translations (`Diagnostics`, l.1089); the tooltip's placement lives in that package, which the clone does not hold, so its rule is **not read** |
| `vscode` | FULL | `vscode@3f3380c6 src/vs/editor/contrib/hover/browser/contentHoverWidget.ts` | A content widget at the marker's position with an ABOVE or BELOW preference, ABOVE first, fallen back when there is no room (`getPosition` l.324, `show` l.336) |
| `vscode-jupyter` | ABSENT | — | A notebook cell is a VS Code editor, so the hover is VS Code's; `vscode-jupyter@f01d80ca` draws no marker card of its own |
| `intellij-community` | FULL | `intellij-community@406a27a8 platform/platform-impl/src/com/intellij/codeInsight/hint/HintManagerImpl.java` | `UNDER` adds `editor.getLineHeight()` to the position's own y; `ABOVE` subtracts the hint's height; each falls back to the other when it does not fit (`getHintPosition` l.474, `_getHintPosition` l.512) |
| `termux-app` | ABSENT | — | A terminal; no diagnostic, marker or popup-position code in its Java or Kotlin |
| `winlator` | ABSENT | — | An emulator front end; the same search finds none |
| the shipped rootfs | ABSENT | — | Ships no editor; the card is drawn by the app |

**Count:** 2 of 7 implement it and read (VS Code, IntelliJ), 1 not read. Both place the card against the
**line's edge in the editor's own coordinates**, so the card can never start inside its line, and both flip
between above and below by room. Ours already flips by room; it is the coordinate space that is wrong.

## Implementation inventory

### ours

| Concern | Files |
|---|---|
| The card | `app/src/main/java/com/jupydroid/app/ui/editor/DiagnosticMarks.kt` (`DiagnosticDetailPopup`, `diagnosticLineRect`) |
| Placement | `app/src/main/java/com/jupydroid/app/ui/editor/CompletionPopupPosition.kt` (shared with the completion, hover and signature-help popups) |
| Anchor | `app/src/main/java/com/jupydroid/app/ui/editor/CodeCellField.kt` (the `Box`, `caretRect`, the `anchor` of the detail line) |
| Padding it must not know about | `app/src/main/java/com/jupydroid/app/ui/editor/CellEditorBox.kt`, `EditorCell.kt` (`modifier = Modifier.fillMaxWidth().cellEditorBox(editing)`); two more callers: `ConsoleInputRow.kt`, `FileEditorScreen.kt` and `SpaceSettingsDialog.kt` also call `CodeCellField` with their own modifiers |
| Tests that already exist | `CompletionPopupPositionTest` (JVM), `DiagnosticMarkTapTest` (instrumented, `getUnclippedBoundsInRoot`), `CellEditorBoxTest` (instrumented) |
| Flow and driver | `.maestro/diagnostic-detail.yaml`, `scripts/diagnostic-detail-driver.sh` |

## Trade-offs and what we adopt

| Axis | Options seen | Trade-off | Our choice, and why |
|---|---|---|---|
| Where the coordinate space is corrected | (a) put the popups in a node whose bounds are the text's own, so `anchorBounds` and the rects agree · (b) give `CompletionPopupPosition` the text's origin offset · (c) shift each rect by the padding at each of four call sites | (a) fixes the cause once and no caller learns the padding; (b) makes the provider know about padding and every caller pass it; (c) repeats a constant four times and the next popup forgets it | **(a) if the parent's bounds can be the text's, otherwise (b).** T1 reads which node `anchorBounds` is, and T2 chooses on that. Not (c): a patch at the symptom |
| Above or below the line | VS Code: above first. IntelliJ: caller's choice, `UNDER` for a hint | Above keeps the code below readable and covers the previous cell's output; below is what the card does today and what the flows' frames show | **Unchanged: below, flipping above when it does not fit.** Changing the preference is a separate UX decision and needs the heuristic named; this item repairs the overlap |

## Gap analysis

| Concern | Ours today | Best reference | Gap |
|---|---|---|---|
| Card against its line | starts 12px (the padding) inside the line's bottom | `HintManagerImpl.java` `UNDER`: line height added to the line's own y | present and wrong in both axes |
| The same for the other three popups | not read | same | **not known** until T1 |

## Deviations

None.

## Open decisions

- Whether `anchorBounds` is the box's outer bounds (T1 reads it with `getUnclippedBoundsInRoot` and a test of
  the card's top against the line's bottom). If it is the inner bounds, this PRD's cause is wrong and the item
  starts again from the measurement.
- Whether the three other popups share the offset. If they do, the repair covers them and their tests assert it;
  if not, the item stays on the card.

## Surface

| Layer | Files |
|---|---|
| `ui/editor` | `CodeCellField.kt`, and `CompletionPopupPosition.kt` or `DiagnosticMarks.kt` per T1 |
| unit tests | `CompletionPopupPositionTest` |
| instrumented tests | `DiagnosticMarkTapTest`: the card's top against the line's bottom in a cell wearing `cellEditorBox` |
| `.maestro/` flow + `scripts/` driver | `diagnostic-detail.yaml`, `diagnostic-detail-driver.sh` printing the gap, and its `_test.py` |

No new port or adapter: this is a layout fault in the UI layer.

## Tasks

- [ ] T1 — Prove the cause, red first: an instrumented test that opens the card on a line in a cell wearing
      `cellEditorBox` and asserts the card's top is at or below the line's bottom, and the same for the
      completion popup against its caret. It fails by the padding. It also reads which node `anchorBounds` is.
- [ ] T2 — Correct the coordinate space where T1 found it, green, so the four popups agree with their rects; no
      call site learns the padding.
- [ ] T3 — The driver prints the card-to-line gap from a UI dump (tested), runs on the tablet at both widths,
      the frames are looked at, and the item ships: PRD deleted, an ADR if the anchor rule is durable, `GAP-207`
      and the backlog row edited.

## Out of scope

The card's preference for above or below, the completion list's length, and the flow gaps in `GAP-208` and
`GAP-209`.
