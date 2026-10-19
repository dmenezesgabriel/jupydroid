package com.jupydroid.app.ui.terminal

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The clipboard's two directions through the pane: what a program copies (OSC 52) comes out of
 * [TerminalPaneViewModel.clipboardCopies], and what is pasted goes to the shell through the
 * emulator's own `paste`, as `TermuxTerminalSessionActivityClient.onPasteTextFromClipboard` does
 * (termux-app `3df69d1d`).
 */
class TerminalPaneViewModelClipboardTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun anOsc52PayloadAProgramPrintsComesOutAsAClipboardCopy() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    attachOn(viewModel)
    advanceUntilIdle()

    gateway.push(TerminalEvent.Output("$ESC]52;c;SGVsbG8=\u0007"))
    advanceUntilIdle()

    assertEquals("Hello", viewModel.clipboardCopies.first())
  }

  /** Bash turns bracketed paste on, so a paste reaches it wrapped - and in order. */
  @Test
  fun aPasteUnderBracketedPasteReachesTheShellWrappedAndInOrder() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    attachOn(viewModel)
    advanceUntilIdle()
    gateway.push(TerminalEvent.Output("$ESC[?2004h"))
    advanceUntilIdle()

    viewModel.emulator.paste("echo hi\n")
    advanceUntilIdle()

    assertEquals("$ESC[200~echo hi\r$ESC[201~", gateway.sent.joinToString(""))
  }
}
