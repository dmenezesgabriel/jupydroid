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
import com.jupydroid.app.ui.terminal.DECREASE_TERMINAL_FONT_SIZE
import com.jupydroid.app.ui.terminal.INCREASE_TERMINAL_FONT_SIZE
import com.jupydroid.app.ui.terminal.PASTE_INTO_TERMINAL
import com.jupydroid.app.ui.terminal.TerminalBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * the terminal's own overflow, `terminal:increase-font`/
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

  /** `terminal:paste` (`terminal-extension/src/index.ts:717`), reachable with nothing selected. */
  @Test
  fun thePasteRowAsksToPaste() {
    showBar()

    compose.onNodeWithContentDescription("Terminal actions").performClick()
    compose.onNodeWithText(PASTE_INTO_TERMINAL).performClick()

    assertEquals(1, pastes)
  }

  private var increases = 0
  private var decreases = 0
  private var pastes = 0

  /**
   * The bar is the shell's own actions and nothing else - a terminal is not a file, but it is a
   * thing occupying the document area, and what empties that area is the tab strip's ✕, the one
   * place the reference keeps it. The one-pane arrow's cases are `MainAreaTabStripTest`'s.
   */
  @Test
  fun theBarHasNoDismissControl() {
    showBar()

    compose.onAllNodesWithContentDescription("Back", substring = true).assertCountEquals(0)
  }

  private fun showBar(fontSizeSp: Int = TerminalFontSize.DEFAULT) {
    compose.setContent {
      JupyterDroidTheme {
        TerminalBar(
          fontSizeSp = fontSizeSp,
          onIncreaseFontSize = { increases++ },
          onDecreaseFontSize = { decreases++ },
          onPaste = { pastes++ },
        )
      }
    }
  }
}
