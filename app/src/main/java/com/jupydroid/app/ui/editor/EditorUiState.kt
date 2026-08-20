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
  /** JupyterLab's `activeCellIndex`, by id: the head of the selection - see [CellRangeSelection]. */
  val selectedCellId: String? = null,
  /**
   * The other end of a selected run of cells, or null when only [selectedCellId] is selected.
   *
   * One nullable id carries the whole of multi-select, because JupyterLab's selection is
   * contiguous - `extendContiguousSelectionTo` moves a head against a fixed anchor and
   * `getContiguousSelection()` throws on anything else (`packages/notebook/src/widget.ts:2216`,
   * `:2325-2348`). The cells in between are derived rather than stored, and "a run is being
   * marked" is this being non-null rather than a second flag that could disagree with it.
   */
  val anchorCellId: String? = null,
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
  /**
   * How many times a cell operation has rewritten each cell's source - see [CellUndo].
   *
   * A cell's `TextFieldState` holds its own copy of the text and is the authority on it while it
   * is on screen, so a rewrite from outside has to be announced. Comparing the two would not do:
   * an edit reaches the model on a debounce, so a field that is merely ahead of the model looks
   * exactly like one that is behind it, and re-seeding on that would revert whatever was typed
   * while the report was in flight.
   *
   * A counter rather than a flag, because two rewrites in a row - split then undo - must each be
   * a change the field notices. Not persisted: it describes a field in this sitting.
   */
  val sourceRevisions: Map<String, Int> = emptyMap(),
  /**
   * The markdown cells showing their source rather than their prose - see [MarkdownEditing].
   *
   * The cells being *edited* and not the ones being rendered, because rendered is the resting
   * state: an empty set is then correct for every notebook ever opened. Not persisted; nbformat has
   * nowhere to say a cell is being written, and the reference keeps it on the widget too.
   */
  val markdownBeingEdited: Set<String> = emptySet(),
  /**
   * The markdown cell whose editor should take the caret, or null when none should.
   *
   * **Separate from [markdownBeingEdited] because *why* a cell was opened decides this, and the
   * set cannot say why.** A tap on the prose asks to type in it; a command that unrenders does not
   * - the reference ends `changeCellType` in command mode (`actions.tsx:3308-3314`).
   *
   * The field used to request focus for having been composed at all, which was a complete answer
   * while a tap was the only way to open one. P2e-3 gave it a second way in, and
   * `evidence/p2e-3-change-to-markdown/` is what that cost: two editors arriving in one frame,
   * both asking, and the soft keyboard up over a notebook nobody was typing in.
   *
   * One id and not a set: only one field can hold the caret, so a second request replaces the
   * first rather than joining it. Not persisted, like the set beside it.
   */
  val markdownTakingCaret: String? = null,
  /**
   * Whether every cell draws a line-number gutter - `notebook:toggle-all-cell-line-numbers`.
   *
   * **One boolean for all three cell types**, because that is what the command is:
   * `toggleAllLineNumbers` writes `code`, `markdown` and `raw` together and reads them as a
   * conjunction (`packages/notebook/src/actions.tsx:1792-1812`). Three flags could disagree, and
   * no command there can produce a disagreement.
   *
   * **Per notebook and per sitting, not persisted**, which is also the reference's: the command
   * writes `notebook.editorConfig`, which belongs to the widget, and never the model. So it is a
   * fact about this sitting, like [markdownBeingEdited] beside it.
   *
   * **Shown by default, departing from the reference**, whose `defaultEditorConfig` is
   * `lineNumbers: false` on all three (`packages/notebook/src/widget.ts:1336-1355`) and whose
   * shipped settings agree (`notebook-extension/schema/tracker.json:704,752,767`). A phone shows a
   * fraction of a cell and a traceback names a line; the departure is `docs/DECISIONS.md`.
   */
  val lineNumbersShown: Boolean = true,
  /**
   * The find bar and what it found, or null while it is closed - `documentsearch:start` and
   * `documentsearch:end`. See [SearchState].
   */
  val search: SearchState? = null,
)

/**
 * The document as it was before one cell operation - the entries on [EditorUiState.undoable].
 *
 * Deliberately **not** an [EditorUiState]: the stacks live on the state, so a record holding the
 * state would hold the stacks and every entry would carry every entry before it. Cells and outputs
 * are immutable and shared by reference, so an entry costs the list and map spines and nothing
 * else.
 *
 * Four fields and no more, because these are what a cell operation can change. The clipboard, the
 * selection and the mode are not the document, and JupyterLab's undo does not restore them either -
 * it `deselectAll()`s afterwards instead.
 *
 * **[executions] is the fourth and it arrived late**, which is the rule above being applied rather
 * than an exception to it: `withOutputsCleared` drops a cell's run along with its outputs (as
 * `clearExecution` sets `executionState = 'idle'`) and `withCellsDeleted` has always dropped it
 * with the cell, so a record without it restored the output and the `[7]` over a cell with no
 * duration and no stripe.
 *
 * Here rather than in [CellUndo] because it is a projection of this state, and the file it would
 * otherwise sit in holds pure functions like every one of its neighbours.
 */
data class NotebookRevision(
  val cells: List<Cell>,
  val outputs: Map<String, List<CellOutput>>,
  val executionCounts: Map<String, Int>,
  val executions: Map<String, CellExecution>,
  /**
   * The cells whose source the operation itself rewrote, while leaving them in the notebook.
   *
   * The one thing an undo may put back over a live cell's text, and empty for every operation
   * that only moves cells around. See [CellUndo] for why the distinction is the whole design.
   */
  val rewrote: Set<String> = emptySet(),
)

internal fun EditorUiState.revision(rewrote: Set<String> = emptySet()) =
  NotebookRevision(cells, outputs, executionCounts, executions, rewrote)

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
 *
 * **And it has an active cell before anything is touched, which is the first.**
 * `Notebook.activeCellIndex` starts at `-1` and its setter clamps to `Math.max(newValue, 0)` once
 * there are widgets (`packages/notebook/src/widget.ts:1965-1974`), so a load leaves it at 0 - which
 * is what puts the reference's active-cell bar on cell one of a notebook just opened. Ours left it
 * null, and the head-relative run commands are what made that visible: with no head there is no
 * range, so `run-all-above` and `run-all-below` were greyed with nothing to say why.
 */
fun Notebook.toEditorUiState(defaultCellId: String): EditorUiState {
  val opened = cells.ifEmpty { listOf(Cell(defaultCellId, CellKind.CODE, "")) }
  return EditorUiState(
    notebookId = id,
    title = notebookLeafOf(title),
    cells = opened,
    // The head alone and no anchor: `deselectAll` leaves `activeCellIndex` where it is
    // (`widget.ts:2196-2210`), so "active" and "marked" are different claims and only the first is
    // made here - an anchor would put the selection bar up over a notebook nobody has touched.
    selectedCellId = opened.first().id,
    loaded = true,
    outputs = cells.filter { it.outputs.isNotEmpty() }.associate { it.id to it.outputs },
    executionCounts = cells.mapNotNull { cell -> cell.executionCount?.let { cell.id to it } }.toMap(),
  )
}

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
