# ADR-0153 — A finding is read from its squiggle, and the gutter is the breakpoint's alone

Status: accepted
Date: 2026-11-15

## Context

A code cell's gutter drew a breakpoint's circle and a finding's severity icon in the same cell, both centred in
one text row (18dp), and its tap table gave that one cell two meanings: a tap at the mark showed the finding's
card and toggled nothing, and a tap beside it toggled the breakpoint. On a line holding both, measured on `dev36`
at 320, 360 and 412dp, the circle (14.4dp) sat inside the icon (16.2dp) and hid it. A tap meant to remove the
breakpoint opened the card and left the breakpoint standing, and a tap meant to read the card set one.

The references that own a gutter give a breakpoint a column to itself, and the one that also marks findings puts
them in the text:

| Reference | Breakpoint | Finding |
|---|---|---|
| `vscode@3f3380c6` | the glyph margin (`src/vs/workbench/contrib/debug/browser/breakpointEditorContribution.ts:55-56`) | a squiggle in the text, its message on hover (`src/vs/editor/contrib/hover/browser/markerHoverParticipant.ts`); the markers contribution sets no glyph-margin decoration |
| `jupyterlab@7f0e6906` | a gutter of its own, `cm-breakpoint-gutter` (`packages/debugger/src/handlers/editor.ts:228-236`) | not drawn by the debugger |
| `intellij-community@406a27a8` | not read | a tooltip on the error stripe and the text range |

Two of seven references own a gutter, both give a breakpoint a column to itself, and none shares one cell between
the two meanings.

## Decision drivers

- Each target has one meaning, so a tap does one thing.
- A finger hits it: Material's 48dp minimum, against a row 18dp tall.
- No width added to every cell.
- A phone has no hover.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Keep the gutter mark and give it a column of its own | Both glyphs stay where they were | 18dp more on every cell, 44dp to 62dp, and both targets stay 18dp |
| **The squiggle's range is the target for a finding** | No width; a range is wider than the cell; VS Code's shape | A tap there also places the caret, and a hint's range is two characters (16.5dp) |
| Both targets | A finding is reachable twice | Two meanings stay in the gutter's neighbourhood |

## Decision

**The gutter is the breakpoint's alone.** It draws no finding's icon, and `gutterPress` takes two inputs, `armed`
and `held`, and has two results: a long press on an armed line edits its condition and any other press toggles
(`ui/editor/GutterPress.kt`). The whole gutter toggles, as every gutter tap always did.

**A finding is read from its squiggle.** A tap on a squiggled range opens the findings card for that line, which is
VS Code's marker hover performed as a tap, since a phone has no hover (`ui/editor/DiagnosticTaps.kt`). The tap is
observed at `PointerEventPass.Initial` and never consumed, so the field's own tap still places the caret and
reading a finding does not cost the position the finger chose. A press that moves past the touch slop is a scroll
and one held past the long-press timeout is a selection; neither reads a finding.

**A span narrower than 48dp is widened sideways to 48dp, for every span.** A hint's span is two characters. A row
is 18dp tall, so widening the other way would take the next line's taps; one rule, not one for hints.

## Consequences

Read on the arm64 tablet at 1280dp (two panes) and 800dp (one pane) with a real `pylsp`, and on `dev36` at 320, 360
and 412dp by `DiagnosticMarkTapTest`. On a line holding both, one circle is drawn in the gutter's cell; a tap there
removes it and opens no card, a tap on the squiggle opens the card and leaves the circle, and a line with only one of
the two still does that one thing.

**A tap places the caret only once the field holds focus.** Read while testing `DiagnosticMarkTapTest`, so its
tests focus the field first. It is the field's own behaviour, not this decision's.

**The card is up before the field's own tap lands.** A BACK sent between the two goes to the soft keyboard, not to
the card; a flow that dismisses the card taps outside it instead.

Anyone tempted to put the finding's icon back in the gutter to make it findable should read the first option: the
cell was 18dp wide and the two glyphs each lost a target to the other.
