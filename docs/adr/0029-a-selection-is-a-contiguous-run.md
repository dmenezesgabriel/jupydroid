# ADR-0029 — A selection is a contiguous run, and it is the operand of almost every cell command

Status: accepted
Date: 2026-08-15

## Context

Read out of `packages/notebook/src/widget.ts` rather than designed: `extendContiguousSelectionTo` is
*"move the head of an existing contiguous selection"*, `getContiguousSelection()` **throws** on
anything else, and shift-click and shift-drag — the only two multi-select gestures the reference has —
both go through it. There is no ctrl-click and no discontiguous selection to model.

So the whole feature is two ends: an **anchor** and a **head**, with the cells between them derived
rather than stored.

**And the operand of almost every cell command is that run, not the active cell.** Merge, move,
cut/copy, paste, duplicate, clear outputs, change type, delete and every collapse all iterate
`isSelectedOrActive`. `_updateSelectedCells` includes the active cell in that, which is what makes
single-cell behaviour the **degenerate case** of the run rather than a second path beside it.

## Decision drivers

- One code path for one cell and for many, or the two will drift.
- A touch surface has no shift-click, so the gestures have to be re-derived without changing the model.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Model a set of selected cells | More general | The reference throws on anything non-contiguous; generality here is a shape nothing can produce and nothing consumes |
| Anchor and head, cells between derived | Matches the reference exactly; single-cell falls out for free | The invariant "the head is the active cell" must hold everywhere, and the reference has one call site that breaks it |

## Decision

A selection is an **anchor and a head**. Six rules follow, each read out of the reference:

- **Extending back onto the anchor ends the selection outright** rather than leaving that one cell
  marked — *"Collapse the selection if it is only the active cell"* → `deselectAll()`. Material says
  the same about clearing the last item, so reference and platform agree and nothing was adapted.
- **Entering edit mode drops the run** — *"Edit mode deselects all cells"*. That one line is what lets
  a tap into a cell keep meaning "type in this one" while a run is marked.
- **Three operations keep the run and every other one clears it.** `move`, `clearOutputs` and the
  collapses are the only cell operations with no `deselectAll()`, because after each the same cells
  are still there and still worth acting on together. Undo is on the clearing side.
- **Merge ignores its direction once more than one cell is marked**, because `mergeAbove` is consulted
  only inside `if (toMerge.length === 1)`.
- **Duplicate lands after the *last* cell of the run; paste lands below the head.**
  `notebook:duplicate-below` passes `'belowSelected'` and `pasteBelow` passes `'below'`. The difference
  is what keeps copies out of the middle of a run whose head is at its top.
- **The collapses set rather than toggle.** `hideCode` and `showCode` are separate commands over the
  selection, so a run comes out uniform; toggling each cell against its own state would invert a mixed
  run and leave it exactly as mixed.

**One departure, and it keeps the reference's own invariant against a call site that breaks it.**
`selectAll` marks every widget and leaves `activeCellIndex` alone; with the active cell in the middle,
the next `extendSelectionAbove` reaches `getContiguousSelection()`, which throws *"Active cell not at
endpoint of selection"* with nothing catching it. The same file states the rule that call site
breaks — *"The head of a contiguous selection is always the active cell"* — so here the head goes on
the last cell.

**The touch adaptations, each naming what it departs from.** A long press is `BasicTextField`'s text
selection and is not available, so the way in is a row in the cell menu; the way out is a contextual
bar replacing the notebook's own, which is Material's rule and also the reference's own split under
another name — its selection commands live in the Edit menu and the notebook's context menu, never on
the cell. The cell's `⋮` is hidden while that bar is up, so there is never a question of what a menu
applies to.

Two things were **measured rather than chosen**: four reach arrows do not fit — at 360dp a navigation
icon plus four actions is 240dp of 360, and the count wrapped to three lines reading `3 / select / ed`,
so all four `extend-marked-cells-*` are overflow rows — and the tint is the reference's own,
`.jp-Cell.jp-mod-selected` being a cell *background*, `#e3f2fd` light and `rgba(33,150,243,.24)` dark,
applied only once the run holds more than one cell, since `jp-mod-multiSelected` is added at
`count > 1`.

**Labels pluralise where the reference's `trans._n` does and nowhere else** — `Delete` / `Delete cells`,
`Cut` / `Cut cells` — while `Clear outputs` and `Collapse code` keep one form, as `'Clear Cell Output'`
and `'Collapse Selected Code'` do. With a run marked the two directional merge rows become one, because
both would do the same thing; the replacement is the reference's own third command,
`notebook:merge-cells`, labelled *"Merge Selected Cells"*.

## Consequences

Single-cell behaviour is the degenerate case, so there is **no separate single-cell path to keep in
step**. Any new command must take the run as its operand or it will behave differently from its nine
neighbours.

The head-is-active-cell invariant is the thing most likely to break next. The reference violates it in
one place and pays with an uncaught throw; we hold it, which means every new way of changing a
selection must place the head at an endpoint.

The thing a future reader will be tempted to "fix" is the collapses setting rather than toggling, which
reads as a missing feature. Toggling a mixed run leaves it exactly as mixed.
