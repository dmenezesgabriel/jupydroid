package com.jupydroid.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.import.SharedNotebook
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.app.ui.console.ConsoleScreen
import com.jupydroid.app.ui.console.ConsoleViewModel
import com.jupydroid.app.ui.editor.EditorScreen
import com.jupydroid.app.ui.fileeditor.FileEditorScreen
import com.jupydroid.app.ui.launcher.LauncherScreen
import com.jupydroid.app.ui.launcher.LauncherViewModel
import com.jupydroid.app.ui.settings.LicencesScreen
import com.jupydroid.app.ui.settings.LicencesViewModel
import com.jupydroid.app.ui.settings.SettingsScreen
import com.jupydroid.app.ui.shell.SidePanelRail
import com.jupydroid.app.ui.shell.TabbedDocumentPane
import com.jupydroid.app.ui.shell.anyOpenDocumentDirty
import com.jupydroid.app.ui.shell.rememberMainAreaCloseGate
import com.jupydroid.app.ui.shell.saveAllOpenDocuments
import com.jupydroid.app.ui.shell.trailingBorder
import com.jupydroid.app.ui.spaces.SpacesHomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainNavigation(
  appContainer: AppContainer,
  sharedNotebook: SharedNotebook?,
  onImportConsumed: () -> Unit,
  importReports: SnackbarHostState = remember { SnackbarHostState() },
  // Passed in rather than read at the point of use, for the reason `CellActionsTest` records:
  // a test states the branch it means, and a `ForcedSize` cannot isolate it anyway - the override
  // sizes layout and measure together (adaptive 1.3.0 reads the container through
  // `LocalDensity.current`, https://issuetracker.google.com/issues/358626778), so "wide window,
  // told one pane" is `forcedToOnePane`'s to draw.
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
  /** [OpenDocumentOutline]'s own seam, crossed for a contributed panel - see [OpenNotebookCells]. */
  notebookCells: OpenNotebookCells = remember { OpenNotebookCells() },
  /**
   * Where every open document is reached by path, for something that is not its own screen -
   * today, the search panel's replace.
   *
   * Retained by the activity, as the documents it holds are - see [RetainedOpenDocuments].
   */
  openDocuments: OpenDocuments = rememberRetainedOpenDocuments(),
) {
  // The Spaces home is what the app opens on: a Space is chosen and managed before it is entered,
  // never from a panel inside one - see [SpacesHome]. Entering one pushes the explorer, the
  // workspace's own root, which has no separate recents screen beside it, as JupyterLab has none.
  val backStack = rememberNavBackStack(SpacesHome)
  // The entered Space's ports, off the browser's key: every workspace entry binds to that one Space.
  val spacePorts = appContainer.portsOf(backStack.enteredSpaceId())
  // Which side panel is showing, or null when the area is collapsed - see [SidePanel.chosen].
  // Saved rather than remembered, because a rotation is what most often brings the rail into range
  // in the first place, and `SidePanel?` round-trips through a Bundle unaided.
  var sidePanel by rememberSaveable { mutableStateOf<ShownPanel?>(ShownPanel.Shell(SidePanel.FILES)) }
  // The strip's opening order, live paths and live titles - see [RetainedTabStrip] for each.
  val tabStrip = rememberRetainedTabStrip()
  val openDocumentPaths = tabStrip.liveDocumentPaths
  val openTabOrder = tabStrip.openingOrder
  val orderedTabs = backStack.mainAreaTabs().orderedAsOpened(openTabOrder)
  val liveTabTitles = tabStrip.liveTabTitles
  LaunchedEffect(orderedTabs) { reconcileOpenTabs(orderedTabs, openTabOrder, liveTabTitles, openDocumentPaths) }
  /**
   * `application:close`'s dirty gate, mounted here rather than inside an entry - the Running
   * panel's row close reaches it too, and the dialog must outlive whichever tab opened the ask.
   * [orderedTabs] is the strip's opening order, so a current tab's close re-selects the sibling
   * the eye expects.
   */
  val closeGate = rememberMainAreaCloseGate(backStack, orderedTabs, openDocuments, openDocumentPaths)
  // The Running panel's Open Tabs section - `labShell.widgets('main')` in the strip's own order,
  // titled by the same `title.label` the strip draws.
  val runningTabs = orderedTabs.map { tab ->
    RunningTab(tab, tab.runningTabTitle(openDocuments, openDocumentPaths, liveTabTitles))
  }
  // One strip for the six detail entries, so an entry states its screen and nothing else - the
  // strip's model is the stack and these four holders, identical for all six.
  val openDocument = rememberDocumentOpener(backStack, appContainer, openDocuments)
  val tabbedPane: @Composable (@Composable () -> Unit) -> Unit = { content ->
    TabbedDocumentPane(
      backStack,
      openDocuments,
      openDocumentPaths,
      orderedTabs,
      liveTabTitles,
      spacePorts.workspaceRepository,
      spacePorts.workspaceFiles,
      // The one width fact the strip reads: below two panes it leads with the Up affordance,
      // because the document is the whole window there and leaving it goes back.
      twoPane = twoPane,
      onCloseTab = closeGate.closeTab,
      onLeaveWorkspace = closeGate.leaveWorkspace,
      onOpenDocument = openDocument,
      documentCheckpoints = spacePorts.documentCheckpoints,
      documentContributions = appContainer.documentContributions,
      onShowSidePanel = { sidePanel = it },
      content = content,
    )
  }
  // The open Console's session and the count that titles the next one - see [RetainedConsole].
  val retainedConsole = rememberRetainedConsole()
  // The directory the explorer is showing, reported up rather than pulled - the launcher's `cwd`,
  // which the reference keeps bound to the filebrowser's path (`ILauncher.cwd` follows the browser
  // widget's). Saveable so a rotation does not send the launcher back to the root the explorer
  // left.
  var browsedDirectory by rememberSaveable { mutableStateOf("") }
  val contributionHost =
    rememberContributionHost(appContainer, spacePorts, browsedDirectory, notebookCells, openDocument)

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

  val newTerminal = rememberNewTerminal(backStack, spacePorts.terminalGateway)

  val openLauncher: () -> Unit = {
    launcherIsFill.value = false
    backStack.openLauncher()
  }
  // Where the browser has been asked to go from *outside* its own entry - the open document's
  // breadcrumb. Plain hoisted state rather than an `OpenDocumentOutline`-style holder,
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
  // point at a pane that is not there. The document's path comes from `openDocumentPaths` rather
  // than from the key, because a rename moves the file and the key keeps the path it was opened
  // under - and now that several editors can sit in the stack, each reports its own.
  val openDocumentPath = backStack.lastOrNull()?.liveDocumentPath(openDocumentPaths).takeIf { twoPane }
  // The scene strategy measures the window for itself, so a caller that was *told* how many panes
  // to draw has to be able to overrule it - otherwise `twoPane` would decide the row marking above
  // and not the layout, and the two would disagree. Same boolean, both halves.
  val measured = paneScaffoldDirective()
  // **A one-pane window has no side area, so its panel *is* the window** - and that once meant
  // the browser and nothing else, because the rail is drawn only `if (twoPane)` below while
  // `sidePanel` is `rememberSaveable`. A tablet rotated to portrait (1280dp landscape is two panes,
  // 800dp portrait is one) kept drawing the panel it had with no rail to switch away from it and no
  // way back to the files but killing the app. The bottom bar leaves Files and Search, so
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
  val backSelectsFiles =
    !twoPane && onePanePanel != ShownPanel.Shell(SidePanel.FILES) && backStack.lastOrNull() is Explorer

  // The VIEW intent - opened in the Space entered, or in base from the home; see the effect's own KDoc.
  ImportedNotebookEffect(sharedNotebook, appContainer, backStack, openDocument, onImportConsumed, importReports)

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
  // **Both documents take this same lambda** - the notebook and the plain file editor alike -
  // because two copies of a branch that has to agree is a defect waiting to be written, the same
  // reason `ShellSidePane` stopped being handed the width fact twice.
  val goToFolder: (String) -> Unit = { folder ->
    browseRequest = folder
    if (twoPane) {
      sidePanel = ShownPanel.Shell(SidePanel.FILES)
    } else {
      // One pane, and the browser is only ever shown as the top entry: bring it forward rather
      // than popping the document - the entries beneath are still-open tabs, so Back
      // from the browser returns to the document exactly as activating a tab would.
      backStack.bringBrowserForward()
    }
  }

  // A hoisted lambda like `goToFolder`, for the same reason: the card-to-document mapping is the
  // navigation's own, not the launcher's - and the `when` inside it must not count toward this
  // function's complexity budget.
  val openLaunched = rememberLaunchHandler(backStack, openDocument) { retainedConsole.session = it }

  // **The activity bar is beside the whole shell, not inside the side area.** The reference's
  // `hboxPanel` holds `leftHandler.sideBar` next to the split that contains both the panel and the
  // document area (`packages/application/src/shell.ts:468-477`), which is what lets the area
  // collapse to the bar alone. An earlier plan row said "the left edge of the list pane"; reading the
  // shell corrected it.
  //
  // Only where there is room for two panes: a compact window gets the bar instead, and a rail
  // there would spend a quarter of a 320dp screen on chrome. And only inside a workspace: the bar
  // is the workspace's, and the Spaces home is entered before any of its panels mean anything.
  Row(modifier = Modifier.fillMaxSize()) {
    if (twoPane && backStack.isInWorkspace()) {
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
      // this id in both default themes, a different id, and nothing holding it.
      SidePanelRail(
        selected = sidePanel,
        onChoose = { sidePanel = sidePanel.chosen(it) },
        contributions = appContainer.sidePanelContributions,
        onOpenLauncher = openLauncher,
        onOpenSettings = { backStack.openSettings() },
        onLeaveWorkspace = closeGate.leaveWorkspace,
        modifier = Modifier.trailingBorder(chromePalette.activityBarBorder),
      )
    }
    NavDisplay(
      backStack = backStack,
      // **Back reaches the workspace's root before it leaves the workspace.** Android's own
      // principle: the start destination *"is also the last screen the user sees when they return
      // to the launcher after pressing the Back button"*
      // (`developer.android.com/guide/navigation/principles`). The browser is the workspace's root,
      // so backing out of a one-pane Search selects Files rather than leaving for the Spaces home -
      // and only on the browser, since over a document Back belongs to the document. Both rules
      // live here rather than in a `BackHandler`, which loses the gesture to the display's own.
      onBack = {
        if (backSelectsFiles) sidePanel = ShownPanel.Shell(SidePanel.FILES) else backStack.popWorkspaceEntry(twoPane)
      },
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
      // Both entries stay entries; this only decides whether they are drawn one at a time or beside each other.
      // `ListDetailSceneStrategy` collects the matching entries into one scene and draws each one's own `Content()`, so
      // every decorator stays where it was - the property the hand-written strategy this replaced existed for, and the
      // reason `NavigableListDetailPaneScaffold` is still not what this uses: it puts both panes in *one* `NavEntry`.
      //
      // With one partition it declines the scene (`shouldHandleSinglePaneLayout` is false by
      // default), and NavDisplay falls through to its single-pane default.
      //
      // **`PopLatest`, not the library's default, and it is not a preference.** With both panes
      // expanded, `PopUntilScaffoldValueChange` looks for an earlier destination whose scaffold
      // value differs and never finds one - the list alone still lays out as two panes - so
      // `getPreviousDestinationIndex` returns -1, `previousEntries` comes back empty, and the scene
      // pops *every* entry. Measured on the tablet: back from a notebook left the app for the home
      // screen. `PopLatest` pops exactly one, which is what
      // `TwoPaneScene.previousEntries = entries.dropLast(1)` did.
      sceneStrategy = rememberListDetailSceneStrategy(
        backNavigationBehavior = BackNavigationBehavior.PopLatest,
        directive = directive,
      ),
      entryProvider = entryProvider {
        // No pane metadata, so the scene strategy declines it and the home takes the whole window.
        entry<SpacesHome> {
          SpacesHomeScreen(
            spaceRepository = appContainer.spaceRepository,
            spaceServers = appContainer.spaceServers,
            spaceEnvironments = appContainer.spaceEnvironments,
            stoppedKernelSpecs = appContainer.stoppedSpaceKernelSpecs,
            onOpen = backStack::enterWorkspace,
          )
        }
        entry<Explorer>(metadata = ListDetailSceneStrategy.listPane()) {
          ShellSidePane(
            showingPanel = showingPanel,
            onePanePanel = onePanePanel,
            // The one width fact, passed once. It used to arrive twice - as `showingPanel` and as a
            // `barSelection` that was `if (twoPane) null else onePanePanel` - and the side bar's
            // own border needed a third reading of it. Two parameters that have to agree
            // is a defect waiting to be written; the pane decides from the fact itself.
            twoPane = twoPane,
            onChoosePanel = { sidePanel = it },
            appContainer = appContainer,
            spacePorts = spacePorts,
            outline = outline,
            debugStack = debugPanel,
            notebookCells = notebookCells,
            openDocuments = openDocuments,
            openDocument = openDocument,
            openDocumentPath = openDocumentPath,
            openTabs = runningTabs,
            onActivateTab = backStack::activateMainAreaTab,
            // The same gate the strip's ✕ runs through - a panel row's close is the identical act.
            onCloseTab = closeGate.closeTab,
            saveAllEnabled = anyOpenDocumentDirty(openDocuments),
            // On the application's scope, so closing the panel mid-sweep cannot cut a write short;
            // on Main, because a refused save's reveal moves the back stack.
            onSaveAllTabs = {
              appContainer.applicationScope.launch(Dispatchers.Main) {
                saveAllOpenDocuments(orderedTabs, openDocuments, backStack::activateMainAreaTab)
              }
            },
            onOpenSession = { backStack.openRunningSession(it, orderedTabs, openDocument) },
            // `terminal:open` - the stack's open-or-reveal reattaches to the named shell's socket.
            onOpenTerminal = { backStack.openTerminal(it.name) },
            browseRequest = browseRequest,
            onBrowseRequestHandled = { browseRequest = null },
            onRevealMatch = { match -> revealRequest = match },
            // The bar's terminal item roots at the directory the explorer is showing - the same
            // cwd the launcher's Terminal card gets, hoisted so both reads answer the same path.
            onNewTerminal = { newTerminal(browsedDirectory) },
            onOpenSettings = { backStack.openSettings() },
            onLeaveWorkspace = closeGate.leaveWorkspace,
            onOpenLauncher = openLauncher,
            onBrowsedDirectory = { browsedDirectory = it },
            browsedDirectory = browsedDirectory,
          )
        }
        contributedDocumentEntry(appContainer.documentContributions, contributionHost, tabbedPane)
        entry<Settings>(metadata = ListDetailSceneStrategy.detailPane()) {
          // No onBack, no width fact: leaving a document is the strip's own act now - the ✕ at
          // two panes, the destination-named arrow below - so a screen states only its content.
          tabbedPane {
            SettingsScreen(
              editorSettingsRepository = appContainer.editorSettings,
              themeSettingsRepository = appContainer.themeSettings,
              applicationScope = appContainer.applicationScope,
              onOpenLicences = { backStack.openLicences() },
            )
          }
        }
        entry<Licences>(metadata = ListDetailSceneStrategy.detailPane()) {
          tabbedPane { LicencesScreen(viewModel { LicencesViewModel(appContainer.licenceDocuments) }) }
        }
        entry<FileEditor>(metadata = ListDetailSceneStrategy.detailPane()) { file ->
          tabbedPane {
            FileEditorScreen(
              path = file.path,
              workspaceRepository = spacePorts.workspaceRepository,
              workspaceWatcher = spacePorts.workspaceWatcher,
              workspaceFiles = spacePorts.workspaceFiles,
              applicationScope = appContainer.applicationScope,
              editorSettingsRepository = appContainer.editorSettings,
              // Its own entry, never the last: a file deleted from its tab is closed by the strip
              // first, and the screen's own close on hearing it go must then find nothing to remove
              // rather than the tab beneath (`DeletedTabTest`).
              onBack = { backStack.remove(file) },
              onGoToFolder = goToFolder,
              onFileMoved = { moved -> openDocumentPaths[file] = moved },
              reveal = revealRequest,
              onRevealHandled = { revealRequest = null },
              openDocuments = openDocuments,
              opening = file.opening,
            )
          }
        }
        entry<Editor>(metadata = ListDetailSceneStrategy.detailPane()) { editor ->
          tabbedPane {
            EditorScreen(
              notebookId = editor.notebookId,
              notebookRepository = spacePorts.notebookRepository,
              workspaceRepository = spacePorts.workspaceRepository,
              workspaceWatcher = spacePorts.workspaceWatcher,
              workspaceFiles = spacePorts.workspaceFiles,
              kernelGateway = spacePorts.kernelGateway,
              kernelDebugger = spacePorts.kernelDebugger,
              kernelSessions = spacePorts.kernelSessions,
              kernelSpecs = spacePorts.kernelSpecs,
              space = spacePorts.space,
              languageServer = appContainer.languageServer,
              notebookExporter = spacePorts.notebookExporter,
              documentSharer = spacePorts.documentSharer,
              applicationScope = appContainer.applicationScope,
              activeCellRuns = appContainer.activeCellRuns,
              notebookBreakpoints = appContainer.notebookBreakpoints,
              editorSettingsRepository = appContainer.editorSettings,
              outline = outline,
              // `toc:show-panel` where there is a side area to activate, and null where there is not -
              // which is what puts the overlay up on a compact window instead.
              onShowTableOfContents = { sidePanel = ShownPanel.Shell(SidePanel.TABLE_OF_CONTENTS) }.takeIf { twoPane },
              debugPanel = debugPanel,
              notebookCells = notebookCells,
              onShowDebugger = { sidePanel = ShownPanel.Shell(SidePanel.DEBUGGER) }.takeIf { twoPane },
              onGoToFolder = goToFolder,
              openDocument = openDocument,
              // Its own entry, as the file editor's is: the strip may have closed it already.
              onBack = { backStack.remove(editor) },
              // The one width fact this screen still reads, and it decides one thing now: whether
              // the debugger's bottom bar carries a variables toggle (a side panel already shows
              // it at two panes). How the document is left is the strip's own act.
              twoPane = twoPane,
              onOpenConsole = { session ->
                retainedConsole.session = session
                backStack.openConsole(editor.notebookId)
              },
              onNotebookMoved = { moved -> openDocumentPaths[editor] = moved },
              // The same request the file editor takes, because the panel does not know - and should
              // not - which of the two a path opens: `docmanager:open` decides the editor from the
              // path alone, and the match travels beside it either way.
              reveal = revealRequest,
              onRevealHandled = { revealRequest = null },
              openDocuments = openDocuments,
              opening = editor.opening,
            )
          }
        }
        terminalEntry(spacePorts.terminalGateway, appContainer.terminalSettings, liveTabTitles, tabbedPane)
        entry<Launcher>(metadata = ListDetailSceneStrategy.detailPane()) {
          tabbedPane {
            LauncherScreen(
              cwd = browsedDirectory,
              viewModel = viewModel {
                LauncherViewModel(
                  spacePorts.notebookRepository,
                  spacePorts.workspaceFiles,
                  spacePorts.terminalGateway,
                  spacePorts.kernelGateway,
                  spacePorts.kernelSpecs,
                  spacePorts.space,
                  consoleOrdinal = retainedConsole::nextOrdinal,
                )
              },
              onLaunched = openLaunched,
            )
          }
        }
        entry<Console>(metadata = ListDetailSceneStrategy.detailPane()) { console ->
          tabbedPane {
            // Null only after a process death: the stack is restored from its Bundle, but a live
            // kernel session cannot round-trip through one, so there is nothing to compose against.
            retainedConsole.session?.let { session ->
              ConsoleScreen(
                viewModel = viewModel {
                  ConsoleViewModel(spacePorts.kernelGateway, session, spacePorts.kernelSessions)
                },
                editorSettingsRepository = appContainer.editorSettings,
                sessionPath = console.sessionPath,
                workspaceRepository = spacePorts.workspaceRepository,
                openDocument = openDocument,
                onGoToFolder = goToFolder,
              )
            }
          }
        }
      },
    )
  }
}
