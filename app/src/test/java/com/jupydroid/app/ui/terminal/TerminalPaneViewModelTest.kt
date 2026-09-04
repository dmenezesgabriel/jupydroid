package com.jupydroid.app.ui.terminal

import androidx.compose.ui.unit.IntSize
import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.terminal.CellStyle
import com.jupydroid.app.terminal.TerminalPalette
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

  /**
   * P4i-2a: the palette a caller passes is what the emulator actually draws with, not always
   * [com.jupydroid.app.terminal.TerminalPalette.REFERENCE] - `TerminalPaneScreen`'s own job is
   * picking [com.jupydroid.app.terminal.TerminalPalette.VS_CODE_DARK] or `_LIGHT` from the
   * system theme; this only has to prove the constructor still threads whatever it is given.
   */
  @Test
  fun threadsTheGivenPaletteIntoTheEmulator() = runTest {
    val gateway = FakeTerminalGateway()
    val viewModel = TerminalPaneViewModel(gateway.open(), gateway, palette = TerminalPalette.VS_CODE_LIGHT)

    assertEquals(
      TerminalPalette.VS_CODE_LIGHT.at(CellStyle.COLOR_INDEX_BACKGROUND),
      viewModel.emulator.screen.colors.at(CellStyle.COLOR_INDEX_BACKGROUND),
    )
  }

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

  /**
   * P4i-3a: a measured grid reaches both halves `TerminalGateway.resize`'s own KDoc names -
   * `emulator.resize` (what is drawn) and the wire `set_size` (what the pty is told), rows before
   * columns since that is the wire's own order, not a preference.
   */
  @Test
  fun resizeUpdatesTheEmulatorAndWritesTheGatewaysSetSize() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    advanceUntilIdle()

    viewModel.resize(IntSize(80, 24), cellWidthPixels = 10, cellHeightPixels = 20)
    advanceUntilIdle()

    assertEquals(80, viewModel.emulator.screen.columns)
    assertEquals(24, viewModel.emulator.screen.rows)
    assertEquals(listOf(24 to 80), gateway.resized)
  }

  /**
   * The regression P4i-3a's own gate names: a recomposition that reports the same grid must not
   * write the wire again.
   */
  @Test
  fun resizeIsANoOpWhenTheGridSizeIsUnchanged() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    advanceUntilIdle()

    viewModel.resize(IntSize(80, 24), cellWidthPixels = 10, cellHeightPixels = 20)
    viewModel.resize(IntSize(80, 24), cellWidthPixels = 10, cellHeightPixels = 20)
    advanceUntilIdle()

    assertEquals("a repeated grid size must not write the wire a second time", 1, gateway.resized.size)
  }

  @Test
  fun resizeWritesAgainWhenTheGridSizeActuallyChanges() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    advanceUntilIdle()

    viewModel.resize(IntSize(80, 24), cellWidthPixels = 10, cellHeightPixels = 20)
    viewModel.resize(IntSize(40, 24), cellWidthPixels = 10, cellHeightPixels = 20)
    advanceUntilIdle()

    assertEquals(listOf(24 to 80, 24 to 40), gateway.resized)
  }

  /**
   * `TerminalCanvas`'s own draw loop bounds itself to `screen.rows`/`columns` captured at its last
   * composition - only a fresh `key(state.redraw)` rebuild picks up what [TerminalEmulator.resize]
   * just mutated in place, the same reason every terminal `Output` event already bumps this.
   */
  @Test
  fun resizeBumpsRedrawSoTheCanvasPicksUpTheNewGrid() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    advanceUntilIdle()
    val before = viewModel.uiState.value.redraw

    viewModel.resize(IntSize(80, 24), cellWidthPixels = 10, cellHeightPixels = 20)

    assertEquals(before + 1, viewModel.uiState.value.redraw)
  }

  @Test
  fun resizeDoesNotBumpRedrawWhenTheGridSizeIsUnchanged() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    advanceUntilIdle()
    viewModel.resize(IntSize(80, 24), cellWidthPixels = 10, cellHeightPixels = 20)
    val afterFirst = viewModel.uiState.value.redraw

    viewModel.resize(IntSize(80, 24), cellWidthPixels = 10, cellHeightPixels = 20)

    assertEquals(afterFirst, viewModel.uiState.value.redraw)
  }

  // `spaceUsed` is the row's full column width, not its content's length - `RowText.kt`'s own
  // KDoc: "only the first [used] chars are the row", and a row starts fully space-filled. Trimmed
  // here, the way a caller comparing content rather than drawing cells wants it.
  private fun firstRowOf(viewModel: TerminalPaneViewModel): String =
    viewModel.emulator.screen.grid.rowAt(0).let { String(it.text, 0, it.spaceUsed) }.trimEnd()

  /** A minimal spy - [FakeTerminalGateway] echoes what it is sent, which hides what was sent. */
  private class RecordingGateway : TerminalGateway {
    val sent = mutableListOf<String>()
    val resized = mutableListOf<Pair<Int, Int>>()
    private val events = MutableSharedFlow<TerminalEvent>(extraBufferCapacity = 8)

    override suspend fun open(cwd: String?) = TerminalSession("1")
    override suspend fun running(): List<TerminalSession> = emptyList()
    override fun attach(session: TerminalSession): Flow<TerminalEvent> = events
    override suspend fun send(session: TerminalSession, text: String) {
      sent += text
    }
    override suspend fun resize(session: TerminalSession, rows: Int, cols: Int) {
      resized += rows to cols
    }
    override suspend fun close(session: TerminalSession) = Unit

    suspend fun push(event: TerminalEvent) = events.emit(event)
  }
}
