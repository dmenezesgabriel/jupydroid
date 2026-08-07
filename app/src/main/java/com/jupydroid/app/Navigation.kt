package com.jupydroid.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.ui.editor.EditorScreen
import com.jupydroid.app.ui.home.HomeScreen

@Composable
fun MainNavigation(
  appContainer: AppContainer,
  importTitle: String?,
  onImportConsumed: () -> Unit,
) {
  val backStack = rememberNavBackStack(Home)

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
    entryProvider =
      entryProvider {
        entry<Home> {
          HomeScreen(
            notebookRepository = appContainer.notebookRepository,
            importTitle = importTitle,
            onImportConsumed = onImportConsumed,
            onOpenNotebook = { id -> backStack.openNotebook(id) },
          )
        }
        entry<Editor> { editor ->
          EditorScreen(
            notebookId = editor.notebookId,
            notebookRepository = appContainer.notebookRepository,
            kernelGateway = appContainer.kernelGateway,
            applicationScope = appContainer.applicationScope,
            onBack = { backStack.removeLastOrNull() },
          )
        }
      },
  )
}
