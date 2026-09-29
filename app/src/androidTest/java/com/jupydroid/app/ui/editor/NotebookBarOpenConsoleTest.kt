package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `notebook:create-console` - filed in the Kernel menu beside the two shutdown commands,
 * `NotebookBarShutdownTest`'s own precedent, since this app has no separate File menu the
 * reference's `mainMenu.fileMenu.consoleCreators` files it under.
 */
@RunWith(AndroidJUnit4::class)
class NotebookBarOpenConsoleTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theKernelMenuOffersANewConsoleForTheNotebook() {
    showBar {}

    compose.onNodeWithContentDescription("Kernel actions").performClick()

    compose.onNodeWithText(NEW_CONSOLE_FOR_NOTEBOOK).assertIsDisplayed()
  }

  @Test
  fun tappingItInvokesOnOpenConsole() {
    var tapped = false
    showBar { tapped = true }

    compose.onNodeWithContentDescription("Kernel actions").performClick()
    compose.onNodeWithText(NEW_CONSOLE_FOR_NOTEBOOK).performClick()

    compose.runOnIdle { assertEquals(true, tapped) }
  }

  private fun showBar(onOpenConsole: () -> Unit) {
    compose.setContent {
      JupyterDroidTheme {
        NotebookBar(
          busy = false,
          onRunAll = {},
          onInterrupt = {},
          onRestart = {},
          onRestartAndRunAll = {},
          commands = NotebookKernelCommands(notebookTitle = "Data", onOpenConsole = onOpenConsole),
        )
      }
    }
  }
}
