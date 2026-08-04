package com.jupydroid.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.ui.home.HomeScreen

@Composable
fun MainNavigation(appContainer: AppContainer) {
  val backStack = rememberNavBackStack(Home)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Home> {
          HomeScreen(notebookRepository = appContainer.notebookRepository)
        }
      },
  )
}
