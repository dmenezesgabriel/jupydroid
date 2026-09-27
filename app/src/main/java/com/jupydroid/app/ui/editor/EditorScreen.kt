package com.jupydroid.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.DocumentOutline
import com.jupydroid.app.OpenDebugStack
import com.jupydroid.app.OpenDocumentOutline
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.model.NotebookBreakpoints
import com.jupydroid.app.domain.port.DocumentSharer
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.theme.editorPalette
import com.jupydroid.app.ui.OverwriteFileDialog
import com.jupydroid.app.ui.ROOT_LABEL
import com.jupydroid.app.ui.SaveBeforeCloseGate
import com.jupydroid.app.ui.ancestorCrumbsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
  notebookId: String,
  notebookRepository: NotebookRepository,
  /** Only listened to: a rename made in the browser beside this document moves it. */
  workspaceRepository: WorkspaceRepository,
  kernelGateway: KernelGateway,
  /** DAP over the same kernel session - the handshake `EditorViewModel.debugger` sends. */
  kernelDebugger: KernelDebugger,
  /**
   * The Space's language server - P5c's completion source, asked before the kernel. Defaulted
   * the way [openDocuments] is, for the tests that drive this screen alone.
   */
  languageServer: LanguageServerPort = FakeLanguageServerPort(),
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
  /**
   * Where this document publishes its structure for the shell's side panel - see
   * [OpenDocumentOutline]. Not a table of contents drawn here; the one place the headings are
   * derived, offered to the pane that cannot reach this entry's `EditorViewModel`.
   */
  outline: OpenDocumentOutline,
  /**
   * `toc:show-panel`, whose whole body there is `labShell.activateById(toc.id)`
   * (`packages/toc-extension/src/index.ts:161-172`): it *activates* the shell's panel and nothing
   * else. **Null when the shell has no side area** - on a compact window the notebook's own menu
   * shows the overlay instead, which is the narrow adaptation of a sidebar this app has always had.
   */
  onShowTableOfContents: (() -> Unit)?,
  /** Where this document publishes its stop's call stack and variables - see [OpenDebugStack]. */
  debugPanel: OpenDebugStack,
  /** [onShowTableOfContents]'s own rule, for the debugger's side panel - see [SidePanel.DEBUGGER]. */
  onShowDebugger: (() -> Unit)?,
  /**
   * Whether a browser is drawn beside this notebook, which decides only how [onBack] is presented -
   * see [DocumentDismissAction][com.jupydroid.app.ui.DocumentDismissAction].
   *
   * **A second reading of the width, and it is worth naming as one.** [onShowTableOfContents] is
   * null for the same underlying reason - there is no side area to activate - and the two are
   * derived from one `twoPane` at the single call site, so they cannot disagree there. They are not
   * folded because they answer different questions of different widgets: one is *"is there a panel
   * to ask"*, whose lambda differs in body and not only in presence, and this is *"is the browser
   * already on screen"*. P7a-2's rule is about two parameters describing one widget's state.
   */
  twoPane: Boolean = false,
  /**
   * A breadcrumb segment was tapped: browse that folder (`""` is the workspace root).
   *
   * `filebrowser` is a different `NavEntry` with its own `ViewModel`, so this cannot reach it -
   * but unlike the outline, which flows *sideways*, this flows **down** from the entries' common
   * parent and needs nothing but the state `MainNavigation` hoists. See [EditorTopBar].
   */
  onGoToFolder: (String) -> Unit,
  onBack: () -> Unit,
  /**
   * `notebook:create-console` (P6b-9) - a Console session sharing this notebook's own kernel,
   * handed the session `NotebookKernel.attachConsole()` mints so the caller can push it wherever a
   * Console screen is reached from - see `MainNavigation`.
   */
  onOpenConsole: (NotebookKernelSession) -> Unit = {},
  /**
   * Reports where the notebook lives *now*, because a rename moves it.
   *
   * The navigation key keeps the path the editor was opened under - replacing it would re-create
   * the entry and throw the editor's state away. The reference has the same split: a `Context`
   * holds the live path and `_contextsForPath` looks up by that, never by the path a widget was
   * created with.
   */
  onNotebookMoved: (String) -> Unit = {},
  /**
   * A workspace search result this notebook was opened from, to be landed on - the notebook half of
   * what [com.jupydroid.app.ui.fileeditor.FileEditorScreen] already does for a plain file.
   *
   * Null for every other way of opening a notebook, which is every way there was before P2x-4d.
   */
  reveal: MatchedLine? = null,
  /** Consumed once, the way `browseRequest` is - see the `LaunchedEffect` below. */
  onRevealHandled: () -> Unit = {},
  /**
   * Where this document says it is open, so a workspace replace edits its cells rather than
   * rewriting the file underneath it. Defaulted for a test that drives this screen alone.
   */
  openDocuments: OpenDocuments = remember { OpenDocuments() },
  /** Where a breakpoint outlives this screen's `EditorViewModel`; see [NotebookBreakpoints]. */
  notebookBreakpoints: NotebookBreakpoints = remember { NotebookBreakpoints() },
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
      openDocuments,
      notebookBreakpoints,
      kernelDebugger,
      editorSettingsRepository,
      languageServer,
    )
  },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val editorSettings by editorSettingsRepository.observeSettings()
    .collectAsStateWithLifecycle(EditorSettings())
  val focusManager = LocalFocusManager.current
  val cellInput = remember { FocusedCellInput() }
  // Plain `var`s read at tap time, so they are set straight rather than remembered: the row's
  // `type` builds its rules from these, and a stale pair is the bug they replaced.
  cellInput.autoClosingBrackets = editorSettings.autoClosingBrackets
  cellInput.indentUnit = editorSettings.indentUnit
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  // Remembered here rather than inside the Scaffold's slot, because [ExportReportSnackbar] posts
  // to it from outside that slot.
  val exportReports = remember { SnackbarHostState() }
  // `saveBeforeClose` (`docmanager/src/dialogs.ts:155`): a dirty notebook asks before it leaves,
  // whichever way out was taken - the dismiss on its bar (the lambda handed back), system Back,
  // or close-and-shutdown, whose kernel question runs first and whose continuation is this same
  // close. Declared *before* the contextual handlers below, whose states the gate's own Back
  // handler stands down for. The `gone` path stays outside the gate: a deleted file has nothing
  // a save could put back.
  val closeOrAsk = SaveBeforeCloseGate(
    dirty = state.dirty,
    backAsks = state.backAsksToClose(),
    title = state.title,
    saveNow = viewModel::saveNow,
    discardChanges = viewModel::discardChanges,
    onClose = onBack,
  )
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
  // **The table of contents is not drawn here.** It is a sidebar panel of the shell's
  // (`shell.add(toc, 'left', { rank: 400 })`), which this entry cannot reach - so the headings are
  // published for it (see [outline]) and the notebook keeps its whole width. Where the shell has no
  // side area, [onShowTableOfContents] is null and the notebook's own menu shows the overlay.
  Box(modifier = modifier.fillMaxSize()) {
    // One list for every surface that reads it - the panel, the numbers on the notebook's own
    // headings, and the jump - so a heading cannot be numbered one way here and another there.
    val tocHeadings = state.tableOfContentsHeadings(editorSettings.tocIncludeOutput)
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
    LandingOnDefinition(state, viewModel.edits, listState, exportReports)
    LandingOnAMatch(reveal, state, viewModel.edits, listState, onRevealHandled)
    // A rename the server refused - jedi's own "no symbol" or the slice guard's - said once
    // per ask, where the definition miss is said: `notificationService` in the reference's
    // `rename.ts`. The tick and not the message is the key, so a second same refusal is seen.
    LaunchedEffect(state.renameOutcome) {
      state.renameOutcome?.let { exportReports.showSnackbar(it.message) }
    }
    // **A cell about to be typed into has to be on screen**, which is the reference's `_ensureFocus`
    // (`packages/notebook/src/widget.ts:2885-2905`) - see [cellBeingEditedIndex] for the rule and
    // for what its absence cost. Keyed on the cell and the mode rather than on the document, so
    // typing into the cell does not re-scroll on every keystroke.
    //
    // Guarded by [isFullyVisible] exactly as the search's scroll is, and for the reference's own
    // reason: `if (activeCell.inViewport) focus() else scrollToItem(...)`. A cell already in front
    // of the reader must not be yanked.
    LaunchedEffect(state.mode, state.selectedCellId, state.cellsOnScreen.size) {
      val index = cellBeingEditedIndex(state.mode, state.selectedCellId, state.cellsOnScreen)
        ?: return@LaunchedEffect
      if (!listState.isFullyVisible(index)) listState.animateScrollToItem(index)
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
    // The document hands its structure to the shell's side panel, and takes it back when it closes:
    // `toc.model = model` assigned from `labShell.currentChanged`, with the model disposed on
    // `widget.disposed` (`packages/toc-extension/src/index.ts:316,341-343,353`). The panel is a
    // different `NavEntry` and cannot reach this one's `EditorViewModel`, which is why anything
    // crosses at all - and why nothing is derived twice: this publishes what the notebook already
    // draws from.
    //
    // `rememberUpdatedState` keeps the published lambda stable while still calling the current
    // [jumpToHeading], which closes over this composition's `listState` and is rebuilt every frame.
    val jump by rememberUpdatedState(jumpToHeading)
    LaunchedEffect(outline, state.title, tocHeadings, editorSettings) {
      outline.publish(DocumentOutline(state.title, tocHeadings, editorSettings.tocNumbering()) { jump(it) })
    }
    // Its own effect rather than an `onDispose` on the one above, which would clear and republish
    // on every keystroke in a heading. This runs once, when the editor entry goes.
    DisposableEffect(outline) { onDispose { outline.clear() } }
    // [outline]'s own seam, crossed for the debugger's side panel - published on every change
    // rather than once, since a stop (and a continue that clears it) can arrive at any time the
    // panel is on screen, not only when the editor entry is built.
    LaunchedEffect(debugPanel, state.debugStack) {
      debugPanel.publish(
        state.debugStack,
        viewModel.debugger::expand,
        viewModel.debugger::selectFrame,
        viewModel.debugger::evaluate,
      )
    }
    DisposableEffect(debugPanel) { onDispose { debugPanel.clear() } }
    // The title's row is not free on a phone, so it is not permanent either - see
    // [titleCollapseFraction]. Derived from the list the cells are drawn from rather than from a
    // `nestedScroll` connection, so there is one answer to "where is the document" and not two.
    // **Both collapsing rows, not just the title.** `titleCollapseFraction` takes the region's
    // height as an argument, so a breadcrumb below the title widens what is passed rather than
    // needing a second fraction - and without this the bar would lose ~96dp over 48dp of scroll.
    val crumbs = ancestorCrumbsFor(state.notebookId, ROOT_LABEL)
    val rowHeight = LocalMinimumInteractiveComponentSize.current
    val collapsingRows = if (crumbs.isEmpty()) 1 else 2
    val stripPx = with(LocalDensity.current) { (rowHeight * collapsingRows).toPx() }
    val titleCollapse by remember(listState, stripPx) {
      derivedStateOf {
        titleCollapseFraction(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, stripPx)
      }
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
      // **The page a document is drawn on, stated rather than inherited.** `Scaffold`'s default is
      // `colorScheme.background`, which this app maps onto `editor.background` - the right colour
      // by way of a role that could be re-mapped, and `ColorSchemeTest` asserts only that a role
      // is *somewhere* on the ramp, never which position. VS Code separates a part's identity from
      // a surface's elevation, and this is the document's own id (`docs/DECISIONS.md`, "The
      // palette states its colours; it does not derive them from a Material role").
      containerColor = editorPalette.editorBackground,
      topBar = {
        Column {
          EditorTopBars(
            state = state,
            viewModel = viewModel,
            tocHeadings = tocHeadings,
            tocNumbering = editorSettings.tocNumbering(),
            onShowTableOfContents = onShowTableOfContents,
            onShowDebugger = onShowDebugger,
            onHeadingSelected = jumpToHeading,
            onBack = closeOrAsk,
            twoPane = twoPane,
            titleCollapse = titleCollapse,
            onGoToFolder = onGoToFolder,
            onOpenConsole = onOpenConsole,
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
          // `notebook:close-and-shutdown` awaits the kernel's end and then *closes* - so what it
          // gets is the gated close, and a dirty notebook is asked the same question here that the
          // dismiss asks, after the shutdown has already run (`index.ts:3189-3213`'s own order).
          onBack = closeOrAsk,
          twoPane = twoPane,
          onOpenConsole = onOpenConsole,
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
          onToggleBreakpoint = viewModel.debugger::toggle,
          onSetCondition = viewModel.debugger::setCondition,
          completionsAt = viewModel.kernel.asks::completionsAt,
          hoverAt = viewModel.kernel.asks::hoverAt,
          signatureHelpAt = viewModel.kernel.asks::signatureHelpAt,
          onCellAction = viewModel::cellAction,
          onCellFocusChanged = viewModel::cellFocusChanged,
          onToggleSelected = viewModel.selection::extendTo,
          onMarkdownEditRequested = viewModel::markdownEditRequested,
          cellInput = cellInput,
          autoClosingBrackets = editorSettings.autoClosingBrackets,
          indentUnit = editorSettings.indentUnit,
          lineNumbersShown = editorSettings.lineNumbersShown,
          codeFontSizeSp = editorSettings.codeFontSizeSp,
          wrapEnabled = editorSettings.codeWrapEnabled,
          completionEnabled = editorSettings.completionEnabled,
          tocNumbering = editorSettings.tocNumbering(),
          tocHeadings = tocHeadings,
          listState = listState,
          onCaretLanded = viewModel.edits::caretLanded,
          modifier = Modifier.weight(1f),
        )
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
internal fun LazyListState.isFullyVisible(index: Int): Boolean {
  val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
  return item.offset >= layoutInfo.viewportStartOffset &&
    item.offset + item.size <= layoutInfo.viewportEndOffset
}

/**
 * Whether system Back reaches the close gate at all: the contextual handlers own it while any of
 * their states is up, and as the later-registered callbacks they answer first - so a `dirty`
 * flip while the find bar is held must not let this one overtake them.
 */
private fun EditorUiState.backAsksToClose(): Boolean = dirty && mode != CellMode.EDIT && !selecting && search == null
