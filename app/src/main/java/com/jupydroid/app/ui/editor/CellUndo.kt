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
private fun EditorUiState.changesNothingFrom(before: EditorUiState): Boolean = cells == before.cells &&
  outputs == before.outputs &&
  executionCounts == before.executionCounts &&
  executions == before.executions

/**
 * Records [before] as undoable, unless this state is the same document.
 *
 * Clearing the redo stack is the other half: an operation made after an undo branches the history,
 * and a redo across that branch would replay a document that no longer exists.
 */
internal fun EditorUiState.recording(before: EditorUiState): EditorUiState {
  if (changesNothingFrom(before)) return this
  val rewrote = sourcesRewrittenFrom(before)
  return copy(
    undoable = undoable + before.revision(rewrote),
    redoable = emptyList(),
    sourceRevisions = sourceRevisions.bumping(rewrote),
  )
}

/**
 * The cells this operation rewrote *and left in the notebook*, which is the one thing an undo may
 * put back over a live cell's text.
 *
 * Split is why this exists: it is the first operation whose own effect is to rewrite a surviving
 * cell, and without this its undo would take the new fragment away and leave the original holding
 * only the half below the caret - the text above it gone, with nothing left to undo. Derived from
 * the before/after diff rather than declared per action, which is Yjs's shape too: a transaction
 * records the delta it made, not the document it made it to. So find-and-replace gets this for
 * nothing and cannot forget to ask for it.
 */
private fun EditorUiState.sourcesRewrittenFrom(before: EditorUiState): Set<String> {
  val was = before.cells.associate { it.id to it.source }
  return cells.filter { it.id in was && was[it.id] != it.source }.mapTo(mutableSetOf()) { it.id }
}

/**
 * Tells each rewritten cell's field that its text changed, **without** an undo entry.
 *
 * What an edit arriving from outside this notebook needs - a workspace replace through
 * `OpenDocuments`. The reference records **one** `MultiModelEditStackElement` for the whole replace
 * (`bulkTextEdits.ts:319-330`), so a second entry in this document's own history would make one
 * command undoable twice, in two scopes that could then disagree about what the document is.
 *
 * The revision bump itself is not optional: a cell's `TextFieldState` cannot see a changed
 * `Cell.source` on its own (see [adoptRewrittenSource]).
 */
internal fun EditorUiState.adoptingRewrites(before: EditorUiState): EditorUiState =
  copy(sourceRevisions = sourceRevisions.bumping(sourcesRewrittenFrom(before)))

/** Tells each named field that its text was changed under it - see [EditorUiState.sourceRevisions]. */
private fun Map<String, Int>.bumping(ids: Set<String>): Map<String, Int> =
  if (ids.isEmpty()) this else this + ids.associateWith { (this[it] ?: 0) + 1 }

/** Takes back the last cell operation. A no-op with nothing recorded, as `NotebookActions.undo` is. */
fun EditorUiState.undoneCellOperation(): EditorUiState {
  val restoring = undoable.lastOrNull() ?: return this
  // The entry pushed for redo carries the same `rewrote` set: replaying the operation rewrites
  // exactly the cells undoing it just rewrote, in the other direction.
  return restoredTo(restoring)
    .copy(undoable = undoable.dropLast(1), redoable = redoable + revision(restoring.rewrote))
}

/** The mirror, and the only asymmetry is which stack is popped and which is pushed. */
fun EditorUiState.redoneCellOperation(): EditorUiState {
  val restoring = redoable.lastOrNull() ?: return this
  return restoredTo(restoring)
    .copy(redoable = redoable.dropLast(1), undoable = undoable + revision(restoring.rewrote))
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
 * So the record supplies the source only for the cells it is bringing back **and the ones the
 * operation itself rewrote** ([NotebookRevision.rewrote]). That second half is split's, and it is
 * an exception the rule needed rather than a hole in it: the text a split moved out of a cell
 * exists nowhere else, so an undo that kept the live source would destroy it. Everything the
 * operation did not touch keeps the source it has now, which is what the forty-lines case is
 * about.
 *
 * Outputs and counts are restored as they are: nothing outside a cell operation writes an output
 * for a cell that is not running, and a run in flight re-emits into whatever is on screen.
 *
 * Ends collapsed and clamped, which is the `deselectAll()` JupyterLab does after an undo
 * (`actions.tsx:1758`, `:1779`): the marks drop, and a head whose cell is gone takes
 * `handleState`'s index clamp ([withHeadOnIndex]). Without the collapse the bar above would go
 * on counting cells the undo has just replaced.
 */
private fun EditorUiState.restoredTo(revision: NotebookRevision): EditorUiState {
  val liveSources = cells.associate { it.id to it.source } - revision.rewrote
  // Read before the cells are swapped: the index the head sits at now, which is what it keeps
  // when the restore removes its cell - a merge's `newId` is the case the undo meets it on.
  val headAt = cells.indexOfFirst { it.id == selectedCellId }
  return copy(
    cells = revision.cells.map { cell -> liveSources[cell.id]?.let { cell.copy(source = it) } ?: cell },
    outputs = revision.outputs,
    executionCounts = revision.executionCounts,
    // The run comes back with the results it produced: a restored output over a cell with no
    // duration and no stripe is half a restoration - see [NotebookRevision].
    executions = revision.executions,
    sourceRevisions = sourceRevisions.bumping(revision.rewrote),
    dirty = true,
  ).withSelectionCollapsed().withSelectionClamped().withHeadOnIndex(headAt)
}
