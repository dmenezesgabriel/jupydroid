package com.jupydroid.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.TerminalFontSize
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.CLOSE_DOCUMENT
import com.jupydroid.app.ui.terminal.DECREASE_TERMINAL_FONT_SIZE
import com.jupydroid.app.ui.terminal.INCREASE_TERMINAL_FONT_SIZE
import com.jupydroid.app.ui.terminal.TerminalBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `docs/PLAN.md`'s P4j-3c: the terminal's own overflow, `terminal:increase-font`/
 * `terminal:decrease-font` (`terminal-extension/src/index.ts:64-66`).
 *
 * Drives [TerminalBar] rather than [com.jupydroid.app.ui.terminal.TerminalActionsMenu] on its
 * own, [NotebookActionsMenuTest][com.jupydroid.app.ui.editor.NotebookActionsMenuTest]'s own
 * reason: what this asserts is that the row is *reachable*, and a test composing the menu
 * directly would still pass with its own opening button gone.
 */
@RunWith(AndroidJUnit4::class)
class TerminalActionsMenuTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theTerminalMenuOffersBothFontSizeCommands() {
    showBar()

    compose.onNodeWithContentDescription("Terminal actions").performClick()

    compose.onNodeWithText(INCREASE_TERMINAL_FONT_SIZE).assertIsDisplayed()
    compose.onNodeWithText(DECREASE_TERMINAL_FONT_SIZE).assertIsDisplayed()
  }

  @Test
  fun theIncreaseRowAsksToIncrease() {
    showBar()

    compose.onNodeWithContentDescription("Terminal actions").performClick()
    compose.onNodeWithText(INCREASE_TERMINAL_FONT_SIZE).performClick()

    assertEquals(1, increases)
  }

  @Test
  fun theDecreaseRowAsksToDecrease() {
    showBar()

    compose.onNodeWithContentDescription("Terminal actions").performClick()
    compose.onNodeWithText(DECREASE_TERMINAL_FONT_SIZE).performClick()

    assertEquals(1, decreases)
  }

  /** JupyterLab's own `isEnabled` gate (`terminal-extension/src/index.ts:771`), read as disabled rather than hidden. */
  @Test
  fun theIncreaseRowIsDisabledAtTheMaximum() {
    showBar(fontSizeSp = TerminalFontSize.MAX)

    compose.onNodeWithContentDescription("Terminal actions").performClick()

    compose.onNodeWithText(INCREASE_TERMINAL_FONT_SIZE).assertIsNotEnabled()
  }

  /** The same gate from the other side - `:791`. */
  @Test
  fun theDecreaseRowIsDisabledAtTheMinimum() {
    showBar(fontSizeSp = TerminalFontSize.MIN)

    compose.onNodeWithContentDescription("Terminal actions").performClick()

    compose.onNodeWithText(DECREASE_TERMINAL_FONT_SIZE).assertIsNotEnabled()
  }

  private var increases = 0
  private var decreases = 0

  /**
   * Beside a browser the shell is closed rather than backed out of, exactly as both documents are -
   * a terminal is not a file, but it is a thing occupying the document area, and what empties that
   * area has one name.
   */
  @Test
  fun besideABrowserTheShellIsClosedRatherThanBackedOutOf() {
    showBar(twoPane = true)

    compose.onNodeWithContentDescription(CLOSE_DOCUMENT).assertIsDisplayed()
    compose.onAllNodesWithContentDescription("Back").assertCountEquals(0)
  }

  /** Below two panes the arrow stays, because there the shell *is* the window. */
  @Test
  fun belowTwoPanesTheArrowStays() {
    showBar()

    compose.onNodeWithContentDescription("Back").assertIsDisplayed()
    compose.onAllNodesWithContentDescription(CLOSE_DOCUMENT).assertCountEquals(0)
  }

  private fun showBar(fontSizeSp: Int = TerminalFontSize.DEFAULT, twoPane: Boolean = false) {
    compose.setContent {
      JupyterDroidTheme {
        TerminalBar(
          title = "Terminal 1",
          onBack = {},
          fontSizeSp = fontSizeSp,
          onIncreaseFontSize = { increases++ },
          onDecreaseFontSize = { decreases++ },
          twoPane = twoPane,
        )
      }
    }
  }
}
