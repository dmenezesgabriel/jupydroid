package com.jupydroid.app

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
private fun rememberDocumentOpener(
  backStack: MutableList<NavKey>,
  appContainer: AppContainer,
): (String) -> Unit = remember(backStack, appContainer) {
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
) {
  // The explorer is what the app opens on. Recents was a scan of one directory and is now a
  // section of this screen, so there is no second surface left to start from - which is also the
  // reference's arrangement: JupyterLab has one file browser and no separate recents screen.
  val backStack = rememberNavBackStack(Explorer)
  // Where the open notebook lives *now*. The navigation key keeps the path it was opened under,
  // because replacing it would re-create the entry and discard the editor's state; a rename is
  // reported here instead. `_contextsForPath` does the same - it filters live contexts by their
  // current path, never by the one a widget was created with.
  var openNotebookId by remember { mutableStateOf<String?>(null) }
  val openDocument = rememberDocumentOpener(backStack, appContainer)
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
  val directive = if (twoPane) measured else measured.forcedToOnePane()

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
            // No arrow at all: this is the first screen now, so there is nowhere to go back *to*.
            // Back still climbs the workspace while there is a parent - `filebrowser:go-up` - and
            // at the root it leaves the app, which is what nothing swallowing it means.
            onBack = null,
          )
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
            onBack = { backStack.removeLastOrNull() },
            onNotebookMoved = { moved -> openNotebookId = moved },
          )
        }
      },
  )
}
