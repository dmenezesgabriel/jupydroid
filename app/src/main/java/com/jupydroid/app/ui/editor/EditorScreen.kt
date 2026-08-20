package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.DocumentSharer
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.ui.OverwriteFileDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
  notebookId: String,
  notebookRepository: NotebookRepository,
  /** Only listened to: a rename made in the browser beside this document moves it. */
  workspaceRepository: WorkspaceRepository,
  kernelGateway: KernelGateway,
  /** `notebook:export-to-format` - see [NotebookExport]. Not a kernel act. */
  notebookExporter: NotebookExporter,
  /** Where an exported file goes next, from the report that says it was written. */
  documentSharer: DocumentSharer,
  applicationScope: CoroutineScope,
  /** The app's count of runs in flight, which P1c's backstop reads; see [ActiveCellRuns]. */
  activeCellRuns: ActiveCellRuns,
  /**
   * `notebook:toggle-autoclosing-brackets`, universal - read here rather than through the
   * ViewModel, because it is app state and not a fact about this document.
   */
  editorSettingsRepository: EditorSettingsRepository,
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
      notebookExporter,
      applicationScope,
      activeCellRuns,
    )
  },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val editorSettings by editorSettingsRepository.observeSettings()
    .collectAsStateWithLifecycle(EditorSettings())
  val focusManager = LocalFocusManager.current
  val cellInput = remember { FocusedCellInput() }
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  // Remembered here rather than inside the Scaffold's slot, because [ExportReportSnackbar] posts
  // to it from outside that slot.
  val exportReports = remember { SnackbarHostState() }
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
  // And out of the find bar before it backs out of the notebook, which is the same contextual-bar
  // rule the one above states. Its own handler rather than a branch, because the three states are
  // exclusive: searching is asked for from the document's bar, which is not up while a run is
  // marked, and the find box taking the caret is not a cell being edited.
  BackHandler(enabled = state.search != null) { viewModel.search.end() }
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
  // **The editor's own width, not the window's**, which is what decides where the table of
  // contents goes ([tocPresentationFor]): in the two-pane layout the explorer has already spent
  // 360dp of the window, so asking `currentWindowAdaptiveInfo()` would offer a pane to an editor
  // with no room for one. It is also what makes both branches reachable from a test - a window
  // cannot be forced under a composable, while a layout constraint can (`docs/DECISIONS.md`).
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val tocPresentation = tocPresentationFor(maxWidth.value.toInt())
    // Saved rather than remembered: the pane survives a rotation, which is the one gesture most
    // likely to bring it into range in the first place. Hidden by default, as the reference's own
    // ToC tab is - the file browser is the sidebar panel that opens with the shell, not this.
    var tocPaneOpen by rememberSaveable { mutableStateOf(false) }
    // Remembered rather than saved, which is the reference's own lifetime for it - see [TocFolds].
    val tocFolds = remember { TocFolds() }
    // One list for every surface that reads it - the panel, the numbers on the notebook's own
    // headings, and the jump - so a heading cannot be numbered one way here and another there.
    val tocHeadings = state.tableOfContentsHeadings(editorSettings.tocIncludeOutput)
    val showTocPane = tocPaneOpen && tocPresentation == TocPresentation.PANE
    // `activateNewMatch`'s `scrollToItem` (`packages/notebook/src/searchprovider.ts:625-630`),
    // keyed on the hit rather than on the cell: two matches in one cell are two steps, and a
    // notebook that only moved when the *cell* changed would look stuck on the second of them.
    // The reveal has already happened in the state, so the index is read after it and the cell is
    // on screen to scroll to.
    LaunchedEffect(state.search?.current, state.search?.hits) {
      val landed = state.search?.currentHitCellId ?: return@LaunchedEffect
      val index = state.cellsOnScreen.indexOfFirst { it.id == landed }
      // **Only when the cell is not already fully on screen**, which is `activateNewMatch`'s two
      // steps read together: `if (!activeCell.inViewport) scrollToItem(…)` and then
      // `editor.revealPosition(…)` (`:625-641`). The first alone is not the rule and measuring is
      // what said so - `inViewport` is true of a cell showing five pixels above the soft keyboard,
      // so a match the arrows had just been asked for stayed cut in half at the bottom edge
      // (`evidence/p2i-4-next-previous/`, the run before this). The second step is what makes the
      // *match* readable rather than merely present, and with a cell-sized list item the equivalent
      // is asking for the whole cell.
      //
      // Still conditional, because the other half of the reference's guard matters too: stepping
      // between two matches already in front of the reader must not yank the notebook.
      if (index >= 0 && !listState.isFullyVisible(index)) listState.animateScrollToItem(index)
    }
    val jumpToHeading: (String) -> Unit = { cellId ->
      // A heading is never hidden by its own section (CellHeadings.kt), but one nested inside an
      // ancestor's collapsed section is - and **the reference reveals it**, which P2h recorded the
      // other way round. `expandParent` is wired to every active-cell change
      // (`packages/notebook-extension/src/index.ts:2871-2873`), so a jump there lands on a cell
      // whose section has just been opened; here the entry was listed and the tap did nothing.
      viewModel.edits.revealCell(cellId)
      // Against the revealed document rather than the one on screen: the transform is pure and the
      // state update has not reached this composition yet, so the index has to be asked of what the
      // notebook is about to be. It answers `this` when nothing was hidden, so the ordinary jump is
      // the same computation it always was.
      val index = state.withCellRevealed(cellId).cellsOnScreen.indexOfFirst { it.id == cellId }
      if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
    }
    Scaffold(
      // **The notebook consumes the keyboard's inset while the find bar is up, and nothing else
      // does.** Every other surface that can be on screen under an IME carries its own
      // `imePadding` - [SymbolRow] in edit mode, [NotebookBar] in command mode - so the cell list
      // has never had to. Searching is the first state with a soft keyboard and *no bottom bar*
      // (see [editorBottomBar]), and without this the `LazyColumn` lays out to the full window and
      // draws its lower half behind the keyboard: measured at `viewport=0..987` on the tablet with
      // the IME up, which made a match the arrows had just been sent to report as visible while
      // being under the keys (`evidence/p2i-4-next-previous/`, two runs before this).
      modifier = Modifier.fillMaxSize().then(if (state.search != null) Modifier.imePadding() else Modifier),
      topBar = {
        Column {
          EditorTopBars(
            state = state,
            viewModel = viewModel,
            editorSettings = editorSettings,
            onSettingsChanged = { changed -> applicationScope.launch { editorSettingsRepository.update(changed) } },
            tocHeadings = tocHeadings,
            tocPresentation = tocPresentation,
            tocPaneOpen = showTocPane,
            onToggleTocPane = { tocPaneOpen = !tocPaneOpen },
            onHeadingSelected = jumpToHeading,
            onBack = onBack,
          )
          // Under whichever bar is up rather than over the notebook: it says the *document* is
          // busy, and covering the document to say so would be the wrong trade. See [ExportStatus].
          ExportProgressBar(state.export)
        }
      },
      snackbarHost = { SnackbarHost(exportReports) },
    bottomBar = {
      EditorBottomBars(
        state = state,
        viewModel = viewModel,
        editorSettings = editorSettings,
        cellInput = cellInput,
        onBack = onBack,
      )
    },
    ) { innerPadding ->
      // The pane is a column *beside* the notebook rather than a scene of its own, and that is a
      // constraint rather than a preference: `ListDetailSceneStrategy.extraPane()` - what
      // `docs/PLAN.md` assumed - is a separate `NavEntry`, and every entry here owns its own
      // `ViewModelStore` (`docs/architecture.md` §4), so it could reach neither this notebook's
      // `EditorViewModel` for the headings nor its `listState` to scroll. See `docs/DECISIONS.md`.
      Row(modifier = Modifier.padding(innerPadding)) {
        EditorContent(
          state = state,
          onRunCell = viewModel.kernel::runCell,
          onStopCell = viewModel.kernel::stopRunningCell,
          onEditCell = viewModel::editCell,
          completionsAt = viewModel.kernel::completionsAt,
          onCellAction = viewModel.edits::applyTo,
          onCellFocusChanged = viewModel::cellFocusChanged,
          onToggleSelected = viewModel.selection::extendTo,
          onMarkdownEditRequested = viewModel::markdownEditRequested,
          cellInput = cellInput,
          autoClosingBrackets = editorSettings.autoClosingBrackets,
          tocNumbering = editorSettings.tocNumbering(),
          tocHeadings = tocHeadings,
          listState = listState,
          modifier = Modifier.weight(1f),
        )
        if (showTocPane) {
          TableOfContentsPane(
            headings = tocHeadings,
            onHeadingSelected = jumpToHeading,
            onClose = { tocPaneOpen = false },
            collapsed = tocFolds.collapsed,
            onToggleCollapsed = tocFolds::toggle,
            onToggleAllCollapsed = { tocFolds.toggleAll(tocHeadings) },
            numbering = editorSettings.tocNumbering(),
          )
        }
      }
    }
    // Outside the Scaffold's slots because it draws nothing: it posts to [exportReports], which
    // the `snackbarHost` slot above is showing. See [ExportStatus].
    ExportReportSnackbar(
      report = state.export.report,
      host = exportReports,
      onShare = { written -> documentSharer.share(written.path, written.mediaType) },
      onShown = viewModel.export::reportSeen,
    )
  }
}

