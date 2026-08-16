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
 * Every control on a cell header accepts a press over at least 48dp, Material's minimum.
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
  fun theRunAndMenuButtonsAreBigEnoughToHit() {
    compose.setContent { CellHeader(CODE_CELL, executionCount = null, execution = null, canPaste = false, mark = null, {}, {}, {}, {}) }

    compose.onNodeWithContentDescription("Run cell").assertIsAtLeastMinimumTouchTarget(compose.density)
    compose.onNodeWithContentDescription("Cell actions").assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  /** Stop replaces Run mid-execution, so it is a separate composition and a separate risk. */
  @Test
  fun theStopButtonIsBigEnoughToHit() {
    compose.setContent { CellHeader(CODE_CELL, executionCount = null, execution = CellExecution(ExecutionStatus.RUNNING), canPaste = false, mark = null, {}, {}, {}, {}) }

    compose.onNodeWithContentDescription("Stop cell").assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  /**
   * The tick a marked run puts on every cell, which is what a thumb reaches for most in that mode
   * - and the only control on the header that is not an `IconButton`.
   */
  @Test
  fun theSelectionTickIsBigEnoughToHit() {
    compose.setContent {
      CellHeader(CODE_CELL, executionCount = null, execution = null, canPaste = false, mark = MARK, {}, {}, {}, {})
    }

    compose.onNodeWithContentDescription("Select cell 1").assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  /** A markdown cell composes a different branch of the header, with its own Run button. */
  @Test
  fun aMarkdownCellsControlsAreBigEnoughToHit() {
    compose.setContent { CellHeader(MARKDOWN_CELL, executionCount = null, execution = null, canPaste = false, mark = null, {}, {}, {}, {}) }

    compose.onNodeWithContentDescription("Run cell").assertIsAtLeastMinimumTouchTarget(compose.density)
    compose.onNodeWithContentDescription("Cell actions").assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  private companion object {
    val MARK = CellMark(checked = false, position = 1)
    val CODE_CELL = Cell(id = "c1", kind = CellKind.CODE, source = "print(1)")
    val MARKDOWN_CELL = Cell(id = "m1", kind = CellKind.MARKDOWN, source = "# hi")
  }
}
