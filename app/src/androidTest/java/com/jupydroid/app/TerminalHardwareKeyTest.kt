package com.jupydroid.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.theme.JupyterDroidTheme
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
 * `docs/PLAN.md`'s P4j-2: a hardware Ctrl+C reaches the shell as the SIGINT byte, not the letter
 * `c`, and an arrow key reaches it as a cursor-motion escape rather than moving
 * `BasicTextField`'s own caret - `TerminalGateway.send`'s own KDoc names `"\u0003"` as Ctrl-C. A device-real check:
 * `performKeyInput` dispatches through the same `onPreviewKeyEvent` a physical or bluetooth
 * keyboard would, which a JVM test - [com.jupydroid.app.ui.terminal.TerminalKeyEncodingTest] -
 * cannot exercise on its own.
 */
@OptIn(ExperimentalTestApi::class) // `performKeyInput`'s own opt-in requirement
@RunWith(AndroidJUnit4::class)
class TerminalHardwareKeyTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun ctrlCReachesTheShellAsTheSigintByte() {
    val gateway = openTerminalScreen()

    compose.onNodeWithTag(TERMINAL_INPUT_TAG).performKeyInput {
      withKeyDown(Key.CtrlLeft) { pressKey(Key.C) }
    }

    compose.waitUntil(TIMEOUT_MILLIS) { gateway.sent.isNotEmpty() }
    assertEquals(listOf("\u0003"), gateway.sent)
  }

  @Test
  fun upArrowReachesTheShellAsCsiA() {
    val gateway = openTerminalScreen()

    compose.onNodeWithTag(TERMINAL_INPUT_TAG).performKeyInput { pressKey(Key.DirectionUp) }

    compose.waitUntil(TIMEOUT_MILLIS) { gateway.sent.isNotEmpty() }
    assertEquals(listOf("\u001B[A"), gateway.sent)
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
