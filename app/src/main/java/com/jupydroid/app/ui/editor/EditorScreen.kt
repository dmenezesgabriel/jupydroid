package com.jupydroid.app.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jupydroid.app.theme.editorPalette
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.ui.OverwriteFileDialog
import kotlinx.coroutines.CoroutineScope

@Composable
fun EditorScreen(
  notebookId: String,
  notebookRepository: NotebookRepository,
  /** Only listened to: a rename made in the browser beside this document moves it. */
  workspaceRepository: WorkspaceRepository,
  kernelGateway: KernelGateway,
  applicationScope: CoroutineScope,
  /** The app's count of runs in flight, which P1c's backstop reads; see [ActiveCellRuns]. */
  activeCellRuns: ActiveCellRuns,
  onBack: () -> Unit,
  /**
   * Reports where the notebook lives *now*, because a rename moves it.
   *
   * The navigation key keeps the path the editor was opened under - replacing it would re-create
   * the entry and throw the editor's state away. The reference has the same split: a `Context`
   * holds the live path and `_contextsForPath` looks up by that, never by the path a widget was
   * created with.
   */
  onNotebookMoved: (String) -> Unit = {},
  modifier: Modifier = Modifier,
  viewModel: EditorViewModel = viewModel {
    EditorViewModel(
      notebookId,
      notebookRepository,
      workspaceRepository,
      kernelGateway,
      applicationScope,
      activeCellRuns,
    )
  },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val focusManager = LocalFocusManager.current
  val cellInput = remember { FocusedCellInput() }
  // Back in edit mode returns to command mode instead of leaving the notebook - the touch
  // analogue of `Escape`, which is what both references bind it to. Dropping focus is what
  // actually does it: the field loses the caret, the keyboard goes, and the focus listener below
  // reports the mode change, so there is one path out of edit mode rather than two.
  BackHandler(enabled = state.mode == CellMode.EDIT) { focusManager.clearFocus() }
  // Back out of the marking mode before it backs out of the notebook, which is what a contextual
  // bar's close does everywhere else on Android - and the reason it is a second handler rather
  // than a branch in the one above is that the two states are exclusive: entering edit mode drops
  // the selection (`widget.ts:1925`), so only one of these is ever enabled.
  BackHandler(enabled = state.selecting) { viewModel.selection.deselectAll() }
  // Marking a run is a command-mode act (`notebook.mode = 'command'`, `actions.tsx:1326`), and in
  // this app the mode *is* where the focus is - the back handler above changes it by dropping the
  // caret rather than by setting a field, so that there is one path out of edit mode and not two.
  // `withSelectionStartedAt` set the mode directly and so invented the second: the symbol row went
  // and the kernel bar came back while the field still held the caret, which left **the soft
  // keyboard up** over the selection bar with nothing to type into - and on a 320dp phone that hid
  // every cell but the first two, so the run could not be extended at all. Caught by the flow, not
  // by a test: no Compose test has an IME (`evidence/p2c-multi-select/`, the run before this).
  LaunchedEffect(state.selecting) { if (state.selecting) focusManager.clearFocus() }
  // The file behind this notebook was deleted, so there is nothing left to show: the reference
  // closes the widget from the manager (`deleteWidgets`), and this is the same close asked for by
  // the document, which is the only party that hears every delete.
  LaunchedEffect(state.gone) { if (state.gone) onBack() }
  LaunchedEffect(state.notebookId) {
    if (state.notebookId.isNotEmpty()) onNotebookMoved(state.notebookId)
  }
  state.nameTaken?.let { taken ->
    OverwriteFileDialog(
      name = taken,
      onOverwrite = { viewModel.answerOverwrite(accepted = true) },
      onDismiss = { viewModel.answerOverwrite(accepted = false) },
    )
  }
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      // A marked run replaces the document's bar rather than adding to it: the notebook's title,
      // its rename and its undo are about the document, and none of them is what the screen is
      // for while cells are being picked. Material's contextual-bar rule, and the reference's
      // too - its selection commands are never on the notebook's toolbar.
      val head = state.cells.firstOrNull { it.id == state.selectedCellId }
      if (state.selecting && head != null) {
        SelectionTopBar(
          selectedCount = state.selectedCellIds.size,
          headCell = head,
          canPaste = state.clipboard.isNotEmpty(),
          onDeselectAll = viewModel.selection::deselectAll,
          onSelectAll = viewModel.selection::selectAll,
          onExtend = viewModel.selection::extend,
          onCellAction = { action -> viewModel.edits.applyTo(head.id, action) },
        )
      } else if (isEditorTopBarVisible(state.mode)) {
        // Dropped only on a window too short to afford it while a keyboard is up - see
        // [editorTopBarVisible]. On a phone in landscape the alternative is a notebook with zero
        // height, which is what this was.
        EditorTopBar(
          title = state.title,
          canUndo = state.undoable.isNotEmpty(),
          canRedo = state.redoable.isNotEmpty(),
          onRename = viewModel::renameNotebook,
          onUndo = viewModel.edits::undo,
          onRedo = viewModel.edits::redo,
          onBack = onBack,
        )
      }
    },
    bottomBar = {
      // One bar at a time, and which one is the mode's answer: typing wants the characters a
      // touch keyboard lacks, and a notebook being read wants the kernel. Showing both would
      // stack two bars over a keyboard that already owns half the screen.
      if (state.mode == CellMode.EDIT) {
        SymbolRow(input = cellInput)
      } else {
        NotebookBar(
          busy = state.runningCellId != null,
          notebookTitle = state.title,
          onRunAll = { viewModel.kernel.run(RunRange.ALL) },
          onRun = viewModel.kernel::run,
          // Greyed rather than absent, so the menu's shape does not change under the thumb as the
          // head moves - and each says why by being disabled exactly where the reference disables it.
          canRunAllAbove = state.canRunAllAbove,
          canRunSelectedAndBelow = state.canRunSelectedAndBelow,
          onInterrupt = viewModel.kernel::stopRunningCell,
          onRestart = { viewModel.kernel.restart() },
          onRestartAndRunAll = { viewModel.kernel.restart(andRunAll = true) },
          onShutdown = { viewModel.kernel.shutdown() },
          // The close is the shutdown's continuation rather than a second act, so it runs after
          // the session has actually ended - `notebook:close-and-shutdown` awaits it too.
          onCloseAndShutdown = { viewModel.kernel.shutdown(then = onBack) },
        )
      }
    },
  ) { innerPadding ->
    EditorContent(
      state = state,
      onRunCell = viewModel.kernel::runCell,
      onStopCell = viewModel.kernel::stopRunningCell,
      onEditCell = viewModel::editCell,
      completionsAt = viewModel.kernel::completionsAt,
      onCellAction = viewModel.edits::applyTo,
      onCellFocusChanged = viewModel::cellFocusChanged,
      onToggleSelected = viewModel.selection::extendTo,
      cellInput = cellInput,
      modifier = Modifier.padding(innerPadding),
    )
  }
}