/**
 * Whether item [index] is on screen **in its entirety** - see the scroll above for why partly is
 * not enough.
 *
 * An item taller than the viewport is never fully visible, and scrolling it to the top is the right
 * answer for that too; there is no loop, because the effect that asks is keyed on the match rather
 * than on the scroll.
 */
private fun LazyListState.isFullyVisible(index: Int): Boolean {
  val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
  return item.offset >= layoutInfo.viewportStartOffset &&
    item.offset + item.size <= layoutInfo.viewportEndOffset
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
  onMarkdownEditRequested: (String) -> Unit,
  cellInput: FocusedCellInput,
  /** `notebook:toggle-autoclosing-brackets`, universal - code cells only, like the reference's. */
  autoClosingBrackets: Boolean,
  /** `toc:display-numbering` / `toc:display-h1-numbering` - see [MarkdownCell]. */
  tocNumbering: TocNumbering,
  /** Every heading in the notebook, numbered as one document - see [tableOfContentsHeadings]. */
  tocHeadings: List<TocHeading>,
  /** Hoisted so P2h-2's click-to-jump can drive the same list a tap in the table of contents. */
  listState: LazyListState,
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
      // Walks the whole notebook, so it is computed once for the list rather than per item -
      // asking inside the item lambda would make drawing a notebook quadratic in its length.
      val onScreen = state.cellsOnScreen
      // Numbered once for the whole notebook rather than per cell, for the reason the two above
      // are: the numbers are the *document's* - one running counter over every heading - so asking
      // inside the item lambda would be both wrong and quadratic. Keyed by cell, so a heading a
      // code cell printed is counted but leaves no prefix for a markdown cell to draw.
      val headingPrefixes = tocHeadings.map { it.cellId }.zip(tocHeadings.tocPrefixes(tocNumbering)).toMap()
      LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        // contentType as well as key: a code cell and a markdown cell compose to very different
        // trees, and telling the list so lets it reuse each kind against its own pool instead of
        // rebuilding whenever the two alternate.
        itemsIndexed(onScreen, key = { _, cell -> cell.id }, contentType = { _, cell -> cell.kind }) { at, cell ->
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
            // Rendered unless somebody opened this one - see [MarkdownEditing]. Read from the state
            // rather than remembered in the cell, because a run has to be able to close the editor.
            markdownRendered = state.isMarkdownRendered(cell.id),
            // Only the cell a tap asked for - a command that unrenders leaves the caret alone.
            markdownTakesCaret = state.markdownTakingCaret == cell.id,
            onMarkdownEditRequested = { onMarkdownEditRequested(cell.id) },
            // Only ever non-zero on a collapsed heading, which is the reference's own condition for
            // drawing the row at all (`packages/cells/src/widget.ts:2424-2427`).
            hiddenBelow = if (cell.headingCollapsed) state.headingSectionOf(cell.id).size else 0,
            // One flag for the whole notebook and all three cell types, which is what the command
            // is: `toggleAllLineNumbers` writes code, markdown and raw together.
            lineNumbersShown = state.lineNumbersShown,
            headingPrefix = headingPrefixes[cell.id].orEmpty(),
            // Per cell rather than sliced once for the list, unlike the numbers above: a cell's
            // matches depend only on that cell - see [searchHighlightOf].
            search = state.searchHighlightOf(cell.id),
            outputSearch = state.outputSearchHighlightsOf(cell.id),
            autoClosingBrackets = autoClosingBrackets,
            cellInput = cellInput,
          )
        }
      }
    }
  }
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
