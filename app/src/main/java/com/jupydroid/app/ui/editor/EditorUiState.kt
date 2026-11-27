package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.model.DebugStop
import com.jupydroid.app.domain.model.KernelInputRequest
import com.jupydroid.app.domain.model.KernelPickGroup
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.notebookLeafOf
import com.jupydroid.app.domain.model.withOutputFolded
import com.jupydroid.app.domain.port.KernelEvent

data class EditorUiState(
  /**
   * Where the notebook lives now, which a rename changes - the file *is* the name.
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
  /**
   * This notebook could not be **opened**, so there is nothing to draw - see `EditorScreen`, which
   * shows this *instead of* the document. A failure to *save* is [saveFailure] and not this, for
   * exactly that reason: replacing a notebook with a message would take unsaved work off the screen.
   */
  val error: String? = null,
  /**
   * The last write that could not be made, or null when the notebook is being saved cleanly.
   *
   * **A state rather than an event**, which is why it is not reported through the snackbar
   * [ExportState.report] uses: an export either happened or did not and is over, while "your work
   * is not reaching the disk" goes on being true until it stops. So it is drawn beside the notebook
   * and cleared by the next save that works, and nothing has to remember having shown it.
   *
   * Reachable, not defensive: `WorkspaceFiles.changes` announces what this app moved, never
   * what the kernel did, so a cell that removes the notebook's folder leaves this editor writing to
   * a path that is not there.
   */
  val saveFailure: String? = null,
  /**
   * A save was refused because the file is no longer the one this notebook was read from, and the
   * user has not yet said which of the two to keep - see [NotebookSaveConflict].
   *
   * Apart from [saveFailure] because it is a question rather than a report: nothing failed, and
   * both answers are available. A state rather than an event for the reason that one is, and so
   * that a second refused save while the question is up is the same question, as
   * `_conflictModalIsOpen` makes it in the reference
   * (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`).
   */
  val saveConflict: Boolean = false,
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
  /**
   * Cells whose next output arrives under a `wait`-ing `clear_output` - the reference's
   * `clearNext` flag, kept per cell here. Run state like [outputs], not a saved attribute.
   */
  val clearsPending: Set<String> = emptySet(),
  /**
   * Per cell, the `input()` its run is blocked on - what the output area draws a field for. Run
   * state like [outputs]: the request dies with the run, and only the echo the answer leaves is
   * saved.
   */
  val inputRequests: Map<String, KernelInputRequest> = emptyMap(),
  val executionCounts: Map<String, Int> = emptyMap(),
  /**
   * Per cell, where its run got to. Replaces a single nullable `runningCellId`, which could say
   * only "this one is running" or "none is" - a queued cell, a skipped one and one nobody had
   * ever run were all the same absence. `runningCellId` is derived from this now.
   */
  val executions: Map<String, CellExecution> = emptyMap(),
  /** Edited since the last write. Cleared by the save, not by the edit. */
  val dirty: Boolean = false,
  /** `notebook:export-to-format` - what the picker is doing; see [NotebookExport]. */
  val export: ExportState = ExportState(),
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
   * while a tap was the only way to open one. A second way in cost exactly that: two editors
   * arriving in one frame, both asking, and the soft keyboard up over a notebook nobody was
   * typing in.
   *
   * One id and not a set: only one field can hold the caret, so a second request replaces the
   * first rather than joining it. Not persisted, like the set beside it.
   */
  val markdownTakingCaret: String? = null,
  /**
   * The code cell whose field should take the caret once it composes, or null while none asked.
   *
   * A consumed one-shot, the same contract as [markdownTakingCaret] beside it - and a second
   * field rather than a reuse, because the two asks have disjoint producers: markdown's is a tap
   * on rendered prose, while this one is a command's answer - `runAndAdvance`/`runAndInsert`
   * land the caret in a cell they just made (`packages/notebook/src/actions.tsx:830-849`,
   * `:908-918`). [NotebookCellEdits.caretTaken] clears it the moment the field reports the caret
   * held, since a stale ask would re-yank focus every time the cell recomposes on a scroll.
   * Not persisted.
   */
  val cellTakingCaret: String? = null,
  /**
   * Head moves made by a hardware key, counted rather than flagged - the same shape as
   * [noDefinitionFound], because the screen's answer (scroll the head in and re-ask the frame's
   * focus) is an event per move, and a second move in the same direction must still be a change.
   *
   * Only the key path counts: a tap that selects a cell already carries its own focus, so a
   * *keyed* head move is the one that can land on a cell the list has not composed yet - the
   * focus-following effect then finds no frame to aim at and the count is what re-asks once the
   * scroll has composed it. Not persisted, like the counters beside it.
   */
  val headMovesByKey: Int = 0,
  /**
   * Where a definition jump lands, or null while none is pending.
   *
   * A consumed one-shot like [markdownTakingCaret]: [NotebookCellEdits.landOnDefinition] sets
   * it, the target cell's own field applies the caret and the symbol flash the moment it is
   * composed - the scroll is what brings it on screen - and `caretLanded` clears it, so a
   * landing can never stale into a caret yank minutes later. Not persisted.
   */
  val caretLanding: DefinitionTarget? = null,
  /**
   * Definition asks that found nothing to jump to, counted rather than flagged - a miss is an
   * event the screen reports once ("No definition found"), and only a counter makes the second
   * miss a change. Not persisted, for [caretLanding]'s reason.
   */
  val noDefinitionFound: Int = 0,
  /**
   * The last refused rename the screen reports - [RenameOutcome.tick] counted rather than
   * flagged, for [noDefinitionFound]'s reason: a second refusal with the same message is
   * still a change the snackbar must show. Not persisted, like the counter beside it.
   */
  val renameOutcome: RenameOutcome? = null,
  /**
   * Breakpoints set on a code cell's lines, by cell id - [NotebookDebugger]'s own state.
   *
   * **Not on [Cell], and not persisted.** JupyterLab keeps a breakpoint in its own in-memory
   * `DebuggerModel`, never in `.ipynb` - the same reasoning that keeps
   * [WorkspaceEntry][com.jupydroid.app.domain.model.WorkspaceEntry]'s `updatedAt` off [Notebook]:
   * a fact this sitting knows, not a fact about the file. What *does* survive [EditorViewModel]
   * being rebuilt for the same notebook is [NotebookBreakpoints][com.jupydroid.app.domain.model.NotebookBreakpoints],
   * which this is seeded from on load and written back to on every toggle.
   *
   * A cell with no entry here has no breakpoints - the same absence-means-empty shape
   * [outputs]/[executionCounts] already use.
   */
  val breakpointsByCellId: Map<String, List<Breakpoint>> = emptyMap(),
  /**
   * The language server's diagnostics per cell - `publishDiagnostics` mapped back through the
   * notebook's virtual document ([NotebookDiagnostics]), keyed like [breakpointsByCellId]:
   * cell id to the marks the gutter draws.
   */
  val diagnosticsByCellId: Map<String, List<CellDiagnostic>> = emptyMap(),
  /**
   * Where the kernel is paused under the debugger, or null while nothing is stopped -
   * [NotebookDebugger.watchForStop]'s own state, following a `debug_event` off [KernelDebugger][
   * com.jupydroid.app.domain.port.KernelDebugger]'s `events`.
   *
   * **One field, not a per-cell map**, unlike [breakpointsByCellId]: the phone-scoped debugger
   * handles one thread stopping at a time, the same reasoning [runningCellId] is derived from
   * [executions] rather than stored as its own nullable id for the domain having only one.
   *
   * Not persisted, not seeded on load - a stop is live kernel state, gone the moment the process
   * restarts, the same reasoning [breakpointsByCellId] is written down for.
   */
  val stoppedAt: DebugStop? = null,
  /**
   * [stoppedAt]'s own call stack and top-frame variables, chained on the same `Stopped` event -
   * [NotebookDebugger.watchForStop]'s other half. Kept apart from [stoppedAt] rather than
   * merged into it: the gutter's stopped-line wash only ever reads [stoppedAt], and
   * widening its contract for a panel it does not draw would couple the two for no reason.
   *
   * Null exactly when [stoppedAt] is - cleared on the same `Continued` event, live kernel state
   * for the same reason [stoppedAt] itself is not persisted.
   */
  val debugStack: DebugStack? = null,
  /**
   * A step/continue command is in flight - [NotebookDebugger.step]'s own guard against a second
   * tap before the kernel reports where it landed, which would send a stale command for a stop
   * already left behind. Cleared by [NotebookDebugger.watchForStop] the moment the next `Stopped`
   * or `Continued` event resolves, the same way [stoppedAt] itself is written from that event.
   */
  val stepping: Boolean = false,
  /**
   * The find bar and what it found, or null while it is closed - `documentsearch:start` and
   * `documentsearch:end`. See [SearchState].
   */
  val search: SearchState? = null,
  /**
   * Whether this notebook holds an open kernel session - see [kernelStatus], which is the only
   * thing that reads it.
   *
   * Written by [NotebookKernelSession] rather than by whoever asked for the kernel, because the
   * session is the one object that sees every way one arrives and every way one goes: an
   * `open()`, a `forget()` on shutdown or teardown, and the drop `reopeningIfStale` makes when
   * the idle backstop's stopped server refuses a session.
   */
  val kernelConnected: Boolean = false,
  /**
   * `notebook:restart-kernel` is in flight - see [NotebookKernel.restart].
   *
   * Its own field rather than an [ExecutionStatus], because a restart is a fact about the *kernel*
   * and the executions are facts about cells: the restart's whole effect on them is that they stop
   * being true.
   */
  val kernelRestarting: Boolean = false,
  /**
   * Kernel picks this notebook is owed, counted rather than flagged - `startKernel` answering
   * needsSelection (`sessioncontext.tsx:746-747`), which is the reference's cue to open
   * `selectKernel` (`context.ts:599-603`). Owed when the persisted kernelspec resolves to
   * nothing in the Space's catalogue, at load and again at each refused run: the picker is
   * a dialog, and only a counter makes the second owe a change it can see. Not persisted.
   */
  val kernelPickRequired: Int = 0,
  /**
   * `SessionContext.kernelDisplayName` (`sessioncontext.tsx`): the *live* kernel's spec
   * `display_name`, falling back to its raw `kernel.name` when the catalogue does not know it -
   * and `noKernelName` = "No Kernel" when nothing is bound. A notebook's saved
   * `metadata.kernelspec` can name a kernelspec the Space does not have, so this is resolved at
   * bind time rather than read off the file.
   */
  val kernelDisplayName: String = "No Kernel",
  /**
   * The `selectKernel` dialog's groups while it is open (`sessioncontext.tsx:1398-1460`),
   * fetched fresh by [NotebookKernelPicker.open] each time it is asked for - the catalogue and
   * the running kernels are the server's answers, so the list is built when the dialog is
   * opened rather than kept. null is closed.
   */
  val kernelPicker: List<KernelPickGroup>? = null,
  /**
   * A kernel change is in flight (`_isChangingKernel`, `sessioncontext.tsx:1255`) - the PATCH is
   * out, the new kernel has not answered. [kernelStatus] reads it as STARTING, the reference's
   * own `kernelDisplayStatus` mapping (`:623-625`).
   */
  val kernelChanging: Boolean = false,
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
  is KernelEvent.Output -> afterOutput(event).withSearchedOutputsRecomputed()

  is KernelEvent.ExecutionCount ->
    copy(executionCounts = executionCounts + (event.cellId to event.count))

  is KernelEvent.InputRequested -> copy(inputRequests = inputRequests + (event.cellId to event.request))

  // Idle says the kernel is done with this cell, but not whether it raised - that is decided by
  // what it emitted, so the run is closed by NotebookKernel rather than here.
  KernelEvent.Idle -> this
}