@Composable
private fun EditorContent(
  state: EditorUiState,
  onRunCell: (Cell) -> Unit,
  onStopCell: () -> Unit,
  onEditCell: (String, String) -> Unit,
  completionsAt: suspend (String, Int) -> CompletionResult,
  onCellAction: (String, CellAction, CellEditorText) -> Unit,
  onCellFocusChanged: (String, Boolean) -> Unit,
  onToggleSelected: (String) -> Unit,
  cellInput: FocusedCellInput,
  modifier: Modifier = Modifier,
) {
  when {
    state.error != null -> ErrorMessage(message = state.error, modifier = modifier)
    !state.loaded -> EmptyMessage("Loading\u2026", modifier)
    // No empty branch: a loaded notebook always has at least one cell, given one by
    // toEditorUiState if the file had none. A "No cells yet" message would advertise a state
    // that cannot happen, and used to be a dead end when it did.
    else -> {
      // Computed once for the whole list rather than per cell: `selectedCellIds` walks the cells
      // to find both ends, and asking it inside the item lambda would make drawing the notebook
      // quadratic in its length.
      val marked = state.selectedCellIds.toSet()
      LazyColumn(modifier = modifier.fillMaxSize()) {
        // contentType as well as key: a code cell and a markdown cell compose to very different
        // trees, and telling the list so lets it reuse each kind against its own pool instead of
        // rebuilding whenever the two alternate.
        itemsIndexed(state.cells, key = { _, cell -> cell.id }, contentType = { _, cell -> cell.kind }) { at, cell ->
          EditorCell(
            cell = cell,
            outputs = state.outputs[cell.id].orEmpty(),
            executionCount = state.executionCounts[cell.id],
            sourceRevision = state.sourceRevisions[cell.id] ?: 0,
            execution = state.executionOf(cell.id),
            canPaste = state.clipboard.isNotEmpty(),
            selected = state.selectedCellId == cell.id,
            // Null unless a run is being marked, which is what keeps the tick and the `\u22ee` from
            // ever being on screen together. The position is 1-based and is the screen reader's,
            // not the eye's - see [CellMark].
            mark = if (state.selecting) CellMark(cell.id in marked, position = at + 1) else null,
            markedCount = marked.size,
            // The reference's rule, read literally: the tint is on the cell node and the active
            // cell only joins it once the run holds more than one - `jp-mod-multiSelected` is
            // added at `count > 1` (`packages/notebook/src/widget.ts:2763-2765`).
            tinted = marked.size > 1 && cell.id in marked,
            mode = state.mode,
            onRun = { onRunCell(cell) },
            onStop = onStopCell,
            onSourceEdited = { source -> onEditCell(cell.id, source) },
            completionsAt = completionsAt,
            onCellAction = { action, editor -> onCellAction(cell.id, action, editor) },
            onFocusChanged = { focused -> onCellFocusChanged(cell.id, focused) },
            onToggleSelected = { onToggleSelected(cell.id) },
            cellInput = cellInput,
          )
        }
      }
    }
  }
}

