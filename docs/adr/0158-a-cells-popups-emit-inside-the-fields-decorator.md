# ADR-0158 — A cell's popups emit inside the field's decorator

Status: accepted
Date: 2026-11-17

## Context

A finding's card covered the line it described: on the tablet at both widths its top edge sat 12px
inside the 31px line — the cell's padding — hiding the lower half of the glyphs, the squiggle and
the breakpoint's circle on that line (GAP-207's pixel read of the saved frames).

The cause was a coordinate-space boundary, not a wrong offset. `anchorBounds` was the outer
`cellEditorBox`'s bounds — which hold the cell's `Spacing.sm` padding and the gutter's start
padding — while the caret and the diagnostic's line rect come from `TextLayoutResult`, whose origin
is the inner text's. The two are one padding apart, and at density 1 that is the 8px the card
overlapped by (12px at the tablet's 1.5). An earlier repair had added a `shiftedBy` compensation at
the call sites, which meant every popup caller had to know the padding it was correcting for.

The cell emits four popups — the completion list, the finding's detail card, the hover card and the
signature-help card — and all four anchored on that padded box.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep translating each rect by the padding (`shiftedBy`) | No structure change | Every caller must know the padding it corrects for, and each new popup repeats the knowledge |
| Pass the padding in so the provider adds it | One place owns the number | `CompletionPopupPosition` learns a layout decision that is not its own, and the callers still carry it |
| **Emit the popups inside `BasicTextField`'s `decorator`** | The anchor node's origin is the text layout's own; nothing is added or subtracted anywhere | The emission moves from beside the field to inside its decorator, which is where it belongs |

## Decision

The four popups emit inside the field's `decorator`, in a `Box` beside `innerTextField()`
(`CodeCellField.kt`, the private `CellPopups`). A `Popup` anchors on the layout node it is emitted
under, and that node's origin is inside every padding the caller's modifier adds — the cell's, the
gutter's start padding and `horizontalScroll`. A caret or line rect in `TextLayoutResult`
coordinates therefore needs no term: `caretRect` returns `getCursorRect` unshifted and
`diagnosticLineRect` spans `-gutterPx..0` so the anchor's edge is the line's own edge.

The existing gate stands: the popups emit only while `layout != null`, so none draws against a zero
caret before the first measure.

## Consequences

- The card's top edge is on its line's bottom edge: `scripts/read_detail_gap.py` reads `gap = 0px`
  off the UI dumps the `diagnostic-detail` driver takes on the tablet at 1280dp and 800dp, and the
  squiggle, the full line and the breakpoint's circle stay readable in the frames.
- A popup caller never learns the cell's padding, so a later change to that padding cannot
  reintroduce the mismatch; a fifth popup written the same way is correct by construction.
- When the IME's rise resizes the window the anchor re-lays out and the `Popup` tracks it, which is
  also why the driver's two dumps are read in the same scroll: it hides the IME its dismissal
  re-raises before the closed dump.
- `PopupAnchorAsserts.kt` holds the red-first assertion on the instrumented suite: the anchor
  bounds are read against the line and caret they describe on `dev36`.
