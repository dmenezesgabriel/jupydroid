package com.jupydroid.app.ui.editor

/**
 * Taking a cell operation back, as pure functions over [EditorUiState].
 *
 * Beside [CellEdits] and [CellRestructure] and under the same rules. This is
 * `notebook:undo-cell-action` / `notebook:redo-cell-action` and **not** `notebook:undo` - the
 * reference declares all four (`packages/notebook-extension/src/index.ts:280-286`) because a
 * document's structure and a cell's text are two histories. The text one is already ours: Compose's
 * `TextFieldState` carries its own, exactly as CodeMirror carries the reference's.
 *
 * The stack starts empty for each opened document, which is the reference's lifetime too -
 * `Context.initialize()` calls `clearUndoHistory()` once the model is loaded
 * (`packages/docregistry/src/context.ts:257-263`). Ours dies with the ViewModel.
 */

/**
 * Whether a transform left the document alone, which is what decides if it is worth an entry.
 *
 * Yjs's own rule - a transaction that changes nothing produces no stack item - and taking it
 * literally is what keeps a table of "which actions are undoable" out of this file. It covers
 * three cases at once: `Copy` fills the clipboard and touches no cell, a menu item tapped after
 * its cell went away is a no-op throughout [CellEdits], and toggling a flag to the value it
 * already had changes nothing either.
 */
private fun EditorUiState.changesNothingFrom(before: EditorUiState): Boolean =
  cells == before.cells && outputs == before.outputs && executionCounts == before.executionCounts

/**
 * Records [before] as undoable, unless this state is the same document.
 *
 * Clearing the redo stack is the other half: an operation made after an undo branches the history,
 * and a redo across that branch would replay a document that no longer exists.
 */
internal fun EditorUiState.recording(before: EditorUiState): EditorUiState =
  if (changesNothingFrom(before)) this else copy(undoable = undoable + before.revision(), redoable = emptyList())

/** Takes back the last cell operation. A no-op with nothing recorded, as `NotebookActions.undo` is. */
fun EditorUiState.undoneCellOperation(): EditorUiState {
  val restoring = undoable.lastOrNull() ?: return this
  return restoredTo(restoring).copy(undoable = undoable.dropLast(1), redoable = redoable + revision())
}

/** The mirror, and the only asymmetry is which stack is popped and which is pushed. */
fun EditorUiState.redoneCellOperation(): EditorUiState {
  val restoring = redoable.lastOrNull() ?: return this
  return restoredTo(restoring).copy(redoable = redoable.dropLast(1), undoable = undoable + revision())
}

/**
 * Puts the document back to [revision], **keeping the source of every cell that is still there**.
 *
 * That rule is the whole reason the two undos stay apart. `notebook:undo` runs `cell.editor?.undo()`
 * and never touches the document's stack (`packages/notebook-extension/src/index.ts:4530-4541`), so
 * a cell operation must not carry text back with it. Restoring whole cells wholesale would: delete
 * a cell, type forty lines into another one, undo, and the forty lines are gone - because a
 * document-wide record cannot tell a cell it is *restoring* from one it would be *reverting*.
 *
 * So the record supplies the source only for the cells it is bringing back. Outputs and counts are
 * restored as they are: nothing outside a cell operation writes an output for a cell that is not
 * running, and a run in flight re-emits into whatever is on screen.
 *
 * Ends clamped, which is the id-based form of the `deselectAll()` JupyterLab does after an undo.
 */
private fun EditorUiState.restoredTo(revision: NotebookRevision): EditorUiState {
  val liveSources = cells.associate { it.id to it.source }
  return copy(
    cells = revision.cells.map { cell -> liveSources[cell.id]?.let { cell.copy(source = it) } ?: cell },
    outputs = revision.outputs,
    executionCounts = revision.executionCounts,
    dirty = true,
  ).withSelectionClamped()
}
