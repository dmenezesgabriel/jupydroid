package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.notebookLeafOf
import com.jupydroid.app.domain.port.KernelEvent

data class EditorUiState(
  /**
   * Where the notebook lives now, which a rename changes - since X1b the file *is* the name.
   *
   * Carried explicitly rather than inferred from [title], which is only the last segment of it -
   * a notebook in a folder has a path the name cannot reconstruct. It is what tells the notebook
   * list which row is the open one, the way `_contextsForPath` filters live contexts by their
   * *current* path rather than by the path a widget was opened under.
   */
  val notebookId: String = "",
  /**
   * What the notebook is *called* - the last segment of [notebookId], not the whole path.
   *
   * `DocumentWidget` labels itself the same way: `PathExt.basename(sender.localPath)`
   * (`packages/docregistry/src/default.ts:618-621`). A rename is given this and the folder is
   * joined back on, so a name never has to carry one.
   */
  val title: String = "",
  val cells: List<Cell> = emptyList(),
  val loaded: Boolean = false,
  val error: String? = null,
  /**
   * The name a rename asked for and could not have, or null when nothing is pending.
   *
   * Non-null puts the reference's question on screen - `"%1" already exists, overwrite?` - and it
   * is set **only** for a name already taken. `renameFile` rethrows anything that is not a 409,
   * so an invalid name is never offered an overwrite; it is simply not a name.
   */
  val nameTaken: String? = null,
  /**
   * The file behind this notebook was deleted, so the screen showing it should close.
   *
   * `DocumentManager.deleteFile` closes every widget open on the path (`manager.ts:351-363`).
   * Ours is reported rather than commanded, because the delete can come from a cell as easily as
   * from the browser, and only the repository sees both.
   */
  val gone: Boolean = false,
  /**
   * Held here rather than on [Cell] while a run is in flight: the cell list is what gets
   * written to disk, and half a run's output has no business being saved.
   */
  val outputs: Map<String, List<CellOutput>> = emptyMap(),
  val executionCounts: Map<String, Int> = emptyMap(),
  /**
   * Per cell, where its run got to. Replaces a single nullable `runningCellId`, which could say
   * only "this one is running" or "none is" - a queued cell, a skipped one and one nobody had
   * ever run were all the same absence. `runningCellId` is derived from this now.
   */
  val executions: Map<String, CellExecution> = emptyMap(),
  /** Edited since the last write. Cleared by the save, not by the edit. */
  val dirty: Boolean = false,
  /**
   * Cells taken by cut or copy, waiting to be pasted.
   *
   * Per notebook rather than per app, which is narrower than JupyterLab's - its `Clipboard` is a
   * singleton, so cells cross between open notebooks. Ours cannot yet, because only one notebook
   * is open at a time; widening it is a change to where this lives, not to what it means.
   */
  val clipboard: List<Cell> = emptyList(),
  /** Whether [clipboard] came from a cut, which decides if a pasted code cell keeps its id. */
  val clipboardWasCut: Boolean = false,
  /** JupyterLab's `activeCellIndex`, by id: the one cell every cell-scoped action means. */
  val selectedCellId: String? = null,
  val mode: CellMode = CellMode.COMMAND,
  /**
   * The cell operations that can be taken back, oldest first - see [CellUndo].
   *
   * Empty on a freshly opened notebook, which is the reference's lifetime as well:
   * `Context.initialize()` clears the history once the model is loaded
   * (`packages/docregistry/src/context.ts:257-263`). Neither stack is written to disk; a cell
   * operation is a fact about this sitting, not about the file.
   */
  val undoable: List<NotebookRevision> = emptyList(),
  /** What an undo took back, ready to be replayed. Cleared by the next operation. */
  val redoable: List<NotebookRevision> = emptyList(),
)

/**
 * The document as it was before one cell operation - the entries on [EditorUiState.undoable].
 *
 * Deliberately **not** an [EditorUiState]: the stacks live on the state, so a record holding the
 * state would hold the stacks and every entry would carry every entry before it. Cells and outputs
 * are immutable and shared by reference, so an entry costs the list and map spines and nothing
 * else.
 *
 * Three fields and no more, because these are what a cell operation can change. The clipboard, the
 * selection and the mode are not the document, and JupyterLab's undo does not restore them either -
 * it `deselectAll()`s afterwards instead.
 *
 * Here rather than in [CellUndo] because it is a projection of this state, and the file it would
 * otherwise sit in holds pure functions like every one of its neighbours.
 */
data class NotebookRevision(
  val cells: List<Cell>,
  val outputs: Map<String, List<CellOutput>>,
  val executionCounts: Map<String, Int>,
)

internal fun EditorUiState.revision() = NotebookRevision(cells, outputs, executionCounts)

/**
 * The state transitions an editor makes, as pure functions.
 *
 * Kept apart from the ViewModel so what the UI shows after a given kernel event is decided by
 * something a test can call directly, with no coroutines or repository in the way.
 */
fun EditorUiState.after(event: KernelEvent): EditorUiState = when (event) {
  is KernelEvent.Output ->
    copy(outputs = outputs + (event.cellId to outputs[event.cellId].orEmpty() + event.output))

  is KernelEvent.ExecutionCount ->
    copy(executionCounts = executionCounts + (event.cellId to event.count))

  // Idle says the kernel is done with this cell, but not whether it raised - that is decided by
  // what it emitted, so the run is closed by NotebookKernel rather than here.
  KernelEvent.Idle -> this
}

fun EditorUiState.withSource(cellId: String, source: String): EditorUiState =
  copy(cells = cells.map { if (it.id == cellId) it.copy(source = source) else it }, dirty = true)

/**
 * Seeded from what was saved, so reopening a notebook shows the results it already had.
 *
 * The inverse of [withResultsOnCells], and kept beside it: how a notebook enters this state and
 * how it leaves are one idea, and reading them apart is how they drift.
 *
 * **A notebook being looked at never has zero cells.** [defaultCellId] names the empty code cell
 * a cell-less notebook is given, so there is always something to type into. That is JupyterLab's
 * rule and it lives here for the same reason it lives in its view layer
 * (`packages/notebook/src/widget.ts:606`): one place covers a notebook just created, one
 * imported with no cells, and the last cell being deleted. It is not an edit - nothing was
 * typed - so the notebook is not dirty and is not written back, which would only bump its
 * timestamp and reorder Recents for having been opened.
 */
fun Notebook.toEditorUiState(defaultCellId: String) = EditorUiState(
  notebookId = id,
  title = notebookLeafOf(title),
  cells = cells.ifEmpty { listOf(Cell(defaultCellId, CellKind.CODE, "")) },
  loaded = true,
  outputs = cells.filter { it.outputs.isNotEmpty() }.associate { it.id to it.outputs },
  executionCounts = cells.mapNotNull { cell -> cell.executionCount?.let { cell.id to it } }.toMap(),
)

/** Moves this run's results onto the cells, which is what gets persisted. */
fun EditorUiState.withResultsOnCells(): List<Cell> =
  cells.map { it.copy(outputs = outputs[it.id].orEmpty(), executionCount = executionCounts[it.id]) }

/**
 * A failure becomes an error output in the cell rather than a thrown exception: an
 * unreachable kernel must say so where the user is looking, not leave a spinner for ever.
 */
fun kernelFailure(cellId: String, failure: Throwable) = KernelEvent.Output(
  cellId,
  CellOutput.Error(
    name = failure::class.simpleName.orEmpty(),
    value = failure.message.orEmpty(),
    traceback = emptyList(),
  ),
)
