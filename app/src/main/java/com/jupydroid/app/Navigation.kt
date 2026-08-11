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
import com.jupydroid.app.domain.model.notebookNameOf
import com.jupydroid.app.ui.editor.EditorScreen
import com.jupydroid.app.ui.explorer.ExplorerScreen
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
        entry<Home> {
          HomeScreen(
            notebookRepository = appContainer.notebookRepository,
            importTitle = importTitle,
            onImportConsumed = onImportConsumed,
            onOpenNotebook = { id -> backStack.openNotebook(id) },
            // Only while the list is *beside* the notebook. In one pane the list is on screen
            // exactly when nothing is open, so a marked row would point at a pane that is not
            // there - and would be the only thing on screen still claiming a notebook is open.
            openNotebookId = openNotebookId.takeIf { twoPane && backStack.lastOrNull() is Editor },
            onBrowseFiles = { backStack.add(Explorer) },
          )
        }
        entry<Explorer> {
          ExplorerScreen(
            workspaceRepository = appContainer.workspaceRepository,
            // The id is the path *without* the suffix - `notebookFileName` puts it back on the
            // way to disk - so handing over `file.path` whole would look up `Nested.ipynb.ipynb`
            // and open nothing. `ExplorerNavigationTest` asserts the cell for that reason.
            onOpenFile = { file -> backStack.openNotebook(notebookNameOf(file.path)) },
            // Only notebooks, until the plain-text editor lands. Opening a `.py` in the notebook
            // editor is precisely the confusion "New Python file" already causes, and doing it
            // from here would spread it rather than fix it.
            canOpenFile = { file -> file.name.endsWith(NOTEBOOK_SUFFIX, ignoreCase = true) },
            onBack = { backStack.removeLastOrNull() },
          )
        }
        entry<Editor> { editor ->
          EditorScreen(
            notebookId = editor.notebookId,
            notebookRepository = appContainer.notebookRepository,
            kernelGateway = appContainer.kernelGateway,
            applicationScope = appContainer.applicationScope,
            onBack = { backStack.removeLastOrNull() },
            onNotebookMoved = { moved -> openNotebookId = moved },
          )
        }
      },
  )
}
