# ADR-0028 — There are two undos, and a cell operation never restores text

Status: accepted
Date: 2026-08-15

## Context

The reference declares **four** commands, not two: `notebook:undo-cell-action` / `redo-cell-action`
reach `NotebookActions.undo/redo` and unwind the *document's* stack, while `notebook:undo` /
`notebook:redo` reach `cell.editor?.undo()` and unwind the *editor's*.

The second pair is already ours for free, because Compose's `TextFieldState` carries its own history
exactly as CodeMirror carries the reference's. Only the document stack had to be built.

## Decision drivers

- Undoing a structural change must not silently discard unrelated typing.
- A control that lights up and visibly does nothing teaches the user that controls lie.

## Options considered

| Option | Pro | Con |
|---|---|---|
| One undo stack over the whole document, text included | One concept | "Delete a cell, type forty lines into another, undo" **loses the forty lines**: a whole-document record cannot tell a cell it is *restoring* from one it would be *reverting* |
| Two stacks, as the reference has | Structure and text unwind independently, which is what the user means | Two concepts, and the boundary between them has to be exact |

## Decision

**An undo restores structure and keeps the source of every cell that is still there.** The record
supplies a source only for the cells it is bringing back. Measured on both devices with `TYPED`
standing in for the forty lines.

**The undoable unit is one cell operation**, which is `CellEdits.applying()` — the same boundary as
the reference's `sharedModel.transact { }`, and why `deleteCells` creates its replacement cell
*inside* the transaction.

**Which operations count needs no list.** An entry is recorded when the document actually changed,
which is Yjs's own rule and covers `Copy` (clipboard only) and every stale-id no-op for free. The
stack starts empty per opened document, as `Context.initialize()`'s `clearUndoHistory()` does.

**One adaptation, and it is Clear Outputs.** In the reference, `clearExecution` deletes the
`execution` metadata — tracked, so an entry exists — while the outputs themselves are not tracked at
all (*"output changes are not tracked by the undo manager"*), so its `Z` restores nothing a user can
see. That is Yjs scoping rather than a decision. Here the entry restores the outputs and the count
with them, because on a phone "Clear outputs" sits one row above "Delete" in a thirteen-row sheet.

**Undo is a document action, so it lives on the app bar** — not on `NotebookBar`, which is what you
ask the *kernel*. The reference files it under Edit and the notebook's own context menu, never the
notebook toolbar, and binds `Z` in command mode only. The icons are Material's
`AutoMirrored.Undo`, whose path is what `ui-components`' own `undo.svg` draws.

## Consequences

The "no text restore" rule is the load-bearing one and it is not obvious. Anything that adds a source
to a record for a cell that was never removed reintroduces the forty-line loss.

Clear Outputs is the one place this app's undo does *more* than the reference's, and the reason is a
phone's sheet layout rather than a disagreement about semantics.

The thing a future reader will be tempted to "fix" is the absence of an explicit list of undoable
operations. The "did the document change" rule is what makes `Copy` and every no-op correct without
anyone maintaining a list.