@Composable
private fun EditorCell(
  cell: Cell,
  outputs: List<CellOutput>,
  executionCount: Int?,
  sourceRevision: Int,
  execution: CellExecution?,
  canPaste: Boolean,
  selected: Boolean,
  mark: CellMark?,
  markedCount: Int,
  tinted: Boolean,
  mode: CellMode,
  onRun: () -> Unit,
  onStop: () -> Unit,
  onSourceEdited: (String) -> Unit,
  completionsAt: suspend (String, Int) -> CompletionResult,
  onCellAction: (CellAction, CellEditorText) -> Unit,
  onFocusChanged: (Boolean) -> Unit,
  onToggleSelected: () -> Unit,
  cellInput: FocusedCellInput,
  modifier: Modifier = Modifier,
) {
  // Markdown never reaches a kernel: for those cells Run means "render", which is what running
  // one means in Jupyter too.
  val rendered = rememberMarkdownRendered(cell.id)
  // One text state per cell, owned here rather than inside the field: a cell has one source, and
  // the caret in it is read by the cell's actions as well as by the field. Created whether or not
  // a field is on screen, so collapsing a cell and expanding it keeps the caret and the typing
  // history instead of re-seeding from the model.
  val field = rememberTextFieldState(cell.source)
  // Every action carries what this cell's field is holding, read when the action is chosen rather
  // than when the menu was built. Only Split reads it, and it is the one action that is not a pure
  // function of the document - see [CellEditorText]. Both surfaces dispatch through here, so the
  // accessibility context menu gets it too.
  val act: (CellAction) -> Unit = { action ->
    onCellAction(action, CellEditorText(field.text.toString(), field.selection))
  }
  val markdown = cell.kind == CellKind.MARKDOWN
  val stripe = cellStripe(
    selected = selected,
    mode = mode,
    status = execution?.status ?: ExecutionStatus.NOT_STARTED,
    palette = editorPalette,
  )
  // The whole cell carries its actions for a screen reader, not only the `⋮` that opens them.
  CellFrame(
    stripe = stripe,
    tint = if (tinted) editorPalette.cellMultiSelected else Color.Unspecified,
    modifier = modifier.cellCustomActions(
      cell = cell,
      canPaste = canPaste,
      selecting = mark != null,
      // What *this* cell's menu will act on: the whole run when it is in it, itself when not.
      count = if (mark?.checked == true) markedCount else 1,
      onAction = act,
    ),
  ) {
    CellHeader(
      cell = cell,
      executionCount = executionCount,
      execution = execution,
      canPaste = canPaste,
      mark = mark,
      onToggleSelected = onToggleSelected,
      onRun = if (markdown) ({ rendered.value = true }) else onRun,
      onStop = onStop,
      onCellAction = act,
    )
    Spacer(modifier = Modifier.height(4.dp))
    // Wrapped so the focus listener sits on the *input area* rather than the whole cell, which
    // is the distinction JupyterLab draws in `_evtFocusIn`: focus anywhere in a cell selects it,
    // but only focus inside the editor means edit mode. Put on the Column, the header's buttons
    // would count as typing.
    Box(modifier = onCellInputFocus(onFocusChanged)) {
      if (cell.sourceHidden) {
        // Collapsed beats every other source state: there is nothing to type into, and the tap
        // that expands must not also land in a text field.
        CollapsedCell(
          preview = firstNonEmptyLine(cell.source),
          description = COLLAPSED_SOURCE,
          onExpand = { act(CellAction.ToggleSourceHidden) },
        )
      } else if (cell.kind == CellKind.CODE) {
        CodeCellField(
          state = field,
          source = cell.source,
          sourceRevision = sourceRevision,
          onSourceEdited = onSourceEdited,
          completionsAt = completionsAt,
          input = cellInput,
          modifier = Modifier
            .fillMaxWidth()
            .background(editorPalette.cellBackground, MaterialTheme.shapes.small)
            .padding(8.dp),
        )
      } else {
        MarkdownCell(
          state = field,
          source = cell.source,
          sourceRevision = sourceRevision,
          rendered = rendered.value,
          onEditRequested = { rendered.value = false },
          onSourceEdited = onSourceEdited,
          input = cellInput,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
    CellOutputs(
      outputs = outputs,
      hidden = cell.outputsHidden,
      onExpand = { act(CellAction.ToggleOutputsHidden) },
    )
  }
}

/** What each output kind becomes on screen is [RenderedOutput]'s decision, not this one's. */
@Composable
private fun CellOutputs(outputs: List<CellOutput>, hidden: Boolean, onExpand: () -> Unit) {
  val rendered = outputs.mapNotNull { it.render() }
  // Nothing to fold when there is nothing to show, so an empty cell looks the same either way.
  if (rendered.isEmpty()) return
  Spacer(modifier = Modifier.height(4.dp))
  if (hidden) {
    CollapsedCell(rendered.collapsedPreview(), COLLAPSED_OUTPUTS, onExpand)
    return
  }
  Column(modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Cell output" }) {
    rendered.forEach { output ->
      when (output) {
        is RenderedOutput.Text -> OutputText(output)
        is RenderedOutput.Image -> OutputImage(output)
        // The same renderer a markdown cell uses: `text/html` parses to the same blocks.
        is RenderedOutput.Document -> RichText(output.blocks, Modifier.fillMaxWidth())
      }
    }
  }
}

@Composable
private fun OutputText(output: RenderedOutput.Text) {
  if (!output.isError) {
    Text(
      text = output.text,
      style = MaterialTheme.typography.bodySmall,
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.onSurface,
    )
    return
  }
  // A traceback is long and needs to be findable at a glance, so it gets its own tinted
  // block rather than only a colour that a monospace wall of text swallows.
  Text(
    text = output.text,
    style = MaterialTheme.typography.bodySmall,
    fontFamily = FontFamily.Monospace,
    color = MaterialTheme.colorScheme.onErrorContainer,
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
      .padding(8.dp),
  )
}

@Composable
private fun OutputImage(output: RenderedOutput.Image) {
  // Decoded here rather than in RenderedOutput so that stays free of Android types and
  // unit-testable; remembered by content so scrolling does not re-decode every frame.
  val bitmap = remember(output) {
    BitmapFactory.decodeByteArray(output.png, 0, output.png.size)?.asImageBitmap()
  } ?: return
  Image(
    bitmap = bitmap,
    contentDescription = "Cell image output",
    modifier = Modifier.fillMaxWidth(),
    contentScale = ContentScale.Fit,
  )
}

@Composable
private fun ErrorMessage(message: String, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
    Text(
      text = message,
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodyLarge,
    )
  }
}

@Composable
private fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
