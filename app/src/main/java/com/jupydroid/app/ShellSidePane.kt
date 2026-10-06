package com.jupydroid.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.app.ui.explorer.ExplorerScreen
import com.jupydroid.app.ui.shell.DebuggerSidePanel
import com.jupydroid.app.ui.shell.RunningSidePanel
import com.jupydroid.app.ui.shell.SearchSidePanel
import com.jupydroid.app.ui.shell.SidePanelBar
import com.jupydroid.app.ui.shell.TableOfContentsSidePanel
import com.jupydroid.app.ui.shell.WorkspaceReplace
import com.jupydroid.app.ui.shell.trailingBorder

/**
 * The shell's side pane: the one panel it is showing, and - where it is the whole window - the bar
 * that changes which.
 *
 * Beside two panes this is the list pane and the activity bar is the rail left of the whole shell
 * (`SidePanelRail`). In one window it *is* the window, and the activity bar is under it.
 *
 * ```
 * entry<Explorer>(metadata = ListDetailSceneStrategy.listPane()) { ShellSidePane(…) }
 * ```
 *
 * @param showingPanel the panel to draw, `null` being the collapsed side area - which only a
 *   two-pane window has, since collapsing the one thing on screen is not a state.
 * @param onePanePanel the panel the bottom bar marks. Read only where [twoPane] is false, since
 *   beside two panes the rail is the activity bar and this pane is only half the shell.
 * @param twoPane whether a document area sits beside this pane. **A parameter, never measured
 *   here**: `currentWindowAdaptiveInfo()` reads the *activity's* window, which a
 *   `DeviceConfigurationOverride` around a composable does not change, so a branch chosen inside
 *   would drive one half twice while looking like it covered both (`PaneDirective.kt:31-38`). It
 *   decides two things - whether a bar is drawn under the panel, and whether there is a document
 *   for the side bar's own border to separate this pane from.
 * @param onRevealMatch where a tapped search result puts the match, on its way to the editor that
 *   has to land on it. Separate from [openDocument] because the two are one act in the caller and
 *   two different pieces of its state.
 * @param onNewTerminal opens a shell - the bottom bar's own item, already bound to the browsed
 *   directory by `Navigation.kt`'s `rememberNewTerminal`. At two panes this callback is unread:
 *   the bar is not drawn and the launcher's Terminal card is the reach.
 * @param onOpenSettings opens the app-wide Settings screen - see `NotebookBackStack.kt`'s
 *   `openSettings`.
 * @param onOpenLauncher opens the Launcher - the bottom bar's third destination, which is a
 *   document rather than a [SidePanel], so it is a callback here and not a `chosen`.
 * @param onBrowsedDirectory where the explorer reports the directory it is showing - the
 *   launcher's cwd, hoisted to the navigation above both entries.
 * @param spacePorts the entered Space's files - what the explorer lists, search walks and Recents
 *   remembers. Handed in rather than looked up here, so the pane cannot bind to a Space other than
 *   the one the navigation entered.
 * @param debugStack the open notebook's stopped call stack - [SidePanel.DEBUGGER]'s own content;
 *   see [OpenDebugStack].
 */
