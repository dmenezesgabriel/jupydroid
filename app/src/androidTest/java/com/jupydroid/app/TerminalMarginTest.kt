package com.jupydroid.app

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.terminal.TERMINAL_INPUT_TAG
import com.jupydroid.app.ui.terminal.TerminalPaneScreen
import com.jupydroid.app.ui.terminal.TerminalPaneViewModel
import com.jupydroid.app.ui.terminal.terminalCellMetrics
import com.jupydroid.app.ui.terminal.terminalMargin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The terminal's text sits `terminalMargin` (`ui/terminal/TerminalGridSize.kt`) in from the pane's
 * left and right edges, which is two facts a layout dump cannot state: the columns the shell is
 * told count only the width inside the margin, and a press maps to the cell drawn under it rather
 * than one a margin away. Both are asked of the real screen, since the margin is a modifier on it.
 */
@RunWith(AndroidJUnit4::class)
class TerminalMarginTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val gateway = FakeTerminalGateway()
  private val session = runBlocking { gateway.open() }
  private val viewModel = TerminalPaneViewModel(session, gateway)
  private var cell by mutableStateOf<Size?>(null)

  @Before
  fun showTheTerminal() {
    // Pinned, as `TerminalPaneScreenTest` pins it: the tablet's own landscape default moves the width.
    compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    compose.waitForIdle()
    compose.setContent {
      JupydroidTheme {
        cell = terminalCellMetrics()
        TerminalPaneScreen(session = session, terminalGateway = gateway, viewModel = viewModel)
      }
    }
  }

  @Test
  fun theColumnsTheShellIsToldCountOnlyTheWidthInsideTheMargin() {
    val paneWidth = compose.onNodeWithTag(TERMINAL_INPUT_TAG).fetchSemanticsNode().size.width
    val margin = with(compose.density) { terminalMargin.roundToPx() }
    val cellWidth = requireNotNull(cell) { "terminalCellMetrics was never composed" }.width

    assertEquals(
      "a $paneWidth px pane with a $margin px margin on each side, at $cellWidth px per column",
      ((paneWidth - 2 * margin) / cellWidth).toInt(),
      settledColumns(),
    )
  }

  /** One-letter words, two columns apart, so a press a margin away from its cell selects another word. */
  @Test
  fun aLongPressSelectsTheWordUnderTheCellDrawnThere() {
    compose.runOnUiThread {
      val bytes = "a b c d e f g h".toByteArray()
      viewModel.emulator.append(bytes, bytes.size)
    }

    compose.pressTerminalCell(cell, column = WORD_COLUMN) { longClick(it) }

    val selected = requireNotNull(viewModel.selector.selection.value) { "the long press selected nothing" }
    assertEquals(WORD_COLUMN, selected.startColumn)
  }

  /**
   * A resize is a cascade of relayouts, so two consecutive idle passes that agree are what says it
   * has finished (`TerminalPaneScreenTest.settledRows`).
   */
  private fun settledColumns(): Int {
    var previous = viewModel.emulator.screen.columns
    repeat(MAX_SETTLE_PASSES) {
      compose.waitForIdle()
      val current = viewModel.emulator.screen.columns
      if (current == previous) return current
      previous = current
    }
    return previous
  }

  private companion object {
    /** The `c` of `a b c d e f g h`. */
    const val WORD_COLUMN = 4
    const val MAX_SETTLE_PASSES = 10
  }
}
