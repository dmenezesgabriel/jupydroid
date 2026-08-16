package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
 * The kernel bar, and above all that a restart asks first.
 *
 * Restart is the one action here with no visible result and no way back: nothing on screen
 * changes except that every variable the notebook built is gone. A restart that fired on the
 * first tap would look exactly like one that had not fired at all, so the confirmation is the
 * behaviour worth pinning rather than a nicety.
 */
@RunWith(AndroidJUnit4::class)
class NotebookBarTest {
  @get:Rule val compose = createComposeRule()

  private val restarts = mutableListOf<String>()

  @Test
  fun restartAsksBeforeItDoesAnything() {
    showBar(busy = false)

    compose.onNodeWithContentDescription("Restart kernel").performClick()

    compose.onNodeWithText("All variables will be lost.").assertIsDisplayed()
    compose.runOnIdle { assertEquals("nothing may happen before the user agrees", emptyList<String>(), restarts) }
  }

  @Test
  fun confirmingTheDialogRestarts() {
    showBar(busy = false)

    compose.onNodeWithContentDescription("Restart kernel").performClick()
    compose.onNodeWithText("Restart").performClick()

    compose.runOnIdle { assertEquals(listOf("restart"), restarts) }
  }

  @Test
  fun cancellingTheDialogRestartsNothing() {
    showBar(busy = false)

    compose.onNodeWithContentDescription("Restart kernel").performClick()
    compose.onNodeWithText("Cancel").performClick()

    compose.runOnIdle { assertEquals(emptyList<String>(), restarts) }
  }

  /** The two restarts are separate actions and must not be confusable for one another. */
  @Test
  fun restartAndRunAllIsItsOwnConfirmedAction() {
    showBar(busy = false)

    compose.onNodeWithContentDescription("Restart kernel and run all cells").performClick()
    compose.onNodeWithText("Restart").performClick()

    compose.runOnIdle { assertEquals(listOf("restart-and-run-all"), restarts) }
  }

  /**
   * Run All is disabled while something runs - a second one would queue every cell again behind
   * the one already going - and Interrupt is disabled while nothing does, because interrupting an
   * idle kernel is a no-op the user cannot tell from a broken button.
   */
  @Test
  fun runAllAndInterruptSwapAvailabilityWithTheKernel() {
    showBar(busy = true)

    compose.onNodeWithContentDescription("Run all cells").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Interrupt kernel").assertIsEnabled()
  }

  /**
   * `notebook:clear-all-cell-outputs`, in the reference's own label (`index.ts:3347`). Never
   * greyed - `isEnabled` there is only "is there a notebook" - so unlike the ranged runs beside it
   * there is no state to assert, only that the row exists and fires once.
   */
  @Test
  fun clearAllOutputsIsAnOverflowRowThatFiresOnce() {
    var cleared = 0
    showBar(busy = false, onClearAllOutputs = { cleared++ })

    compose.onNodeWithContentDescription("Kernel actions").performClick()
    compose.onNodeWithText(CLEAR_ALL_OUTPUTS).performClick()

    compose.runOnIdle { assertEquals(1, cleared) }
  }

  @Test
  fun anIdleKernelOffersRunAllAndNotInterrupt() {
    showBar(busy = false)

    compose.onNodeWithContentDescription("Run all cells").assertIsEnabled()
    compose.onNodeWithContentDescription("Interrupt kernel").assertIsNotEnabled()
  }

  private fun showBar(busy: Boolean, onClearAllOutputs: () -> Unit = {}) {
    compose.setContent {
      JupyterDroidTheme {
        NotebookBar(
          busy = busy,
          notebookTitle = "Data",
          onRunAll = {},
          onInterrupt = {},
          onRestart = { restarts += "restart" },
          onRestartAndRunAll = { restarts += "restart-and-run-all" },
          // The shutdown half is `NotebookBarShutdownTest`'s; this suite is about the restarts.
          onShutdown = {},
          onCloseAndShutdown = {},
          onClearAllOutputs = onClearAllOutputs,
        )
      }
    }
  }
}