/**
 * The user answered [cellId]'s prompt with [value]: the field closes and the echo joins the cell's
 * outputs, ahead of whatever the cell prints with the answer. A cell with no prompt is left as it
 * was, so a second submit of a spent field echoes nothing.
 */
fun EditorUiState.withInputAnswered(cellId: String, value: String): EditorUiState {
  val request = inputRequests[cellId] ?: return this
  return copy(
    inputRequests = inputRequests - cellId,
    outputs = outputs + (cellId to outputs[cellId].orEmpty().withOutputFolded(request.echoOf(value))),
  ).withSearchedOutputsRecomputed()
}

/** [EditorUiState.stoppedAt]'s line for [cellId], or null when nothing is stopped there. */
fun EditorUiState.stoppedLineOf(cellId: String): Int? = stoppedAt?.takeIf { it.cellId == cellId }?.line

fun EditorUiState.withSource(cellId: String, source: String): EditorUiState =
  copy(cells = cells.map { if (it.id == cellId) it.copy(source = source) else it }, dirty = true)

/**
 * Several cells' sources at once - a rename's whole-document answer ([NotebookCellEdits.cellsRenamed]),
 * where each cell's new text lands together or the operation is no operation at all.
 */
fun EditorUiState.withSources(sources: Map<String, String>): EditorUiState = copy(
  cells = cells.map { cell -> sources[cell.id]?.let { source -> cell.copy(source = source) } ?: cell },
  dirty = true,
)

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
