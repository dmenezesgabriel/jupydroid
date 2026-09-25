package com.jupydroid.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.model.NOTEBOOK_SUFFIX
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.app.ui.console.ConsoleScreen
import com.jupydroid.app.ui.console.ConsoleViewModel
import com.jupydroid.app.ui.editor.EditorScreen
import com.jupydroid.app.ui.editor.NotebookKernelSession
import com.jupydroid.app.ui.fileeditor.FileEditorScreen
import com.jupydroid.app.ui.launcher.LauncherScreen
import com.jupydroid.app.ui.launcher.LauncherViewModel
import com.jupydroid.app.ui.settings.SettingsScreen
import com.jupydroid.app.ui.shell.SidePanelRail
import com.jupydroid.app.ui.shell.trailingBorder
import com.jupydroid.app.ui.terminal.TerminalPaneScreen
import kotlinx.coroutines.launch

/**
 * Opening a document, wherever it was opened from: the right editor, and a line in Recents.
 *
 * **One opener rather than one per screen.** Home knew which of its rows was a notebook and the
 * explorer worked it out from the suffix, so the same rule lived in two places and only one of
 * them could record a recent. The reference has a single `docmanager:open` for the same reason -
 * the path decides the factory, and recents are recorded once, from the widget manager's
 * `activate-request` hook (`packages/docmanager/src/widgetmanager.ts:268-285`).
 *
 * A `.ipynb` goes to the notebook editor and anything else to the plain one, which is the
 * reference's own split: the notebook factory claims its extension and the file editor is the
 * default for the rest (`packages/fileeditor`).
 *
 * Recorded on [AppContainer.applicationScope], not on a screen's: the write must finish even if
 * the tap that caused it is the last thing that screen does.
 */
@Composable
private fun rememberDocumentOpener(backStack: MutableList<NavKey>, appContainer: AppContainer): (String) -> Unit =
  remember(backStack, appContainer) {
    { path ->
      if (path.endsWith(NOTEBOOK_SUFFIX, ignoreCase = true)) {
        backStack.openNotebook(path)
      } else {
        backStack.openFile(path)
      }
      appContainer.applicationScope.launch { appContainer.recentDocuments.recordOpening(path) }
    }
  }

/**
 * `terminal:create-new`, adapted: the reference reaches it from the Launcher tab or the File
 * menu, and this app has neither until `docs/PLAN.md`'s P6d gives it one. So this is the
 * explorer's own provisional reach point - a second icon beside the New notebook/file/folder
 * menu rather than a fourth item inside it, since that menu's own KDoc already states parity
 * with the reference's New widget (exactly three, none of them a terminal).
 *
 * A separate `rememberCoroutineScope` rather than [AppContainer.applicationScope]: opening a
 * terminal only matters while this screen exists to navigate away from, which is
 * `architecture.md`'s own line for what belongs on which scope - unlike recording a recent or
 * flushing an edit, nothing here has to outlive the composition that asked for it.
 */
