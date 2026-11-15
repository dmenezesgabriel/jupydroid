package com.jupydroid.app.ui.terminal

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The terminal's input paints nothing and takes focus and the keyboard over the whole screen, so a
 * screen reader meets a focusable edit box it can only call "edit box" unless the field carries a
 * name (TalkBack's rule: every actionable node has one). The name sits on the node a flow addresses
 * by [TERMINAL_INPUT_TAG], so the one node is both what is spoken and what a flow taps.
 */
@RunWith(AndroidJUnit4::class)
class TerminalInputNameTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theTerminalsInputIsOneNamedNodeThatTakesText() {
    val gateway = FakeTerminalGateway()
    val session = runBlocking { gateway.open() }
    compose.setContent {
      JupyterDroidTheme { TerminalPaneScreen(session = session, terminalGateway = gateway) }
    }
    compose.waitForIdle()

    val named = hasContentDescription("Terminal input") and hasSetTextAction() and hasTestTag(TERMINAL_INPUT_TAG)
    assertEquals(1, compose.onAllNodes(named).fetchSemanticsNodes().size)
  }
}
