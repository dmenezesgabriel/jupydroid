# ADR-0027 — Cell semantics come from `NotebookActions`, not from what looks reasonable

Status: accepted
Date: 2026-08-14

## Context

Every cell operation has an obvious answer and a correct one, and they differ more often than not.
Read out of `packages/notebook/src/actions.tsx` and `packages/cells/src/model.ts`:

- Deleting the **last** cell leaves an empty cell, not an empty notebook — of
  `notebookConfig.defaultCell`, not a hardcoded `"code"` — and the replacement is inserted **inside
  the same transaction**, *"to make the deletion of a notebook's last cell undoable"*. A cell with
  `deletable: false` is skipped, and deleting a collapsed heading takes its children.
- Clearing outputs also clears the **execution count** — `clearExecution` sets
  `executionCount = null`.
- A **duplicate keeps** the original's outputs and count; only its id is renewed. The one exception
  is deliberate: a code cell pasted after a **cut** keeps its id.
- **Splitting keeps the outputs on the *last* fragment**, not the first: the original cell object
  becomes the final piece *"to preserve kernel connection"*, and every new cell is created with
  `outputs: undefined`.
- **The undoable unit is one `sharedModel.transact { }` block**, over a Yjs `undoManager`. That is
  *why* `deleteCells` inserts its replacement inside the transaction rather than after it.

Mime-bundle preference order is JupyterLab's `defaultRank`, not taste.

## Decision drivers

- Parity is the product. A notebook app that behaves nearly like JupyterLab is a different app.
- A rule guessed at is a rule that will be re-guessed differently next time.

## Options considered

| Option | Pro | Con |
|---|---|---|
| Implement what seems reasonable per operation | Fast | Five of the six rules above contradict the obvious answer. The result diverges invisibly and unevenly |
| Read `actions.tsx` for every operation and port it | Behaviour matches, and each departure is a stated choice | Slower, and the reasoning has to be recorded or it is lost |

## Decision

**Every cell operation is read out of `NotebookActions` before it is written**, and any departure is
stated where it is made. The list above is the set of rules that are *not* what a reasonable
implementer would have chosen, so they are the ones most at risk of being "fixed".

## Consequences

The degenerate single-cell case falls out of the run-based rule rather than being a second code path
([ADR-0029](0029-a-selection-is-a-contiguous-run.md)).

A future operation added without reading the reference will probably be wrong in a way no test here
catches, because the tests encode the reference's answers and a new operation brings none.

The thing a future reader will be tempted to "fix" is splitting keeping its outputs on the *last*
fragment, which reads backwards. It is what preserves the cell's identity — and therefore its
outputs, counts and kernel association, all keyed by id.