@Composable
private fun rememberNewTerminal(backStack: MutableList<NavKey>, appContainer: AppContainer): (String) -> Unit {
  val scope = rememberCoroutineScope()
  return remember(backStack, appContainer, scope) {
    { cwd ->
      scope.launch {
        val session = appContainer.terminalGateway.open(cwd.ifEmpty { null })
        backStack.openTerminal(session.name)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainNavigation(
  appContainer: AppContainer,
  importTitle: String?,
  onImportConsumed: () -> Unit,
  // Passed in rather than read at the point of use, for the reason `CellActionsTest` records:
  // `currentWindowAdaptiveInfo()` measures the *activity's* window, which a
  // `DeviceConfigurationOverride` around a composable does not change - so a test that tried to
  // force a width would drive one branch twice while appearing to cover both.
  twoPane: Boolean = isWindowWideEnoughForTwoPanes(),
  /**
   * Where the open document publishes its structure for the shell's side panel to draw.
   *
   * Remembered here rather than owned by [AppContainer], and the lifetime is the argument: a
   * process-scoped holder would survive a rotation still carrying a jump closed over a disposed
   * composition's `LazyListState`. Passed in so a test can read what a real editor entry published.
   */
  outline: OpenDocumentOutline = remember { OpenDocumentOutline() },
  /** [OpenDocumentOutline]'s own seam, crossed for the debugger's side panel - see [OpenDebugStack]. */
  debugPanel: OpenDebugStack = remember { OpenDebugStack() },
  /**
   * Where the open document is reached by path, for something that is not its own screen - today,
   * the search panel's replace (P2x-5).
   *
   * Remembered here rather than owned by [AppContainer] for [OpenDocumentOutline]'s reason: it
   * holds a live ViewModel, and a process-scoped holder would outlive the composition that made it.
   */
  openDocuments: OpenDocuments = remember { OpenDocuments() },
) {
  // The explorer is what the app opens on. Recents was a scan of one directory and is now a
  // section of this screen, so there is no second surface left to start from - which is also the
  // reference's arrangement: JupyterLab has one file browser and no separate recents screen.
  val backStack = rememberNavBackStack(Explorer)
  // Which side panel is showing, or null when the area is collapsed - see [SidePanel.chosen].
  // Saved rather than remembered, because a rotation is what most often brings the rail into range
  // in the first place, and `SidePanel?` round-trips through a Bundle unaided.
  var sidePanel by rememberSaveable { mutableStateOf<SidePanel?>(SidePanel.FILES) }
  // Where the open notebook lives *now*. The navigation key keeps the path it was opened under,
  // because replacing it would re-create the entry and discard the editor's state; a rename is
  // reported here instead. `_contextsForPath` does the same - it filters live contexts by their
  // current path, never by the one a widget was created with.
  var openNotebookId by remember { mutableStateOf<String?>(null) }
  // The session `NotebookKernel.attachConsole()` minted for whichever notebook's Console was last
  // opened - `outline`/`debugPanel`/`openDocuments`' own shape, the side channel a live object
  // needs to flow between two `NavEntry`s that own separate `ViewModelStore`s. Plain `remember`,
  // not saveable, for `openNotebookId`'s own reason: a `NotebookKernelSession` cannot survive a
  // process death any more than an `EditorViewModel`'s own kernel connection can, so there is
  // nothing this could round-trip through a Bundle. A Console entry restored without one composes
  // no screen - the same silent gap every other holder here already leaves unaddressed rather than
  // one this row invents a fallback for.
  var consoleSession by remember { mutableStateOf<NotebookKernelSession?>(null) }
  // The directory the explorer is showing, reported up rather than pulled - the launcher's `cwd`,
  // which the reference keeps bound to the filebrowser's path (`ILauncher.cwd` follows the browser
  // widget's). Saveable so a rotation does not send the launcher back to the root the explorer
  // left.
  var browsedDirectory by rememberSaveable { mutableStateOf("") }

  /**
   * Whether the launcher on top of the stack got there by `maybeCreate` rather than by a tap.
   *
   * The fill is a *placeholder for an empty detail area* - and at one pane there is no detail area,
   * so a window that narrows onto it pops it back to the panel the user was actually looking at
   * (which is `aWindowThatNarrowsOntoSearchKeepsIt`'s contract: a rotation mid-search keeps the
   * search). A launcher the user opened from the bar or rail is a real destination and survives,
   * exactly as an open document does - the flag is the whole difference between the two.
   */
  val launcherIsFill = remember { mutableStateOf(false) }

  val openDocument = rememberDocumentOpener(backStack, appContainer)
  val newTerminal = rememberNewTerminal(backStack, appContainer)
  val openLauncher: () -> Unit = {
    launcherIsFill.value = false
    backStack.openLauncher()
  }
  // Where the browser has been asked to go from *outside* its own entry - the open document's
  // breadcrumb (P2w-1). Plain hoisted state rather than an `OpenDocumentOutline`-style holder,
  // because that one exists for something flowing **sideways** between two entries and this flows
  // down from their common parent: the editor asks here, and here is already above the browser.
  var browseRequest by remember { mutableStateOf<String?>(null) }
  // The match a search result was opened from, on its way to the editor that has to land on it.
  // **Beside the route rather than inside it**, which `browseRequest` is the precedent for and the
  // `FileEditor` key is the reason: `NavEntry.contentKey` defaults to the key's `toString()`, so a
  // match carried in the key would make the same file opened from search and from the browser two
  // entries with two `ViewModelStore`s - two editors on one document.
  var revealRequest by remember { mutableStateOf<MatchedLine?>(null) }
  // Which row the pane beside the browser belongs to, and **only while there is a pane beside it**.
  // In one window the browser is on screen exactly when nothing is open, so a marked row would
  // point at a pane that is not there. The notebook's path comes from `openNotebookId` rather than
  // from the key, because a rename moves the file and the key keeps the path it was opened under.
  val openDocumentPath = when (val top = backStack.lastOrNull()) {
    is Editor -> openNotebookId
    is FileEditor -> top.path
    else -> null
  }.takeIf { twoPane }
  // The scene strategy measures the window for itself, so a caller that was *told* how many panes
  // to draw has to be able to overrule it - otherwise `twoPane` would decide the row marking above
  // and not the layout, and the two would disagree. Same boolean, both halves.
  val measured = paneScaffoldDirective()
  // **A one-pane window has no side area, so its panel *is* the window** - and until P2y that meant
  // the browser and nothing else, because the rail is drawn only `if (twoPane)` below while
  // `sidePanel` is `rememberSaveable`. A tablet rotated to portrait (1280dp landscape is two panes,
  // 800dp portrait is one) kept drawing the panel it had with no rail to switch away from it and no
  // way back to the files but killing the app (P2x-2b). The bottom bar leaves Files and Search, so
  // only the table of contents still falls back - it has no item there, and its own route at this
  // width is the overlay the notebook's menu opens.
  //
  // An override rather than a reset, so rotating back restores the panel the user had chosen.
  //
  // The two are held apart because the bar needs the **non-null** answer to mark its selection,
  // where `showingPanel` still has a collapsed state to carry for the two-pane case.
  val onePanePanel = sidePanel.showingAtOnePane()
  val showingPanel = if (twoPane) sidePanel else onePanePanel
  // **A collapsed side area is a one-pane layout**, which is what `collapseLeft` does there:
  // the panel goes and the document area takes the width, while the activity bar stays
  // (`packages/application/src/shell.ts:2383-2394`). So the collapse is spelled here rather
  // than inside the pane, and the notebook actually gains the 360dp.
  val directive = if (twoPane && showingPanel != null) measured else measured.forcedToOnePane()

  // The VIEW intent, which used to be Home's because Home was the first screen. It is an
  // app-level act rather than a screen's - nothing about it belongs to the browser - so it lives
  // here now. Keyed on the title so a second import in the same process is not swallowed.
  //
  // **Opened, not merely listed.** Home added a row and left it there, which was defensible when
  // Home was a list of notebooks; an ACTION_VIEW intent asks to *view* the document, and opening
  // it is also what makes the result deterministic - a row only appears once the browser has
  // listed again, and the create races that listing on a cold start. Opening also records it as a
  // recent, so it is on the browser's first section when the editor is left.
  LaunchedEffect(importTitle) {
    importTitle?.let { title ->
      runCatching { appContainer.notebookRepository.createNotebook(title, DEFAULT_KERNEL_ID) }
        .onSuccess { openDocument(it.id) }
      onImportConsumed()
    }
  }

  // `maybeCreate` and the fill's provenance - extracted with the rest of the launcher lifecycle,
  // for the same reason `openLaunched` below is: a lambda's branches count toward this function's
  // complexity budget.
  LauncherFillEffect(backStack, appContainer.launcherSettings, twoPane, launcherIsFill)

  // **A breadcrumb segment, and one copy of what it does.** JupyterLab's `this._model.cd(destination)`
  // (`packages/filebrowser/src/crumbs.ts:415`) - so the browser has to be *showing* for the
  // navigation to be visible, and which half of the shell that needs differs by layout. Beside the
  // document the side area may be drawing the table of contents instead; in one window the browser
  // is not composed at all until the open entry goes.
  //
  // **Both documents take this same lambda** - the notebook since P2w-1, the plain file editor
  // since P7b - because two copies of a branch that has to agree is a defect waiting to be written,
  // which is the reason `ShellSidePane` stopped being handed the width fact twice in P7a-2.
  val goToFolder: (String) -> Unit = { folder ->
    browseRequest = folder
    if (twoPane) sidePanel = SidePanel.FILES else backStack.removeLastOrNull()
  }

  // A hoisted lambda like `goToFolder`, for the same reason: the card-to-document mapping is the
  // navigation's own, not the launcher's - and the `when` inside it must not count toward this
  // function's complexity budget.
  val openLaunched = rememberLaunchHandler(backStack, openDocument) { consoleSession = it }

  // **Back reaches the start destination before it leaves the app.** Android's own principle: the
  // start destination *"is also the last screen the user sees when they return to the launcher
  // after pressing the Back button"* (`developer.android.com/guide/navigation/principles`). The
  // browser is this app's root, so backing out of a one-pane Search selects Files rather than
  // exiting. `NavDisplay` cannot do it - there is one entry on the stack and nothing to pop - and
  // it is deliberately off while a document is open, where Back belongs to the document.
  BackHandler(enabled = !twoPane && onePanePanel != SidePanel.FILES && backStack.lastOrNull() == Explorer) {
    sidePanel = SidePanel.FILES
  }

  // **The activity bar is beside the whole shell, not inside the side area.** The reference's
  // `hboxPanel` holds `leftHandler.sideBar` next to the split that contains both the panel and the
  // document area (`packages/application/src/shell.ts:468-477`), which is what lets the area
  // collapse to the bar alone. `docs/PLAN.md` said "the left edge of the list pane"; reading the
  // shell corrected it.
  //
  // Only where there is room for two panes: the bar on a compact window is P2o-4's, and a rail
  // there would spend a quarter of a 320dp screen on chrome.
  Row(modifier = Modifier.fillMaxSize()) {
    if (twoPane) {
      // The seam between the bar and whatever it is showing, **drawn by the bar** - which is where
      // both references put it, and the reason it is no longer a `VerticalDivider` beside it: the
      // width is budgeted into the bar itself,
      // `calc(var(--jp-private-sidebar-tab-width) + var(--jp-border-width))`
      // (`packages/application/style/sidepanel.css:41-44`), and VS Code's activity bar strokes its
      // own `border-right` (`parts/activitybar/media/activitybarpart.css:30-34`). Without it the
      // rail and the panel are one white field and the bar reads as part of the panel's content -
      // measured, and one of the three things the first run's screenshot showed and its assertions
      // could not.
      //
      // **And it is `activityBar.border` now, not whatever `outlineVariant` happened to be.** The
      // divider took `DividerDefaults.color`, which resolves to `panel.border` - the same hex as
      // this id in both default themes, a different id, and nothing holding it (P7a-2).
      SidePanelRail(
        selected = sidePanel,
        onChoose = { sidePanel = sidePanel.chosen(it) },
        onOpenLauncher = openLauncher,
        modifier = Modifier.trailingBorder(chromePalette.activityBarBorder),
      )
    }
    NavDisplay(
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      // NavDisplay's default is the saveable-state decorator ALONE, so without this the
      // ViewModelStoreOwner inside an entry is the Activity: viewModel {} keys on the class name
      // and hands the second notebook the first one's ViewModel, id and all.
      //
      // Order is load-bearing. decorateEntry folds right, so the first in the list is the
      // outermost, and the ViewModel decorator has to sit inside the saveable one - it reads the
      // SavedStateRegistryOwner from its surroundings.
      entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
      ),
      // Both entries stay entries; this only decides whether they are drawn one at a time or beside
      // each other. `ListDetailSceneStrategy` collects the matching entries into one scene and draws
      // each one's own `Content()`, so every decorator stays where it was - which is the property
      // U7's hand-written strategy existed for and the reason `NavigableListDetailPaneScaffold` is
      // still not what this uses: the scaffold puts both panes inside *one* `NavEntry`.
      //
      // With one partition it declines the scene (`shouldHandleSinglePaneLayout` is false by
      // default), and NavDisplay falls through to its single-pane default.
      //
      // **`PopLatest`, not the library's default, and it is not a preference.** With both panes
      // expanded, `PopUntilScaffoldValueChange` looks for an earlier destination whose scaffold
      // value differs and never finds one - the list alone still lays out as two panes - so
      // `getPreviousDestinationIndex` returns -1, `previousEntries` comes back empty, and the scene
      // pops *every* entry. Measured on the tablet: back from a notebook left the app for the home
      // screen (`evidence/p1e-list-detail-scene/`). `PopLatest` pops exactly one, which is what
      // `TwoPaneScene.previousEntries = entries.dropLast(1)` did.
      sceneStrategy = rememberListDetailSceneStrategy(
        backNavigationBehavior = BackNavigationBehavior.PopLatest,
        directive = directive,
      ),
      entryProvider =
      entryProvider {
        entry<Explorer>(metadata = ListDetailSceneStrategy.listPane()) {
          ShellSidePane(
            showingPanel = showingPanel,
            onePanePanel = onePanePanel,
            // The one width fact, passed once. It used to arrive twice - as `showingPanel` and as a
            // `barSelection` that was `if (twoPane) null else onePanePanel` - and P7a-2 needed a
            // third reading of it for the side bar's own border. Two parameters that have to agree
            // is a defect waiting to be written; the pane decides from the fact itself.
            twoPane = twoPane,
            onChoosePanel = { sidePanel = it },
            appContainer = appContainer,
            outline = outline,
            debugStack = debugPanel,
            openDocuments = openDocuments,
            openDocument = openDocument,
            openDocumentPath = openDocumentPath,
            browseRequest = browseRequest,
            onBrowseRequestHandled = { browseRequest = null },
            onRevealMatch = { match -> revealRequest = match },
            onNewTerminal = newTerminal,
            onOpenSettings = { backStack.openSettings() },
            onOpenLauncher = openLauncher,
            onBrowsedDirectory = { browsedDirectory = it },
          )
        }
        entry<Settings>(metadata = ListDetailSceneStrategy.detailPane()) {
          SettingsScreen(
            editorSettingsRepository = appContainer.editorSettings,
            themeSettingsRepository = appContainer.themeSettings,
            applicationScope = appContainer.applicationScope,
            onBack = { backStack.removeLastOrNull() },
            // The one width fact this screen reads, and it decides one thing: whether leaving it
            // is drawn as going back or as closing it (`DocumentDismissAction`).
            twoPane = twoPane,
          )
        }
        entry<FileEditor>(metadata = ListDetailSceneStrategy.detailPane()) { file ->
          FileEditorScreen(
            path = file.path,
            workspaceRepository = appContainer.workspaceRepository,
            applicationScope = appContainer.applicationScope,
            editorSettingsRepository = appContainer.editorSettings,
            onBack = { backStack.removeLastOrNull() },
            // The one width fact this screen reads, and it decides one thing: whether leaving the
            // document is drawn as going back or as closing it (`DocumentDismissAction`).
            twoPane = twoPane,
            onGoToFolder = goToFolder,
            reveal = revealRequest,
            onRevealHandled = { revealRequest = null },
            openDocuments = openDocuments,
          )
        }
        entry<Editor>(metadata = ListDetailSceneStrategy.detailPane()) { editor ->
          EditorScreen(
            notebookId = editor.notebookId,
            notebookRepository = appContainer.notebookRepository,
            workspaceRepository = appContainer.workspaceRepository,
            kernelGateway = appContainer.kernelGateway,
            kernelDebugger = appContainer.kernelDebugger,
            notebookExporter = appContainer.notebookExporter,
            documentSharer = appContainer.documentSharer,
            applicationScope = appContainer.applicationScope,
            activeCellRuns = appContainer.activeCellRuns,
            notebookBreakpoints = appContainer.notebookBreakpoints,
            editorSettingsRepository = appContainer.editorSettings,
            outline = outline,
            // `toc:show-panel` where there is a side area to activate, and null where there is not -
            // which is what puts the overlay up on a compact window instead.
            onShowTableOfContents = { sidePanel = SidePanel.TABLE_OF_CONTENTS }.takeIf { twoPane },
            debugPanel = debugPanel,
            onShowDebugger = { sidePanel = SidePanel.DEBUGGER }.takeIf { twoPane },
            onGoToFolder = goToFolder,
            onBack = { backStack.removeLastOrNull() },
            // The one width fact this screen reads, and it decides one thing: whether leaving the
            // document is drawn as going back or as closing it (`DocumentDismissAction`).
            twoPane = twoPane,
            onOpenConsole = { session ->
              consoleSession = session
              backStack.openConsole(editor.notebookId)
            },
            onNotebookMoved = { moved -> openNotebookId = moved },
            // The same request the file editor takes, because the panel does not know - and should
            // not - which of the two a path opens: `docmanager:open` decides the editor from the
            // path alone, and the match travels beside it either way.
            reveal = revealRequest,
            onRevealHandled = { revealRequest = null },
            openDocuments = openDocuments,
          )
        }
        entry<Terminal>(metadata = ListDetailSceneStrategy.detailPane()) { terminal ->
          TerminalPaneScreen(
            session = TerminalSession(terminal.name),
            terminalGateway = appContainer.terminalGateway,
            onBack = { backStack.removeLastOrNull() },
            // The one width fact this screen reads, and it decides one thing: whether leaving the
            // shell is drawn as going back or as closing it (`DocumentDismissAction`).
            twoPane = twoPane,
            terminalSettingsRepository = appContainer.terminalSettings,
          )
        }
        entry<Launcher>(metadata = ListDetailSceneStrategy.detailPane()) {
          LauncherScreen(
            cwd = browsedDirectory,
            viewModel = viewModel {
              LauncherViewModel(
                appContainer.notebookRepository,
                appContainer.workspaceRepository,
                appContainer.terminalGateway,
                appContainer.kernelGateway,
              )
            },
            onLaunched = openLaunched,
            onBack = launcherDismiss(backStack, twoPane),
            twoPane = twoPane,
          )
        }
        entry<Console>(metadata = ListDetailSceneStrategy.detailPane()) { console ->
          // Null only when this entry was restored without the live session that opened it - see
          // `consoleSession`'s own doc comment; there is nothing to compose against in that case.
          consoleSession?.let { session ->
            ConsoleScreen(
              title = console.notebookId,
              viewModel = viewModel { ConsoleViewModel(appContainer.kernelGateway, session) },
              onBack = { backStack.removeLastOrNull() },
              twoPane = twoPane,
            )
          }
        }
      },
    )
  }

  // The lone-launcher back-swallow - after NavDisplay on purpose, see its KDoc.
  LoneLauncherBackGuard(backStack, twoPane)
}
