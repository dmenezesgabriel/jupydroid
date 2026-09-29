# ADR-0030 — A run has a range, and the command rather than the range owns the caret

Status: accepted
Date: 2026-08-16

## Context

Read out of `packages/notebook/src/actions.tsx` and `notebook-extension/src/index.ts`. Four of these
contradict the obvious answer.

- **`run-all-above` excludes the head and `run-all-below` includes it**: `slice(0, activeCellIndex)`
  against `slice(activeCellIndex)`. Nothing about the two names says so, and one cell is the whole
  difference between them.
- **Every `run*` command deselects, and every one but `above` moves the head to the last cell it
  ran.** Above is exempt in the reference's own words — *"The currently active cell will remain
  selected"*.
- **The compound restarts do neither**, because they are built on the *public*
  `NotebookActions.runCells` rather than on a `run*` action. So the same cells run with a different
  effect on the caret depending on which command asked.
- **`run-all-below` is refused on the last cell of a multi-cell notebook** even though its range is
  non-empty, and **allowed on the only cell of a one-cell one**. With nothing below it, *"Run Selected
  Cell and All Below"* is the cell's own Run under a longer name.
- **A restart's second half runs after the restart, and runs even when there was no kernel.** The
  first is the difference between a result and a lie — cells executed first would answer from the
  interpreter about to be destroyed. The second is `shutdown(then:)`'s rule: with nothing to replace,
  there is nothing to wait for.

## Decision drivers

- Two commands that run the same cells may still differ, and the difference must live somewhere
  findable.
- A compound command must not produce answers from a kernel that is about to be destroyed.

## Options considered

| Option | Pro | Con |
|---|---|---|
| One `run(range)` that also normalises the caret | One place | Wrong for the compound restarts, which deliberately leave the caret alone; the caret effect is the command's, not the range's |
| Range and caret effect both on the command | Nothing shared | Five commands re-implement the same range arithmetic |
| `run(range)` carries the caret effect; `runCells(cells)` does not | Matches the reference's own split between an action and the public API | Two entry points, and a caller has to know which it wants |

## Decision

The range is computed once; **the caret effect belongs to the command**. `run(range)` applies the
deselect-and-move that every `run*` action does, and `runCells(cells)` does not — which is exactly the
reference's split between its `run*` actions and the public `NotebookActions.runCells` the compound
restarts are built on.

`run-all-above` keeps its exemption, and the last-cell refusal is kept as the reference states it
including the one-cell exception.

A restart's second half runs **after** the restart completes, and runs even when there was no kernel to
restart.

## Consequences

Five commands share one piece of range arithmetic and differ only where the reference differs, so a
new run command is a range plus a choice of entry point.

The `run-all-below` refusal looks like an off-by-one and is not: it is `index.ts`'s own `isEnabled`,
including the case where a one-cell notebook is allowed.

The thing a future reader will be tempted to "fix" is `runCells` not moving the caret, which looks like
a missing line. Adding it would make the compound restarts move a caret the reference deliberately
leaves alone.
