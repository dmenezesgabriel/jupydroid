package com.jupydroid.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import com.jupydroid.app.OpenNotebookCells
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.data.fake.FakeWorkspaceWatcher
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.model.NotebookBreakpoints
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.DocumentSharer
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceFiles
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.domain.port.WorkspaceWatcher
import com.jupydroid.app.theme.editorPalette
import com.jupydroid.app.ui.FileChangedDialog
import com.jupydroid.app.ui.ROOT_LABEL
import com.jupydroid.app.ui.SaveBeforeCloseGate
import com.jupydroid.app.ui.ancestorCrumbsFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
  notebookId: String,
  notebookRepository: NotebookRepository,
  /** Read for the images and links a notebook's Markdown names, and written for an export. */
  workspaceRepository: WorkspaceRepository,
  /** Hears the file written by anything else. Defaulted for a test that drives this screen alone. */
  workspaceWatcher: WorkspaceWatcher = FakeWorkspaceWatcher(),
  /** Only listened to: a rename made beside this document moves it. Defaulted as [workspaceWatcher] is. */
  workspaceFiles: WorkspaceFiles = FakeWorkspaceFiles(),
  kernelGateway: KernelGateway,
  /** DAP over the same kernel session - the handshake `EditorViewModel.debugger` sends. */
  kernelDebugger: KernelDebugger,
  /**
   * The Space's language server - the completion source, asked before the kernel. Defaulted
   * the way [openDocuments] is, for the tests that drive this screen alone.
   */
  languageServer: LanguageServerPort = FakeLanguageServerPort(),
  /**
   * The Running panel's "a session ended" feed, so its shutdown lands this notebook on No
   * Kernel - `session.disposed`'s route in the reference. Defaulted as [languageServer] is.
   */
  kernelSessions: KernelSessions = FakeKernelSessions(),
  /**
   * The kernelspec catalogue the picker lists and the saved `kernelspec.name` resolves through -
   * `specsManager.specs` in the reference. Defaulted as [kernelSessions] is.
   */
  kernelSpecs: KernelSpecs = FakeKernelSpecs(),
  /** The entered Space, whose server runs this notebook's kernel. Defaulted as [kernelSpecs] is. */
  space: Space = BASE_SPACE,
  /** `notebook:export-to-format` - see [NotebookExport]. Not a kernel act. */
  notebookExporter: NotebookExporter,
  /** Where an exported file goes next, from the report that says it was written. */
  documentSharer: DocumentSharer,
  applicationScope: CoroutineScope,
  /** The app's count of runs in flight, which the idle backstop reads; see [ActiveCellRuns]. */
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
  /** Where this document publishes its cells and its insert - see [OpenNotebookCells]. */
  notebookCells: OpenNotebookCells,
  /** [onShowTableOfContents]'s own rule, for the debugger's side panel - see [SidePanel.DEBUGGER]. */
  onShowDebugger: (() -> Unit)?,
  /**
   * Whether a browser is drawn beside this notebook, which decides one thing here: whether the
   * debugger's bottom bar carries a variables toggle - see [EditorBottomBars]. How the document
   * is left is the tab strip's own act now (the ✕ at two panes, the arrow below).
   *
   * **A second reading of the width, and it is worth naming as one.** [onShowTableOfContents] is
   * null for the same underlying reason - there is no side area to activate - and the two are
   * derived from one `twoPane` at the single call site, so they cannot disagree there. They are not
   * folded because they answer different questions of different widgets: one is *"is there a panel
   * to ask"*, whose lambda differs in body and not only in presence, and this is *"is the browser
   * already on screen"*. The rule against two parameters is about one widget's state.
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
  /** A workspace link was tapped and named a file: open it, as the browser would (`openDocument`). */
  openDocument: (String) -> Unit = {},
  onBack: () -> Unit,
  /**
   * `notebook:create-console` - a Console session sharing this notebook's own kernel,
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
   * Null for every other way of opening a notebook, which is every way but a search result.
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
  /** Which of [notebookId]'s opens this screen's tab is - see `DocumentOpening`. */
  opening: Int = 0,
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
      kernelSessions,
      kernelSpecs,
      space,
      opening,
      workspaceWatcher,
      workspaceFiles,
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
  // whichever way out was taken - system Back (the handler the gate registers), the tab strip's
  // own gated close, or close-and-shutdown, whose kernel question runs first and whose
  // continuation is this same close. Declared *before* the contextual handlers below, whose
  // states the gate's own Back handler stands down for. The `gone` path stays outside the gate:
  // a deleted file has nothing a save could put back.
  val closeOrAsk = SaveBeforeCloseGate(
    dirty = state.dirty,
    backAsks = state.backAsksToClose(),
    title = state.title,
    saveNow = viewModel::saveNow,
    discardChanges = viewModel::discardChanges,
    onClose = onBack,
  )
  // Over the notebook, never instead of it: both versions are still there to choose between.
  if (state.saveConflict) {
    FileChangedDialog(
      state.title,
      viewModel.conflict::overwrite,
      viewModel.conflict::revert,
      viewModel.conflict::cancel,
    )
  }
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
  // by a test: no Compose test has an IME (the run before this).
  LaunchedEffect(state.selecting) { if (state.selecting) focusManager.clearFocus() }
  // The file behind this notebook was deleted, so there is nothing left to show: the reference
  // closes the widget from the manager (`deleteWidgets`), and this is the same close asked for by
  // the document, which is the only party that hears every delete.
  LaunchedEffect(state.gone) { if (state.gone) onBack() }
  LaunchedEffect(state.notebookId) {
    if (state.notebookId.isNotEmpty()) onNotebookMoved(state.notebookId)
  }
  // **The table of contents is not drawn here.** It is a sidebar panel of the shell's
  // (`shell.add(toc, 'left', { rank: 400 })`), which this entry cannot reach - so the headings are
  // published for it (see [outline]) and the notebook keeps its whole width. Where the shell has no
  // side area, [onShowTableOfContents] is null and the notebook's own menu shows the overlay.
  Box(modifier = modifier.fillMaxSize()) {
    // One list for every surface that reads it - the panel, the numbers on the notebook's own
    // headings, and the jump - so a heading cannot be numbered one way here and another there.
    val tocHeadings = state.tableOfContentsHeadings(editorSettings.tocIncludeOutput)
    CellRevealScroll(state, listState)
    LandingOnDefinition(state, viewModel.edits, listState, exportReports)
    LandingOnAMatch(reveal, state, viewModel.edits, listState, onRevealHandled)
    // A rename the server refused - jedi's own "no symbol" or the slice guard's - said once
    // per ask, where the definition miss is said: `notificationService` in the reference's
    // `rename.ts`. The tick and not the message is the key, so a second same refusal is seen.
    LaunchedEffect(state.renameOutcome) {
      state.renameOutcome?.let { exportReports.showSnackbar(it.message) }
    }
    val jumpToHeading: (String) -> Unit = { cellId ->
      // A heading is never hidden by its own section (CellHeadings.kt), but one nested inside an
      // ancestor's collapsed section is - and **the reference reveals it**, which an earlier
      // reading had the other way round. `expandParent` is wired to every active-cell change
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
    NotebookCellsPublication(notebookCells, viewModel.uiState, state.selectedCellId, viewModel.edits)
    // The breadcrumb's row is not free on a phone, so it is not permanent either - see
    // [collapsibleRegionFraction]. Derived from the list the cells are drawn from rather than from a
    // `nestedScroll` connection, so there is one answer to "where is the document" and not two.
    // The region the fraction slides is now the crumbs alone - the title row that shared it moved
    // to the tab strip, so a notebook at the workspace root has nothing left to collapse.
    val crumbs = ancestorCrumbsFor(state.notebookId, ROOT_LABEL)
    val rowHeight = LocalMinimumInteractiveComponentSize.current
    val collapsingRows = if (crumbs.isEmpty()) 0 else 1
    val stripPx = with(LocalDensity.current) { (rowHeight * collapsingRows).toPx() }
    val collapsibleRegion by remember(listState, stripPx) {
      derivedStateOf {
        collapsibleRegionFraction(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, stripPx)
      }
    }
    // A link in a cell or an output that names a workspace path opens it from here: this screen
    // knows where the notebook lives now and owns the snackbar a link to nothing is said on. A
    // `#heading` link lands where the table of contents' jump does, on the heading's cell.
    val jumpToHeadingAnchor = { fragment: String -> state.cellOfHeadingAnchor(fragment)?.also(jumpToHeading) != null }
    WorkspaceLinksFollowed(
      state.notebookId,
      workspaceRepository,
      openDocument,
      onGoToFolder,
      jumpToHeadingAnchor,
      exportReports,
    ) {
      Scaffold(
        // **The notebook consumes the keyboard's inset while the find bar is up, and nothing else
        // does.** Every other surface that can be on screen under an IME carries its own
        // `imePadding` - [SymbolRow] in edit mode, [NotebookBar] in command mode - so the cell list
        // has never had to. Searching is the first state with a soft keyboard and *no bottom bar*
        // (see [editorBottomBar]), and without this the `LazyColumn` lays out to the full window and
        // draws its lower half behind the keyboard: measured at `viewport=0..987` on the tablet with
        // the IME up, which made a match the arrows had just been sent to report as visible while
        // being under the keys (two runs before this).
        modifier = Modifier.fillMaxSize().then(if (state.search != null) Modifier.imePadding() else Modifier),
        // **The page a document is drawn on, stated rather than inherited.** `Scaffold`'s default is
        // `colorScheme.background`, which this app maps onto `editor.background` - the right colour
        // by way of a role that could be re-mapped, and `ColorSchemeTest` asserts only that a role
        // is *somewhere* on the ramp, never which position. VS Code separates a part's identity from
        // a surface's elevation, and this is the document's own id (ADR-0042).
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
              collapsibleRegion = collapsibleRegion,
              onGoToFolder = onGoToFolder,
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
        // an earlier reading assumed would work - is a separate `NavEntry`, and every entry here
        // owns its own `ViewModelStore` (`docs/architecture/runtime-views.md`), so it could reach
        // neither this notebook's `EditorViewModel` for the headings nor its `listState` to scroll.
        Row(modifier = Modifier.padding(innerPadding)) {
          // An image in a cell or an output that names a workspace path is read from beside the
          // notebook, wherever it lives now.
          WorkspaceImagesResolved(state.notebookId, workspaceRepository) {
            EditorContent(
              state = state,
              onRunCell = viewModel.kernel::runCell,
              onStopCell = viewModel.kernel::stopRunningCell,
              onAnswerInput = viewModel.kernel::answerInput,
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
              onAddCell = { kind -> scope.launch { viewModel.edits.appendCellInView(kind, listState) } },
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
    KernelPickerHost(state, viewModel.kernel)
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
 * Whether system Back reaches the close gate at all: the contextual handlers own it while any of
 * their states is up, and as the later-registered callbacks they answer first - so a `dirty`
 * flip while the find bar is held must not let this one overtake them.
 */
private fun EditorUiState.backAsksToClose(): Boolean = dirty && mode != CellMode.EDIT && !selecting && search == null

/**
 * The `selectKernel` dialog and the condition that raises it - lifted out of [EditorScreen]
 * because the branchcount the pair adds there is a complexity gate's worth on its own.
 *
 * `Context.initialize` -> `selectKernel` (`docregistry/src/context.ts:599-603`): a kernelspec the
 * file names and the catalogue has never heard of opens the picker as soon as the document is
 * ready, and again for each refused run (`cellexecutor.ts:69`). The owe
 * [EditorUiState.kernelPickRequired] counts is the key rather than the groups: a second owe while
 * the first is still up must still be a change. The dialog draws while its groups are in state -
 * see [KernelPickerDialog] for the reference's shape.
 */
@Composable
private fun KernelPickerHost(state: EditorUiState, kernel: NotebookKernel) {
  LaunchedEffect(state.kernelPickRequired) {
    if (state.kernelPickRequired > 0) kernel.picker.open()
  }
  state.kernelPicker?.let { groups ->
    KernelPickerDialog(
      groups = groups,
      notebookTitle = state.title,
      noKernel = !state.kernelConnected,
      kernelDisplayName = state.kernelDisplayName,
      onPick = kernel.picker::pick,
      onDismiss = kernel.picker::dismiss,
    )
  }
}
