package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.assertIsAtLeastMinimumTouchTarget
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two shutdown commands, which live in a menu rather than on the bar.
 *
 * That is where the reference keeps them - the Kernel menu, `kernelUsers.shutdownKernel`
 * (`packages/mainmenu-extension/src/index.ts:690`), not the notebook toolbar - and the bar already
 * carries four buttons, which is as many as a phone's bottom bar affords.
 *
 * What is worth pinning here is the asymmetry between the two: `notebook:shutdown-kernel` fires on
 * the tap, and `notebook:close-and-shutdown` asks first
 * (`packages/notebook-extension/src/index.ts:3189-3213`). Whether there is something *visible to
 * press* is likewise not a question a JVM test can answer, which is why this is instrumented.
 */
@RunWith(AndroidJUnit4::class)
class NotebookBarShutdownTest {
  @get:Rule val compose = createComposeRule()

  private val commands = mutableListOf<String>()

  @Test
  fun theKernelMenuOffersBothWaysOfEndingIt() {
    showBar()

    compose.onNodeWithContentDescription(KERNEL_ACTIONS).performClick()

    compose.onNodeWithText("Shut Down Kernel").assertIsDisplayed()
    compose.onNodeWithText("Close and Shut Down Notebook…").assertIsDisplayed()
  }

  /** No dialog: the reference's `notebook:shutdown-kernel` executes straight away. */
  @Test
  fun shuttingTheKernelDownNeedsNoConfirmation() {
    showBar()

    compose.onNodeWithContentDescription(KERNEL_ACTIONS).performClick()
    compose.onNodeWithText("Shut Down Kernel").performClick()

    compose.runOnIdle { assertEquals(listOf("shutdown"), commands) }
  }

  /** Closing takes the notebook off the screen as well, so this one asks. */
  @Test
  fun closingAndShuttingDownAsksBeforeItDoesAnything() {
    showBar()

    compose.onNodeWithContentDescription(KERNEL_ACTIONS).performClick()
    compose.onNodeWithText("Close and Shut Down Notebook…").performClick()

    compose.onNodeWithText("""Are you sure you want to close "Data"?""").assertIsDisplayed()
    compose.runOnIdle { assertEquals("nothing may happen before the user agrees", emptyList<String>(), commands) }
  }

  @Test
  fun confirmingTheCloseShutsTheKernelDownAndCloses() {
    showBar()

    compose.onNodeWithContentDescription(KERNEL_ACTIONS).performClick()
    compose.onNodeWithText("Close and Shut Down Notebook…").performClick()
    compose.onNodeWithText("Shut Down").performClick()

    compose.runOnIdle { assertEquals(listOf("close-and-shutdown"), commands) }
  }

  @Test
  fun cancellingTheCloseLeavesTheKernelAlone() {
    showBar()

    compose.onNodeWithContentDescription(KERNEL_ACTIONS).performClick()
    compose.onNodeWithText("Close and Shut Down Notebook…").performClick()
    compose.onNodeWithText("Cancel").performClick()

    compose.runOnIdle { assertEquals(emptyList<String>(), commands) }
  }

  /** Material's minimum, and the rule `CellTouchTargetTest` pins for every control in the app. */
  @Test
  fun theKernelMenuIsBigEnoughToHit() {
    showBar()

    compose.onNodeWithContentDescription(KERNEL_ACTIONS).assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  private fun showBar() {
    compose.setContent {
      JupyterDroidTheme {
        NotebookBar(
          busy = false,
          notebookTitle = "Data",
          onRunAll = {},
          onInterrupt = {},
          onRestart = {},
          onRestartAndRunAll = {},
          onShutdown = { commands += "shutdown" },
          onCloseAndShutdown = { commands += "close-and-shutdown" },
        )
      }
    }
  }

  private companion object {
    const val KERNEL_ACTIONS = "Kernel actions"
  }
}
