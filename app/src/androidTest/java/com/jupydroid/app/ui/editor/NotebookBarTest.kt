package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

  /**
   * **Every restart asks, including the two that arrived behind a menu.** The reference awaits
   * `sessionDialogs.restart(…)` in all three compound commands
   * (`packages/notebook-extension/src/index.ts:3245-3345`), so a row that fired on the tap would be
   * the one action here with no visible result and no way back doing exactly that.
   */
  @Test
  fun theCompoundRestartsAskBeforeTheyDoAnything() {
    showBar(busy = false)

    compose.onNodeWithContentDescription("Kernel actions").performClick()
    compose.onNodeWithText(RESTART_AND_CLEAR).performClick()

    compose.onNodeWithText("All variables will be lost.").assertIsDisplayed()
    compose.runOnIdle { assertEquals("nothing may happen before the user agrees", emptyList<String>(), restarts) }
  }

  /** The four titles are what tell the four apart once the sentence under them is the same. */
  @Test
  fun eachRestartNamesItselfInTheDialog() {
    showBar(busy = false)

    compose.onNodeWithContentDescription("Kernel actions").performClick()
    compose.onNodeWithText(RESTART_AND_RUN_TO_SELECTED).performClick()

    compose.onNodeWithText("Restart and run up to the selected cell?").assertIsDisplayed()
  }

  @Test
  fun confirmingACompoundRestartRunsItsOwnContinuation() {
    showBar(busy = false)

    compose.onNodeWithContentDescription("Kernel actions").performClick()
    compose.onNodeWithText(RESTART_AND_CLEAR).performClick()
    compose.onNodeWithText("Restart").performClick()

    compose.runOnIdle { assertEquals(listOf("restart-and-clear"), restarts) }
  }

  /**
   * `restart-and-run-to-selected` is `isEnabledAndSingleSelected` there, and with no head there is
   * no "up to" to run.
   */
  @Test
  fun restartAndRunToSelectedIsGreyedWithNoHead() {
    showBar(busy = false, canRestartAndRunTo = false)

    compose.onNodeWithContentDescription("Kernel actions").performClick()

    compose.onNodeWithText(RESTART_AND_RUN_TO_SELECTED).assertIsNotEnabled()
    compose.onNodeWithText(RESTART_AND_CLEAR).assertIsEnabled()
  }

  /**
   * `notebook:change-kernel` - a menu row over the same `selectKernel` the kernel-name button's
   * tap is (`mainmenu-extension`'s `kernelUser.changeKernel`), filed with the shutdowns as the
   * reference's Kernel menu files it. Never greyed and never confirmed: the picker it opens is
   * the question.
   */
  @Test
  fun changeKernelIsAnOverflowRowThatAsksWhichKernel() {
    var asked = false
    showBar(busy = false, onPickKernel = { asked = true })

    compose.onNodeWithContentDescription("Kernel actions").performClick()
    compose.onNodeWithText("Change Kernel…").performClick()

    compose.runOnIdle { assertTrue("the picker was not asked for", asked) }
  }

  @Test
  fun anIdleKernelOffersRunAllAndNotInterrupt() {
    showBar(busy = false)

    compose.onNodeWithContentDescription("Run all cells").assertIsEnabled()
    compose.onNodeWithContentDescription("Interrupt kernel").assertIsNotEnabled()
  }

  private fun showBar(
    busy: Boolean,
    onClearAllOutputs: () -> Unit = {},
    canRestartAndRunTo: Boolean = true,
    onPickKernel: () -> Unit = {},
  ) {
    compose.setContent {
      JupydroidTheme {
        NotebookBar(
          busy = busy,
          onRunAll = {},
          onInterrupt = {},
          onRestart = { restarts += "restart" },
          onRestartAndRunAll = { restarts += "restart-and-run-all" },
          // The shutdown half is `NotebookBarShutdownTest`'s; this suite is about the restarts.
          commands = NotebookKernelCommands(
            onClearAllOutputs = onClearAllOutputs,
            onRestartAndClear = { restarts += "restart-and-clear" },
            onRestartAndRunTo = { restarts += "restart-and-run-to" },
            canRestartAndRunTo = canRestartAndRunTo,
            onPickKernel = onPickKernel,
          ),
        )
      }
    }
  }
}
