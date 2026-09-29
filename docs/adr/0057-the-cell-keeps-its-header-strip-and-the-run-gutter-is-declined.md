# ADR-0057 — The cell keeps its header strip, and VS Code's run gutter is declined

Status: accepted
Date: 2026-08-25

## Context

With VS Code as the presentation reference
([ADR-0040](0040-the-presentation-reference-is-vs-code-behaviour-stays-jupyterlab.md)), the cell's chrome was
re-derived from it. Four changes followed from that reading:

- the run control into a gutter **outside** the cell (`width: 35px; left: -35px`);
- the execution count at that gutter's foot;
- the heading caret onto the same left indicator;
- `CellHeader` retired — the `⋮` to the cell's top-right, the duration to a foot status bar.

**All four are declined**, and unlike the first time this was written down, **the gutter was built before it
was refused.** The frames exist on an archive tag, and they are the measurement this decision rests on rather
than the arithmetic that preceded them.

## Decision drivers

- A 35px gutter outside the cell is width a phone does not have to give.
- A decision to decline a reference must rest on having tried it, or it is a preference.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Take VS Code's gutter | Presentation parity | 35px outside the cell on a screen where the cell is already the narrow dimension; built, measured, and worse |
| Keep the header strip | The controls stay inside the cell's own bounds, at 48dp targets | Departs from the presentation reference on four rows |

## Decision

**The cell keeps its header strip**, and all four gutter-derived rows are declined — on frames, not on
arithmetic.

What the re-derivation *does* change is narrower and is kept: the run control draws an **outlined** play
rather than a filled one, because filled at rest reads as "playing"; and a heading's chevron is drawn only
while that heading is **rendered**, where the reference keeps it in both states, because beside an open
source it cost 48dp of the editor's width for a fold nobody can reach mid-typing.

The kernel indicator on the action row follows the outlined-at-rest rule for the same reason, where the
reference's own filled bolt was the loudest mark in a row of outlines.

## Consequences

This is the largest deliberate departure from the presentation reference, and it is recorded with the archive
tag that holds the frames so the next person can look rather than re-derive.

The outlined-play rule now applies in two places, and a third filled glyph at rest would break the pattern
rather than merely differ.

The thing a future reader will be tempted to "fix" is the gutter, because the presentation reference has one
and this does not. It was built; the frames are the answer.
