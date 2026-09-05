package com.jupydroid.app.ui.terminal

import androidx.compose.ui.unit.IntSize
import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.data.fake.FakeTerminalSettingsRepository
import com.jupydroid.app.domain.model.TerminalFontSize
import com.jupydroid.app.domain.model.TerminalSettings
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.terminal.CellStyle
import com.jupydroid.app.terminal.TerminalPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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

  /**
   * P4i-3b: `attach()` is a plain suspend function now, no longer launched for the caller in
   * `init` - `TerminalPaneScreen`'s own `repeatOnLifecycle(STARTED)` is what launches it for real,
   * so every test below launches it explicitly, the same shape.
   */
  @Test
  fun becomesReadyOnceTheGatewaySignalsIt() = runTest {
    val gateway = FakeTerminalGateway()
    val viewModel = TerminalPaneViewModel(gateway.open(), gateway)
    attachOn(viewModel)

    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.ready)
  }

  /** [TerminalPaneViewModel.type] is the user's own keystrokes - sent, then read back through the emulator. */
  @Test
  fun typedTextIsSentAndComesBackOntoTheScreen() = runTest {
    val gateway = FakeTerminalGateway()
    val viewModel = TerminalPaneViewModel(gateway.open(), gateway)
    attachOn(viewModel)
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
    val viewModel = TerminalPaneViewModel(session, gateway)
    attachOn(viewModel)
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
    attachOn(viewModel)
    advanceUntilIdle()

    gateway.close(session)
    advanceUntilIdle()

    assertEquals("terminal ${session.name} was closed", viewModel.uiState.value.endedReason)
  }

  /**
   * P4i-3b's own gate: "disposal cancels the collection." `attach()` is a plain suspend function,
   * so cancelling whatever job is collecting it cancels the flow it is collecting the same way any
   * other suspend function's caller would - this proves this class's own half, not
   * `repeatOnLifecycle` itself, which is already-tested androidx machinery this class only calls
   * correctly rather than re-verifies.
   */
  @Test
  fun cancellingAttachsJobStopsTheCollection() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    val job = attachOn(viewModel)
    advanceUntilIdle()

    job.cancel()
    advanceUntilIdle()

    assertTrue("attach()'s own collection should have been cancelled", gateway.attachWasCancelled)
  }

  /**
   * The other half of "a re-attach replays scrollback": scrollback replay itself is the real
   * server's job (`RealTerminalGateway`/`TermSocket`'s own `read_buffer`, not testable against a
   * fake with no replay cache), so this proves what is this class's to prove - a second `attach()`
   * call after the first was cancelled collects fresh events exactly as the first one did, rather
   * than being left in some half-started state by the cancellation.
   */
  @Test
  fun reattachingAfterACancelCollectsFreshEvents() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    attachOn(viewModel).also { advanceUntilIdle() }.cancel()

    attachOn(viewModel)
    advanceUntilIdle()
    gateway.push(TerminalEvent.Ready)
    gateway.push(TerminalEvent.Output("ls"))
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.ready)
    assertEquals("ls", firstRowOf(viewModel))
  }

  /**
   * A device run found this, not a spec: terminado's own `read_buffer` replays the *whole*
   * session's history on every attach, not only what was missed while backgrounded
   * (`terminado/management.py`'s own comment: "historical messages for reconnection", a
   * `deque(maxlen=1000)`, not an incremental log) - so appending a re-attach's events straight
   * onto an emulator that already drew them once duplicated the screen
   * (`evidence/p4i-terminal-background/`, `docs/DECISIONS.md`). A re-attach clears the screen and
   * scrollback first, so the replay becomes the sole source of truth instead of stacking onto
   * what backgrounding already left behind - provable against a fake by pushing the same content
   * twice, once per attach, the shape a real replay actually takes.
   */
  @Test
  fun reattachingClearsPriorContentBeforeTheReplay() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    // Cancelled before the re-attach below, matching real usage - exactly one collector of
    // `terminalGateway.attach` is ever active - and not merely left to leak: two live collectors
    // on the same flow would double-apply the second push regardless of this fix.
    val firstAttach = attachOn(viewModel)
    advanceUntilIdle()
    gateway.push(TerminalEvent.Ready)
    gateway.push(TerminalEvent.Output("hello"))
    advanceUntilIdle()
    assertEquals("hello", firstRowOf(viewModel))
    firstAttach.cancel()

    // Re-attach - backgrounding and foregrounding, P4i-3b's own shape - then the "server" replays
    // the same history a real one would resend in full.
    attachOn(viewModel)
    advanceUntilIdle()
    gateway.push(TerminalEvent.Ready)
    gateway.push(TerminalEvent.Output("hello"))
    advanceUntilIdle()

    assertEquals("a re-attach's replay must not duplicate what is already drawn", "hello", firstRowOf(viewModel))
    assertEquals("", rowAt(viewModel, 1))
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

  /**
   * Launches `attach()` on `Dispatchers.Main` - the same dispatcher [MainDispatcherRule] points at
   * `viewModelScope`'s own, so a keystroke sent through `type`/`resize` and this collector's flow
   * rendezvous on one scheduler exactly as they do in the real app, where `TerminalPaneScreen`'s
   * own `repeatOnLifecycle` effect runs on the same Main dispatcher `viewModelScope` does. A plain
   * `launch` here runs on `runTest`'s own, separate scheduler instead - harmless for a buffered
   * fake, but a real deadlock against `FakeTerminalGateway`'s unbuffered `printed` flow, which
   * needs its collector and `type`'s `send` pumped by the same scheduler to rendezvous at all.
   */
  // --- P4j-3a: a tapped/long-pressed Ctrl or Alt latch applied to soft-keyboard text ---

  @Test
  fun typingAfterATappedCtrlSendsTheControlByteNotTheLetter() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    advanceUntilIdle()

    viewModel.tapModifier(TerminalModifierKey.CTRL)
    viewModel.typeCharacters("c")
    advanceUntilIdle()

    assertEquals(listOf("\u0003"), gateway.sent)
  }

  @Test
  fun aTappedCtrlLatchClearsAfterOneCharacter() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    advanceUntilIdle()

    viewModel.tapModifier(TerminalModifierKey.CTRL)
    viewModel.typeCharacters("c")
    viewModel.typeCharacters("d")
    advanceUntilIdle()

    assertEquals(listOf("\u0003", "d"), gateway.sent)
  }

  @Test
  fun aLongPressedCtrlLatchConvertsEveryTypedCharacterUntilToggledOff() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    advanceUntilIdle()

    viewModel.longPressModifier(TerminalModifierKey.CTRL)
    viewModel.typeCharacters("a")
    viewModel.typeCharacters("b")
    advanceUntilIdle()

    assertEquals(listOf("\u0001", "\u0002"), gateway.sent)
  }

  @Test
  fun aTappedAltPrefixesTheNextCharacterWithEscape() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    advanceUntilIdle()

    viewModel.tapModifier(TerminalModifierKey.ALT)
    viewModel.typeCharacters("f")
    advanceUntilIdle()

    assertEquals(listOf("${ESC}f"), gateway.sent)
  }

  @Test
  fun theUiStateExposesTheCurrentLatches() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    advanceUntilIdle()

    viewModel.tapModifier(TerminalModifierKey.CTRL)

    assertEquals(TerminalModifierLatch(isActive = true), viewModel.uiState.value.latches.ctrl)
  }

  @Test
  fun aTapOnAnActiveLatchDisarmsItWithoutTouchingTheNextCharacter() = runTest {
    val gateway = RecordingGateway()
    val viewModel = TerminalPaneViewModel(TerminalSession("1"), gateway)
    advanceUntilIdle()

    viewModel.tapModifier(TerminalModifierKey.CTRL)
    viewModel.tapModifier(TerminalModifierKey.CTRL)
    viewModel.typeCharacters("c")
    advanceUntilIdle()

    assertEquals(listOf("c"), gateway.sent)
  }

  // --- P4j-3c: the terminal's own font-size setting ---

  /** The screen's own state carries whatever the repository already held, not always the default. */
  @Test
  fun theUiStateExposesTheStoredFontSize() = runTest {
    val settings = FakeTerminalSettingsRepository(TerminalSettings(fontSizeSp = 20))
    val viewModel =
      TerminalPaneViewModel(TerminalSession("1"), RecordingGateway(), terminalSettingsRepository = settings)

    advanceUntilIdle()

    assertEquals(20, viewModel.uiState.value.fontSizeSp)
  }

  @Test
  fun increaseFontSizeStepsUpAndPersists() = runTest {
    val settings = FakeTerminalSettingsRepository(TerminalSettings(fontSizeSp = 20))
    val viewModel =
      TerminalPaneViewModel(TerminalSession("1"), RecordingGateway(), terminalSettingsRepository = settings)
    advanceUntilIdle()

    viewModel.changeFontSize(TerminalFontSize.STEP)
    advanceUntilIdle()

    assertEquals(22, viewModel.uiState.value.fontSizeSp)
    assertEquals(22, settings.observeSettings().first().fontSizeSp)
  }

  @Test
  fun decreaseFontSizeStepsDownAndPersists() = runTest {
    val settings = FakeTerminalSettingsRepository(TerminalSettings(fontSizeSp = 20))
    val viewModel =
      TerminalPaneViewModel(TerminalSession("1"), RecordingGateway(), terminalSettingsRepository = settings)
    advanceUntilIdle()

    viewModel.changeFontSize(-TerminalFontSize.STEP)
    advanceUntilIdle()

    assertEquals(18, viewModel.uiState.value.fontSizeSp)
    assertEquals(18, settings.observeSettings().first().fontSizeSp)
  }

  /** JupyterLab's own `isEnabled` gate (`terminal-extension/src/index.ts:771`), reread as a clamp. */
  @Test
  fun increaseFontSizeStopsAtTheMaximum() = runTest {
    val settings = FakeTerminalSettingsRepository(TerminalSettings(fontSizeSp = TerminalFontSize.MAX))
    val viewModel =
      TerminalPaneViewModel(TerminalSession("1"), RecordingGateway(), terminalSettingsRepository = settings)
    advanceUntilIdle()

    viewModel.changeFontSize(TerminalFontSize.STEP)
    advanceUntilIdle()

    assertEquals(TerminalFontSize.MAX, viewModel.uiState.value.fontSizeSp)
  }

  /** The same gate from the other side - `terminal-extension/src/index.ts:791`. */
  @Test
  fun decreaseFontSizeStopsAtTheMinimum() = runTest {
    val settings = FakeTerminalSettingsRepository(TerminalSettings(fontSizeSp = TerminalFontSize.MIN))
    val viewModel =
      TerminalPaneViewModel(TerminalSession("1"), RecordingGateway(), terminalSettingsRepository = settings)
    advanceUntilIdle()

    viewModel.changeFontSize(-TerminalFontSize.STEP)
    advanceUntilIdle()

    assertEquals(TerminalFontSize.MIN, viewModel.uiState.value.fontSizeSp)
  }

  private fun TestScope.attachOn(viewModel: TerminalPaneViewModel): Job =
    backgroundScope.launch(Dispatchers.Main) { viewModel.attach() }

  private fun firstRowOf(viewModel: TerminalPaneViewModel): String = rowAt(viewModel, 0)

  // `spaceUsed` is the row's full column width, not its content's length - `RowText.kt`'s own
  // KDoc: "only the first [used] chars are the row", and a row starts fully space-filled. Trimmed
  // here, the way a caller comparing content rather than drawing cells wants it.
  private fun rowAt(viewModel: TerminalPaneViewModel, row: Int): String =
    viewModel.emulator.screen.grid.rowAt(row).let { String(it.text, 0, it.spaceUsed) }.trimEnd()

  /** A minimal spy - [FakeTerminalGateway] echoes what it is sent, which hides what was sent. */
  private class RecordingGateway : TerminalGateway {
    val sent = mutableListOf<String>()
    val resized = mutableListOf<Pair<Int, Int>>()

    /** Set from [attach]'s own `onCompletion` - true only once a collector was cancelled, not merely absent. */
    var attachWasCancelled = false
    private val events = MutableSharedFlow<TerminalEvent>(extraBufferCapacity = 8)

    override suspend fun open(cwd: String?) = TerminalSession("1")
    override suspend fun running(): List<TerminalSession> = emptyList()
    override fun attach(session: TerminalSession): Flow<TerminalEvent> =
      events.onCompletion { cause -> if (cause is CancellationException) attachWasCancelled = true }
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
