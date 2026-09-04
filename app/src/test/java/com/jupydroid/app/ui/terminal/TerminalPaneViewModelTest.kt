package com.jupydroid.app.ui.terminal

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val ESC = "\u001B"

/**
 * Binding the already-built [com.jupydroid.app.domain.port.TerminalGateway] to the already-built
 * `terminal/` emulator - the wiring P4i's own row exists for. `FakeTerminalGateway.send` echoes
 * back whatever it is given as `Output`, so it stands in for a real shell that types back what it
 * was sent.
 */
class TerminalPaneViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun becomesReadyOnceTheGatewaySignalsIt() = runTest {
    val gateway = FakeTerminalGateway()
    val viewModel = TerminalPaneViewModel(gateway.open(), gateway)

    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.ready)
  }

  /** [TerminalPaneViewModel.type] is the user's own keystrokes - sent, then read back through the emulator. */
  @Test
  fun typedTextIsSentAndComesBackOntoTheScreen() = runTest {
    val gateway = FakeTerminalGateway()
    val viewModel = TerminalPaneViewModel(gateway.open(), gateway)
    advanceUntilIdle()

    viewModel.type("ls\r\n")
    advanceUntilIdle()

    assertEquals("ls", firstRowOf(viewModel))
  }

  /**
   * The emulator's *own* replies - DA1 here - go back to the shell through `TerminalGateway.send`
   * exactly as typed text does, which is what `TerminalReplies`'s KDoc says P4i binds: an escape
   * sequence is consumed whole by the parser and never becomes a printable glyph, so
   * [firstRowOf]'s screen-reading trick from [typedTextIsSentAndComesBackOntoTheScreen] cannot
   * prove this - only what actually reached [TerminalGateway.send] can. [RecordingGateway] records
   * exactly that. Expected reply transcribed from `TerminalReportingTest.deviceAttributesReport`.
   */
  @Test
  fun theEmulatorsOwnRepliesAreSentBackToTheShell() = runTest {
    val gateway = RecordingGateway()
    val session = TerminalSession("1")
    TerminalPaneViewModel(session, gateway)
    advanceUntilIdle()

    // Standing in for a program's own stdout - `vim` and friends query the terminal this way,
    // over the same channel a shell prints anything else on.
    gateway.push(TerminalEvent.Output("$ESC[c"))
    advanceUntilIdle()

    assertEquals(listOf("$ESC[?64;1;2;6;9;15;18;21;22c"), gateway.sent)
  }

  @Test
  fun endingTheTerminalSurfacesItsReason() = runTest {
    val gateway = FakeTerminalGateway()
    val session = gateway.open()
    val viewModel = TerminalPaneViewModel(session, gateway)
    advanceUntilIdle()

    gateway.close(session)
    advanceUntilIdle()

    assertEquals("terminal ${session.name} was closed", viewModel.uiState.value.endedReason)
  }

  // `spaceUsed` is the row's full column width, not its content's length - `RowText.kt`'s own
  // KDoc: "only the first [used] chars are the row", and a row starts fully space-filled. Trimmed
  // here, the way a caller comparing content rather than drawing cells wants it.
  private fun firstRowOf(viewModel: TerminalPaneViewModel): String =
    viewModel.emulator.screen.grid.rowAt(0).let { String(it.text, 0, it.spaceUsed) }.trimEnd()

  /** A minimal spy - [FakeTerminalGateway] echoes what it is sent, which hides what was sent. */
  private class RecordingGateway : TerminalGateway {
    val sent = mutableListOf<String>()
    private val events = MutableSharedFlow<TerminalEvent>(extraBufferCapacity = 8)

    override suspend fun open(cwd: String?) = TerminalSession("1")
    override suspend fun running(): List<TerminalSession> = emptyList()
    override fun attach(session: TerminalSession): Flow<TerminalEvent> = events
    override suspend fun send(session: TerminalSession, text: String) {
      sent += text
    }
    override suspend fun resize(session: TerminalSession, rows: Int, cols: Int) = Unit
    override suspend fun close(session: TerminalSession) = Unit

    suspend fun push(event: TerminalEvent) = events.emit(event)
  }
}
