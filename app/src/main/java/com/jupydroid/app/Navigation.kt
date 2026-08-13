package com.jupydroid.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.NOTEBOOK_SUFFIX
import com.jupydroid.app.ui.editor.EditorScreen
import com.jupydroid.app.ui.explorer.ExplorerScreen
import com.jupydroid.app.ui.fileeditor.FileEditorScreen
import com.jupydroid.app.ui.home.HomeScreen

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
  val backStack = rememberNavBackStack(Home)
  // Where the open notebook lives *now*. The navigation key keeps the path it was opened under,
  // because replacing it would re-create the entry and discard the editor's state; a rename is
  // reported here instead. `_contextsForPath` does the same - it filters live contexts by their
  // current path, never by the one a widget was created with.
  var openNotebookId by remember { mutableStateOf<String?>(null) }

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
    // Both entries stay entries; this only decides whether they are drawn one at a time or
    // beside each other. Declining a scene (a narrow window, or nothing open) falls through to
    // NavDisplay's single-pane default. See TwoPaneSceneStrategy for why not the pane scaffold.
    sceneStrategy = TwoPaneSceneStrategy(twoPane = twoPane),
    entryProvider =
      entryProvider {
        entry<Home>(metadata = HOME_PANE_METADATA) {
          HomeScreen(
            notebookRepository = appContainer.notebookRepository,
            workspaceRepository = appContainer.workspaceRepository,
            importTitle = importTitle,
            onImportConsumed = onImportConsumed,
            onOpenNotebook = { id -> backStack.openNotebook(id) },
            onOpenFile = { path -> backStack.openFile(path) },
            // Only while the list is *beside* the notebook. In one pane the list is on screen
            // exactly when nothing is open, so a marked row would point at a pane that is not
            // there - and would be the only thing on screen still claiming a notebook is open.
            openNotebookId = openNotebookId.takeIf { twoPane && backStack.lastOrNull() is Editor },
            onBrowseFiles = { backStack.add(Explorer) },
          )
        }
        entry<Explorer>(metadata = EXPLORER_PANE_METADATA) {
          ExplorerScreen(
            workspaceRepository = appContainer.workspaceRepository,
            notebookRepository = appContainer.notebookRepository,
            // The entry's path *is* the notebook's id - `Contents.IModel.path`, suffix included -
            // so there is nothing to translate. It briefly was not, and the suffix went on twice:
            // `Nested.ipynb.ipynb` opened nothing.
            // Everything opens. A `.ipynb` goes to the notebook editor and anything else to the
            // plain one, which is the reference's own split: the notebook factory claims its
            // extension and the file editor is the default for the rest (`packages/fileeditor`).
            onOpenFile = { file ->
              if (file.name.endsWith(NOTEBOOK_SUFFIX, ignoreCase = true)) {
                backStack.openNotebook(file.path)
              } else {
                backStack.openFile(file.path)
              }
            },
            // The arrow exists only while the explorer is the top of the stack - a screen with a
            // door back to what pushed it. Once it is the list pane beside a document it is a
            // *surface*, and a "Back to recents" that would swallow the landscape is the same
            // confusion Recents itself caused (TwoPaneSceneStrategy's table).
            onBack =
              if (backStack.lastOrNull() is Explorer) {
                { backStack.removeLastOrNull() }
              } else {
                null
              },
          )
        }
        entry<FileEditor>(
          metadata = mapOf(PANE_ROLE to PANE_ROLE_DOCUMENT, PANE_DOCUMENT_KIND to PANE_KIND_FILE),
        ) { file ->
          FileEditorScreen(
            path = file.path,
            workspaceRepository = appContainer.workspaceRepository,
            applicationScope = appContainer.applicationScope,
            onBack = { backStack.removeLastOrNull() },
          )
        }
        entry<Editor>(
          metadata = mapOf(PANE_ROLE to PANE_ROLE_DOCUMENT, PANE_DOCUMENT_KIND to PANE_KIND_NOTEBOOK),
        ) { editor ->
          EditorScreen(
            notebookId = editor.notebookId,
            notebookRepository = appContainer.notebookRepository,
            workspaceRepository = appContainer.workspaceRepository,
            kernelGateway = appContainer.kernelGateway,
            applicationScope = appContainer.applicationScope,
            onBack = { backStack.removeLastOrNull() },
            onNotebookMoved = { moved -> openNotebookId = moved },
          )
        }
      },
  )
}
