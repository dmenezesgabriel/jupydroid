package com.jupydroid.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
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
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.model.NOTEBOOK_SUFFIX
import com.jupydroid.app.ui.editor.EditorScreen
import com.jupydroid.app.ui.explorer.ExplorerScreen
import com.jupydroid.app.ui.fileeditor.FileEditorScreen
import com.jupydroid.app.ui.shell.SearchSidePanel
import com.jupydroid.app.ui.shell.SidePanelRail
import com.jupydroid.app.ui.shell.TableOfContentsSidePanel
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
  val openDocument = rememberDocumentOpener(backStack, appContainer)
  // Where the browser has been asked to go from *outside* its own entry - the open document's
  // breadcrumb (P2w-1). Plain hoisted state rather than an `OpenDocumentOutline`-style holder,
  // because that one exists for something flowing **sideways** between two entries and this flows
  // down from their common parent: the editor asks here, and here is already above the browser.
  var browseRequest by remember { mutableStateOf<String?>(null) }
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
  // **There is no side area at all where the shell draws one pane**, so the browser is what the
  // list pane holds there whatever the rail was last left on. The rail is drawn only `if (twoPane)`
  // below, and `sidePanel` is `rememberSaveable` - so without this a tablet rotated to portrait
  // (1280dp landscape is two panes, 800dp portrait is one) kept drawing the panel it had, with no
  // rail to switch away from it and no way back to the files but killing the app. Measured:
  // `SidePaneNavigationTest.aWindowThatNarrowsWhileAPanelIsUpStillDrawsTheBrowser`.
  //
  // An override rather than a reset, so rotating back restores the panel the user had chosen.
  val showingPanel = if (twoPane) sidePanel else SidePanel.FILES
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
      SidePanelRail(selected = sidePanel, onChoose = { sidePanel = sidePanel.chosen(it) })
      // The seam between the bar and whatever it is showing, which the reference budgets into the
      // bar's own width: `.jp-SideBar[data-orientation='vertical']` is
      // `calc(var(--jp-private-sidebar-tab-width) + var(--jp-border-width))`
      // (`packages/application/style/sidepanel.css:41-44`). Without it the rail and the panel are
      // one white field and the bar reads as part of the panel's content - measured, and it is one
      // of the three things the first run's screenshot showed and its assertions could not.
      VerticalDivider()
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
          // The side area draws **one** panel, which is the reference's own arrangement:
          // `SideBarHandler._onCurrentChanged` hides the outgoing widget and shows the incoming one
          // (`packages/application/src/shell.ts:2630-2643`). The collapsed branch draws nothing here
          // and the directive above has already dropped this pane, so it is unreachable - a one-pane
          // window has no side area to collapse, and `showingPanel` is the file browser there.
          when (showingPanel) {
            SidePanel.TABLE_OF_CONTENTS -> TableOfContentsSidePanel(outline = outline.current)
            SidePanel.SEARCH ->
              SearchSidePanel(
                searcher = appContainer.workspaceSearcher,
                // The path is what opens a document, exactly as the browser's own rows hand one up
                // - `docmanager:open` decides the editor from the path and nothing else. The match
                // comes with it because the panel is the only thing that knows which one was
                // tapped; **P2x-3f is what reads it**, to land the editor on the line.
                onOpenMatch = { file, _ -> openDocument(file.path) },
              )
            null -> Unit
            SidePanel.FILES ->
              ExplorerScreen(
                workspaceRepository = appContainer.workspaceRepository,
                notebookRepository = appContainer.notebookRepository,
                recentDocuments = appContainer.recentDocuments,
                // The entry's path *is* the notebook's id - `Contents.IModel.path`, suffix included -
                // so there is nothing to translate. It briefly was not, and the suffix went on twice:
                // `Nested.ipynb.ipynb` opened nothing.
                onOpenFile = { file -> openDocument(file.path) },
                onOpenRecent = openDocument,
                openDocumentPath = openDocumentPath,
                browseRequest = browseRequest,
                onBrowseRequestHandled = { browseRequest = null },
                // No arrow at all: this is the first screen now, so there is nowhere to go back *to*.
                // Back still climbs the workspace while there is a parent - `filebrowser:go-up` - and
                // at the root it leaves the app, which is what nothing swallowing it means.
                onBack = null,
              )
          }
        }
        entry<FileEditor>(metadata = ListDetailSceneStrategy.detailPane()) { file ->
          FileEditorScreen(
            path = file.path,
            workspaceRepository = appContainer.workspaceRepository,
            applicationScope = appContainer.applicationScope,
            editorSettingsRepository = appContainer.editorSettings,
            onBack = { backStack.removeLastOrNull() },
          )
        }
        entry<Editor>(metadata = ListDetailSceneStrategy.detailPane()) { editor ->
          EditorScreen(
            notebookId = editor.notebookId,
            notebookRepository = appContainer.notebookRepository,
            workspaceRepository = appContainer.workspaceRepository,
            kernelGateway = appContainer.kernelGateway,
            notebookExporter = appContainer.notebookExporter,
            documentSharer = appContainer.documentSharer,
            applicationScope = appContainer.applicationScope,
            activeCellRuns = appContainer.activeCellRuns,
            editorSettingsRepository = appContainer.editorSettings,
            outline = outline,
            // `toc:show-panel` where there is a side area to activate, and null where there is not -
            // which is what puts the overlay up on a compact window instead.
            onShowTableOfContents = { sidePanel = SidePanel.TABLE_OF_CONTENTS }.takeIf { twoPane },
            // A breadcrumb segment, which is JupyterLab's `this._model.cd(destination)`
            // (`packages/filebrowser/src/crumbs.ts:415`) - so the browser has to be *showing* for
            // the navigation to be visible, and which half that needs differs by layout. Beside
            // the notebook the side area may be drawing the table of contents instead; in one
            // window the browser is not composed at all until this entry goes.
            onGoToFolder = { folder ->
              browseRequest = folder
              if (twoPane) sidePanel = SidePanel.FILES else backStack.removeLastOrNull()
            },
            onBack = { backStack.removeLastOrNull() },
            onNotebookMoved = { moved -> openNotebookId = moved },
          )
        }
      },
    )
  }
}
