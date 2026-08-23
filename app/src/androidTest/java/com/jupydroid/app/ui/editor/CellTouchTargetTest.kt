package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.ui.assertIsAtLeastMinimumTouchTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every control a cell offers accepts a press over at least 48dp, Material's minimum - on the
 * header since it was written, and in the run gutter since P2v-1 moved the controls there.
 *
 * **This pins behaviour that was already correct, and the measurement is the reason it says so.**
 * The header used to wrap each `IconButton` in `Modifier.size(32.dp)`, which looked like an
 * accessibility bug and was written up as one. It was not: `IconButton` applies
 * `minimumInteractiveComponentSize()` *outside* the caller's modifier, so the pressable area
 * stayed 48dp and only the ripple shrank. Measured on device with the 32dp wrapper still in
 * place - `touch target is 48.0.dp x 48.0.dp`.
 *
 * The wrapper is gone anyway, for the reason that survives: 32dp is smaller than Material's 40dp
 * default and made the header look cramped beside every other control in the app. That is taste,
 * not accessibility, and the two should not be confused.
 *
 * What this test is worth keeping for is the thing that *could* break: a control built from a
 * bare `clickable` instead of an `IconButton`, or one that opts out of the minimum, would have a
 * real 24dp target and nothing but this to notice.
 *
 * The measurement itself is `assertIsAtLeastMinimumTouchTarget`, shared with the bars' own suites.
 */
@RunWith(AndroidJUnit4::class)
class CellTouchTargetTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theMenuButtonIsBigEnoughToHit() {
    compose.setContent {
      CellHeader(CODE_CELL, execution = null, canPaste = false, mark = null, {}, {})
    }

    compose.onNodeWithContentDescription("Cell actions").assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  /**
   * The run controls left the header for the gutter in P2v-1, and the minimum travels with them -
   * a column reserved at [CELL_RUN_GUTTER] is exactly for the press, so a smaller target would
   * make the reservation a lie.
   */
  @Test
  fun theRunControlInTheGutterIsBigEnoughToHit() {
    compose.setContent {
      CellGutter(kind = CellKind.CODE, status = ExecutionStatus.NOT_STARTED, executionCount = null, onRun = {}, onStop = {})
    }

    compose.onNodeWithContentDescription("Run cell").assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  /** Stop replaces Run mid-execution, so it is a separate composition and a separate risk. */
  @Test
  fun theStopButtonInTheGutterIsBigEnoughToHit() {
    compose.setContent {
      CellGutter(kind = CellKind.CODE, status = ExecutionStatus.RUNNING, executionCount = null, onRun = {}, onStop = {})
    }

    compose.onNodeWithContentDescription("Stop cell").assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  /**
   * The tick a marked run puts on every cell, which is what a thumb reaches for most in that mode
   * - and the only control on the header that is not an `IconButton`.
   */
  @Test
  fun theSelectionTickIsBigEnoughToHit() {
    compose.setContent {
      CellHeader(CODE_CELL, execution = null, canPaste = false, mark = MARK, {}, {})
    }

    compose.onNodeWithContentDescription("Select cell 1").assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  /** Markdown's render-play moved to the gutter with code's Run, and keeps its own press. */
  @Test
  fun aMarkdownCellsPlayIsBigEnoughToHit() {
    compose.setContent {
      CellGutter(kind = CellKind.MARKDOWN, status = ExecutionStatus.NOT_STARTED, executionCount = null, onRun = {}, onStop = {})
    }

    compose.onNodeWithContentDescription("Run cell").assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  private companion object {
    val MARK = CellMark(checked = false, position = 1)
    val CODE_CELL = Cell(id = "c1", kind = CellKind.CODE, source = "print(1)")
    val MARKDOWN_CELL = Cell(id = "m1", kind = CellKind.MARKDOWN, source = "# hi")
  }
}
