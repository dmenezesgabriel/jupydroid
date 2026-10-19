package com.jupydroid.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.terminal.TERMINAL_INPUT_TAG

/**
 * Presses the centre of [column] on the terminal's top row with [press], given the [cell] size
 * `terminalCellMetrics` measured - the canvas sits `Spacing.md` in from the input field the press
 * lands on.
 *
 * ```
 * compose.pressTerminalCell(cell, column = 4) { longClick(it) }
 * ```
 */
fun ComposeContentTestRule.pressTerminalCell(cell: IntSize?, column: Int, press: TouchInjectionScope.(Offset) -> Unit) {
  val measured = requireNotNull(cell) { "terminalCellMetrics was never composed" }
  val inset = with(density) { Spacing.md.toPx() }
  onNodeWithTag(TERMINAL_INPUT_TAG).performTouchInput {
    press(Offset(inset + measured.width * (column + 0.5f), measured.height * 0.5f))
  }
  waitForIdle()
}
