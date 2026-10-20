package com.jupydroid.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.assertIsAtLeastMinimumTouchTarget
import com.jupydroid.app.ui.shell.WindowKeyRowHosted
import com.jupydroid.app.ui.terminal.TERMINAL_EXTRA_KEYS_TAG
import com.jupydroid.app.ui.terminal.TERMINAL_INPUT_TAG
import com.jupydroid.app.ui.terminal.TerminalPaneScreen
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * the row a touchscreen terminal needs that a soft keyboard cannot
 * produce on its own - `SymbolRowTest`'s own shape, composing [TerminalPaneScreen] directly
 * against a recording fake exactly as [TerminalHardwareKeyTest] does, since this row's keys must
 * reach a real shell the same way a hardware key already does.
 */
@RunWith(AndroidJUnit4::class)
class TerminalExtraKeysRowTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun tappingEscSendsTheBareEscapeByte() {
    val gateway = openTerminalScreen()

    compose.onNodeWithText("Esc").performScrollTo().performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { gateway.sent.isNotEmpty() }
    assertEquals(listOf("\u001B"), gateway.sent)
  }

  @Test
  fun tappingTabSendsTheHorizontalTabByte() {
    val gateway = openTerminalScreen()

    compose.onNodeWithText("Tab").performScrollTo().performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { gateway.sent.isNotEmpty() }
    assertEquals(listOf("\t"), gateway.sent)
  }

  @Test
  fun arrowAndNavigationKeysReachTheShellAsTheirOwnSequences() {
    val gateway = openTerminalScreen()

    compose.onNodeWithText("\u2191").performScrollTo().performClick()
    compose.onNodeWithText("Home").performScrollTo().performClick()
    compose.onNodeWithText("PgUp").performScrollTo().performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { gateway.sent.size == 3 }
    assertEquals(listOf("\u001B[A", "\u001B[H", "\u001B[5~"), gateway.sent)
  }

  /** The gate 3a itself names: a tapped Ctrl converts the next typed letter. */
  @Test
  fun tappingCtrlThenTypingALetterSendsItsControlByte() {
    val gateway = openTerminalScreen()

    compose.onNodeWithContentDescription("Ctrl").performScrollTo().performClick()
    compose.onNodeWithTag(TERMINAL_INPUT_TAG).performTextInput("c")

    compose.waitUntil(TIMEOUT_MILLIS) { gateway.sent.isNotEmpty() }
    assertEquals(listOf("\u0003"), gateway.sent)
  }

  @Test
  fun aLongPressOnCtrlLocksItActiveAcrossMultipleLetters() {
    val gateway = openTerminalScreen()

    compose.onNodeWithContentDescription("Ctrl").performScrollTo().performTouchInput { longClick() }
    compose.onNodeWithTag(TERMINAL_INPUT_TAG).performTextInput("ab")

    compose.waitUntil(TIMEOUT_MILLIS) { gateway.sent.isNotEmpty() }
    assertEquals(listOf("\u0001\u0002"), gateway.sent)
  }

  @Test
  fun theCtrlKeyIsDescribedAsActiveWhileArmedAndPlainOnceConsumed() {
    val gateway = openTerminalScreen()

    compose.onNodeWithContentDescription("Ctrl").performScrollTo().performClick()
    compose.onNodeWithContentDescription("Ctrl, active").assertExists()

    compose.onNodeWithTag(TERMINAL_INPUT_TAG).performTextInput("c")
    compose.waitUntil(TIMEOUT_MILLIS) { gateway.sent.isNotEmpty() }
    compose.onNodeWithContentDescription("Ctrl").assertExists()
  }

  /**
   * Beside a second pane the row is the window's, as `WindowKeyRowTest` says of any published row,
   * and the latches it draws there are still the terminal's: drawn by the host, a tap still arms Ctrl
   * and a long-press still locks it.
   */
  @Test
  fun aHostedRowSpansTheWindowAndItsCtrlStillArmsAndLocks() {
    val gateway = RecordingTerminalGateway()
    compose.setContent {
      JupyterDroidTheme {
        WindowKeyRowHosted {
          Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(200.dp))
            Box(modifier = Modifier.weight(1f)) {
              TerminalPaneScreen(session = TerminalSession("1"), terminalGateway = gateway)
            }
          }
        }
      }
    }
    compose.waitUntil(TIMEOUT_MILLIS) { gateway.attached }

    val rowWidth = compose.onNodeWithTag(TERMINAL_EXTRA_KEYS_TAG).getBoundsInRoot().width
    assertEquals(compose.onRoot().getBoundsInRoot().width, rowWidth)
    compose.onNodeWithContentDescription("Ctrl").performScrollTo().performClick()
    compose.onNodeWithContentDescription("Ctrl, active").assertExists()
    compose.onNodeWithContentDescription("Ctrl, active").performClick()
    compose.onNodeWithContentDescription("Ctrl").performTouchInput { longClick() }
    compose.onNodeWithContentDescription("Ctrl, locked").assertExists()
    compose.onNodeWithTag(TERMINAL_INPUT_TAG).performTextInput("ab")
    compose.waitUntil(TIMEOUT_MILLIS) { gateway.sent.isNotEmpty() }
    assertEquals(listOf("\u0001\u0002"), gateway.sent)
  }

  @Test
  fun everyKeyClearsTheMinimumTouchTarget() {
    openTerminalScreen()

    compose.onNodeWithText("Esc").performScrollTo().assertIsAtLeastMinimumTouchTarget(compose.density)
    compose.onNodeWithContentDescription("Ctrl").performScrollTo().assertIsAtLeastMinimumTouchTarget(compose.density)
    compose.onNodeWithContentDescription("Alt").performScrollTo().assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  /** Composes [TerminalPaneScreen] against a fresh, attached [RecordingTerminalGateway]. */
  private fun openTerminalScreen(): RecordingTerminalGateway {
    val gateway = RecordingTerminalGateway()
    compose.setContent {
      JupyterDroidTheme {
        TerminalPaneScreen(session = TerminalSession("1"), terminalGateway = gateway)
      }
    }
    compose.waitUntil(TIMEOUT_MILLIS) { gateway.attached }
    return gateway
  }

  /** Records what reaches [send] rather than echoing it - a raw C0 byte draws nothing to see. */
  private class RecordingTerminalGateway : TerminalGateway {
    val sent = mutableListOf<String>()
    var attached = false

    override suspend fun open(cwd: String?) = TerminalSession("1")

    override suspend fun running(): List<TerminalSession> = emptyList()

    override fun attach(session: TerminalSession): Flow<TerminalEvent> = flow {
      attached = true
      emit(TerminalEvent.Ready)
      awaitCancellation()
    }

    override suspend fun send(session: TerminalSession, text: String) {
      sent += text
    }

    override suspend fun resize(session: TerminalSession, rows: Int, cols: Int) = Unit

    override suspend fun close(session: TerminalSession) = Unit
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