@Composable
fun ShellSidePane(
  showingPanel: SidePanel?,
  onePanePanel: SidePanel,
  twoPane: Boolean,
  onChoosePanel: (SidePanel) -> Unit,
  appContainer: AppContainer,
  spacePorts: EnteredSpacePorts,
  outline: OpenDocumentOutline,
  debugStack: OpenDebugStack,
  openDocuments: OpenDocuments,
  openDocument: (String) -> Unit,
  openDocumentPath: String?,
  /**
   * `labShell.widgets('main')` for [SidePanel.RUNNING]'s Open Tabs section, in the strip's own
   * opening order. Built by the caller rather than read here, because the back stack it comes
   * from is the navigation's, not this pane's.
   */
  openTabs: List<RunningTab>,
  /** `labShell.activateById` for a tapped tab row. */
  onActivateTab: (NavKey) -> Unit,
  /**
   * The shared `application:close` - `MainNavigation`'s close gate, so a row's ✕ asks the same
   * dirty question the strip's would.
   */
  onCloseTab: (NavKey) -> Unit,
  /** `running:kernel-open-session` - opens (or reveals) the document a session row names. */
  onOpenSession: (KernelSessionModel) -> Unit,
  /** `terminal:open` - reveals the terminal's tab or reattaches to the named shell. */
  onOpenTerminal: (TerminalSession) -> Unit,
  browseRequest: String?,
  onBrowseRequestHandled: () -> Unit,
  onRevealMatch: (MatchedLine) -> Unit,
  onNewTerminal: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenLauncher: () -> Unit,
  onBrowsedDirectory: (String) -> Unit,
) {
  // **`sideBar.border`, drawn by the side bar itself** - the seam between this pane and the
  // document, which the reference states (`workbench/common/theme.ts:623`, *"on the side separating
  // to the editor"*) and this app once drew nowhere: the panel's band ended at x661 and the page's
  // began at x662 with no column between them.
  // Only beside two panes, because at one pane
  // this pane *is* the window and its trailing edge is the screen's.
  val separated = if (twoPane) Modifier.trailingBorder(chromePalette.sideBarBorder) else Modifier
  Column(modifier = Modifier.fillMaxSize().then(separated)) {
    Box(modifier = Modifier.weight(1f).then(panelInsets(hasBar = !twoPane))) {
      // The side area draws **one** panel, which is the reference's own arrangement:
      // `SideBarHandler._onCurrentChanged` hides the outgoing widget and shows the incoming one
      // (`packages/application/src/shell.ts:2630-2643`). The collapsed branch draws nothing here
      // and the caller's directive has already dropped this pane, so it is unreachable - a one-pane
      // window has no side area to collapse, and `SidePanel.showingAtOnePane` never answers null.
      when (showingPanel) {
        SidePanel.TABLE_OF_CONTENTS -> TableOfContentsSidePanel(outline = outline.current)
        SidePanel.DEBUGGER -> DebuggerSidePanel(
          stack = debugStack.current,
          onExpand = debugStack::expand,
          onSelectFrame = debugStack::selectFrame,
          onEvaluate = debugStack::evaluate,
        )
        SidePanel.RUNNING ->
          RunningSidePanel(
            kernelSessions = appContainer.kernelSessions,
            terminalGateway = appContainer.terminalGateway,
            openTabs = openTabs,
            onActivateTab = onActivateTab,
            onCloseTab = onCloseTab,
            onOpenSession = onOpenSession,
            onOpenTerminal = onOpenTerminal,
          )
        SidePanel.SEARCH ->
          SearchSidePanel(
            searcher = spacePorts.workspaceSearcher,
            // Built here and not in `AppContainer`, because the registry it needs is the shell's
            // own state rather than a process-wide implementation: `openDocuments` is remembered by
            // this composition and a container outlives every one of them.
            replace = remember(spacePorts) {
              WorkspaceReplace(openDocuments, spacePorts.notebookRepository, spacePorts.workspaceRepository)
            },
            // The path is what opens a document, exactly as the browser's own rows hand one up -
            // `docmanager:open` decides the editor from the path and nothing else. The match
            // travels beside it, because `openEditor` takes a `selection` as well as a resource
            // (`searchView.ts:2245-2256`) and the panel is the only thing that knows which result
            // was tapped.
            onOpenMatch = { file, match ->
              onRevealMatch(match)
              openDocument(file.path)
            },
          )
        null -> Unit
        SidePanel.FILES ->
          ExplorerScreen(
            workspaceRepository = spacePorts.workspaceRepository,
            notebookRepository = spacePorts.notebookRepository,
            recentDocuments = spacePorts.recentDocuments,
            explorerSettings = appContainer.explorerSettings,
            // The entry's path *is* the notebook's id - `Contents.IModel.path`, suffix included -
            // so there is nothing to translate. It briefly was not, and the suffix went on twice:
            // `Nested.ipynb.ipynb` opened nothing.
            onOpenFile = { file -> openDocument(file.path) },
            onOpenRecent = openDocument,
            onOpenSettings = onOpenSettings,
            openDocumentPath = openDocumentPath,
            browseRequest = browseRequest,
            onBrowseRequestHandled = onBrowseRequestHandled,
            onBrowsedDirectory = onBrowsedDirectory,
            // No arrow at all: this is the first screen now, so there is nowhere to go back *to*.
            // Back still climbs the workspace while there is a parent - `filebrowser:go-up` - and
            // at the root it leaves the app, which is what nothing swallowing it means.
            onBack = null,
          )
      }
    }
    // **The bar is drawn here and nowhere else**, which is what keeps it off an open document: in
    // one window `NavDisplay` composes only the top entry, so a bar spelled beside it would sit
    // under the notebook too. That is Android's own rule - a navigation bar carries top-level
    // destinations - and the difference was costed: an 80dp bar is 20% of the 400.0dp cell list at
    // 320dp, a width at which 48dp had already been fought for. Here it costs the document
    // nothing.
    //
    // Never `SidePanel.chosen`: re-picking here would collapse the only thing on screen.
    if (!twoPane) {
      SidePanelBar(
        selected = onePanePanel,
        onChoose = onChoosePanel,
        onOpenLauncher = onOpenLauncher,
        onNewTerminal = onNewTerminal,
      )
    }
  }
}

/**
 * What the panel above the bottom bar must **not** reserve a second time.
 *
 * Every side panel is a `Scaffold` and none sets `contentWindowInsets`, so each reserves the system
 * navigation-bar inset at its foot - and `NavigationBar` reserves it again underneath. Left alone
 * that is a gap between the last row and the bar, which is the doubled-window-inset defect in a
 * new place. Consumed only where a bar is drawn: with none, the panel really is the window's foot.
 */
@Composable
private fun panelInsets(hasBar: Boolean): Modifier =
  if (hasBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier
