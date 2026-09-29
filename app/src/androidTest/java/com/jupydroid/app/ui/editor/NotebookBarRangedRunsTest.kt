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
 * The two ranged runs in the kernel overflow - `notebook:run-all-above` and
 * `notebook:run-all-below`.
 *
 * Its own suite beside [NotebookBarTest] and [NotebookBarShutdownTest], on the same split those
 * two already draw: one bar, one concern per file.
 *
 * **Whether there is a row to press, and whether pressing it does anything, is not a question any
 * JVM test can answer** - which is why 4b earned `app/src/androidTest` in the first place. Both
 * rows are behind an overflow that has to be opened, and both are disabled at a notebook end.
 *
 * **This suite is the only place the greying is asserted, and that is deliberate.** Compose's
 * merged semantics tree carries a `DropdownMenuItem`'s disabled state onto its label, so
 * `assertIsNotEnabled` here sees it. Android's accessibility tree - which is what Maestro reads -
 * does not: it keeps the state on a clickable node one level up that has no label, so a flow
 * naming the row always matches an enabled `TextView`. Measured three ways on the tablet.
 * The flow proves the same fact by behaviour instead.
 */
@RunWith(AndroidJUnit4::class)
class NotebookBarRangedRunsTest {
  @get:Rule val compose = createComposeRule()

  private val ran = mutableListOf<RunRange>()

  @Test
  fun bothRangedRunsAreInTheKernelOverflow() {
    showBar(canRunAllAbove = true, canRunSelectedAndBelow = true)

    compose.onNodeWithContentDescription("Kernel actions").performClick()

    compose.onNodeWithText(RUN_ALL_ABOVE).assertIsDisplayed()
    compose.onNodeWithText(RUN_SELECTED_AND_BELOW).assertIsDisplayed()
  }

  @Test
  fun eachRangedRunAsksForItsOwnRange() {
    showBar(canRunAllAbove = true, canRunSelectedAndBelow = true)

    compose.onNodeWithContentDescription("Kernel actions").performClick()
    compose.onNodeWithText(RUN_ALL_ABOVE).performClick()
    compose.onNodeWithContentDescription("Kernel actions").performClick()
    compose.onNodeWithText(RUN_SELECTED_AND_BELOW).performClick()

    // ABOVE excludes the head and BELOW includes it, so sending one where the other was meant is
    // an off-by-one nobody would see on screen - see [cellsToRun].
    compose.runOnIdle { assertEquals(listOf(RunRange.ABOVE, RunRange.BELOW), ran) }
  }

  /**
   * The head sits on the first cell: there is nothing above it, and `notebook:run-all-above` is
   * disabled there (`packages/notebook-extension/src/index.ts:3082-3089`).
   */
  @Test
  fun runAllAboveIsGreyedOnTheFirstCell() {
    showBar(canRunAllAbove = false, canRunSelectedAndBelow = true)

    compose.onNodeWithContentDescription("Kernel actions").performClick()

    compose.onNodeWithText(RUN_ALL_ABOVE).assertIsNotEnabled()
    compose.onNodeWithText(RUN_SELECTED_AND_BELOW).assertIsEnabled()
  }

  @Test
  fun runSelectedAndBelowIsGreyedOnTheLastCell() {
    showBar(canRunAllAbove = true, canRunSelectedAndBelow = false)

    compose.onNodeWithContentDescription("Kernel actions").performClick()

    compose.onNodeWithText(RUN_SELECTED_AND_BELOW).assertIsNotEnabled()
    compose.onNodeWithText(RUN_ALL_ABOVE).assertIsEnabled()
  }

  /** A disabled row that still fired would be the worst of both: greyed, and doing it anyway. */
  @Test
  fun aGreyedRangedRunRunsNothing() {
    showBar(canRunAllAbove = false, canRunSelectedAndBelow = false)

    compose.onNodeWithContentDescription("Kernel actions").performClick()
    compose.onNodeWithText(RUN_ALL_ABOVE).performClick()
    compose.onNodeWithText(RUN_SELECTED_AND_BELOW).performClick()

    compose.runOnIdle { assertEquals(emptyList<RunRange>(), ran) }
  }

  private fun showBar(canRunAllAbove: Boolean, canRunSelectedAndBelow: Boolean) {
    compose.setContent {
      JupyterDroidTheme {
        NotebookBar(
          busy = false,
          onRunAll = { ran += RunRange.ALL },
          onInterrupt = {},
          // The restarts are `NotebookBarTest`'s and the shutdowns `NotebookBarShutdownTest`'s.
          onRestart = {},
          onRestartAndRunAll = {},
          commands = NotebookKernelCommands(
            onRun = { range -> ran += range },
            canRunAllAbove = canRunAllAbove,
            canRunSelectedAndBelow = canRunSelectedAndBelow,
          ),
        )
      }
    }
  }
}
