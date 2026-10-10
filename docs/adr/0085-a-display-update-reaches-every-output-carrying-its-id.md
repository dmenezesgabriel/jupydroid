# ADR-0085 — A display update reaches every output carrying its id

Status: accepted
Date: 2026-10-09

## Context

`display(x, display_id=…)` shows an output the kernel can later redraw: `handle.update(y)`, or
`update_display(y, display_id=…)`, sends `update_display_data` with the same `transient.display_id`
(the shipped rootfs's `usr/lib/python3.12/site-packages/ipykernel/zmqshell.py`, `publish`). Every
live display rests on it: `tqdm.notebook`, a progress handle, an animation that redraws one frame.

This app read `update_display_data` as one more `display_data` and appended it under the cell that
was running. So `h.update()` from cell 2 left cell 1 on its first frame and stacked every later
frame under cell 2.

The two notebook front ends both implement the message, and they disagree on how far an update
reaches:

- **JupyterLab** keeps a map from display id to output indices per output area
  (jupyterlab@7f0e690 `packages/outputarea/src/widget.ts`, `_displayIdMap`, shifted on removal by
  `_moveDisplayIdIndices`). The kernel connection re-routes an update to every earlier request that
  showed the id, but only while that request's future lives
  (`packages/services/src/kernel/default.ts`, `_handleDisplayId`). So an update from a later cell
  reaches a finished cell only by accident of lifetime.
- **vscode-jupyter** keeps one id→output map per notebook, for the notebook's life, and drops a
  cell's entries when its outputs are cleared
  (vscode-jupyter@f01d80ca0 `src/kernels/execution/cellDisplayIdTracker.ts`).

Both drop `transient` on save (jupyterlab@7f0e690 `packages/rendermime/src/outputmodel.ts`, `toJSON`),
so a reopened notebook's displays can no longer be targeted.

## Decision drivers

- `h.update()` from any later cell redraws the cell that showed `h`, which is what the user wrote.
- Outputs here are immutable lists in `EditorUiState`, copied and restored whole by undo, clear and
  paste.
- A notebook saved here is the file JupyterLab would save from the same run.

## Options considered

| Option | Pro | Con |
|---|---|---|
| **The id on the output, the target found by a scan** | Travels through undo, clear and paste with no bookkeeping | Each update visits every output of the notebook |
| A side map of indices (JupyterLab) | Finds the target directly | Must be shifted on every removal, clear, undo and paste, or it points at the wrong output |
| A side map of output objects (vscode-jupyter) | No index shifting | Our outputs are values, not objects with identity; the map would hold stale copies |

## Decision

**The id rides on the output, and an update reaches every output carrying it across the whole
notebook.**

- `CellOutput.DisplayData.displayId` holds `transient.display_id`
  (`app/src/main/java/com/jupydroid/app/domain/model/CellOutput.kt`). It is never written to the file.
- `update_display_data` arrives as `CellOutput.DisplayUpdate`, an instruction like `ClearOutput`: the
  reducer spends it and it never lands in a cell's outputs. An update with no id is dropped at the
  port (`app/src/main/java/com/jupydroid/app/data/space/KernelOutputContent.kt`).
- An update replaces each matching `DisplayData` whole, `metadata` included, as JupyterLab's
  `model.set` does (`withDisplayReplaced`, `app/src/main/java/com/jupydroid/app/domain/model/DisplayUpdate.kt`).
- A `display_data` carrying an id already shown updates the earlier outputs, then is appended, as
  JupyterLab's `_onIOPub` does.
- An update does not spend a pending `clear_output(wait=True)`; only an output that is *added* does,
  as JupyterLab's `set` leaves `_clearNext` alone
  (`EditorUiStateTest.anUpdateLeavesAPendingClearForTheNextOutput`).
- The scope is vscode-jupyter's, the whole notebook, not JupyterLab's request lifetime.

## Consequences

`h.update()` from any cell redraws the cell that showed `h`, in place, and the calling cell shows
nothing. A list an update does not touch is returned as it is, so untouched cells keep their identity
for Compose and for the undo stack.

An update costs a scan of every output in the notebook. A notebook has tens of outputs; a `tqdm` bar
sends a few updates a second.

What a future reader will be tempted to "fix":

- **An index map for speed.** It has to be shifted by every clear, undo, paste and delete, and one
  that is not points an update at the wrong output.
- **Saving the id so a reopened notebook can still be updated.** No other front end writes
  `transient`, and the kernel that issued the id is gone after a relaunch anyway.

Not covered:

- An update the kernel sends after every request that could carry it has ended (a background thread
  updating a finished cell): iopub is filtered by parent `msg_id`, so it never reaches the reducer.
  JupyterLab drops it too once the future is disposed.
- Clearing a cell drops its outputs and their ids with them, so a later update to that id finds no
  target and is a no-op, as in vscode-jupyter.
