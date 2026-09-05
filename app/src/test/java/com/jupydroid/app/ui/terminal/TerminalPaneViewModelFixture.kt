package com.jupydroid.app.ui.terminal

import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.terminal.CellStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

/**
 * What [TerminalPaneViewModelTest] and [TerminalPaneViewModelScrollbackTest] both need to drive a
 * [TerminalPaneViewModel] without a real [TerminalGateway] - split out once the first of those two
 * files crossed the 500-line file-length gate (`docs/PLAN.md`'s P4j-3d-4).
 */
internal const val ESC = "\u001B"

/** A minimal spy - `FakeTerminalGateway` echoes what it is sent, which hides what was sent. */
internal class RecordingGateway : TerminalGateway {
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

/**
 * Launches `attach()` on `Dispatchers.Main` - the same dispatcher `MainDispatcherRule` points at
 * `viewModelScope`'s own, so a keystroke sent through `type`/`resize` and this collector's flow
 * rendezvous on one scheduler exactly as they do in the real app, where `TerminalPaneScreen`'s
 * own `repeatOnLifecycle` effect runs on the same Main dispatcher `viewModelScope` does. A plain
 * `launch` here runs on `runTest`'s own, separate scheduler instead - harmless for a buffered
 * fake, but a real deadlock against `FakeTerminalGateway`'s unbuffered `printed` flow, which
 * needs its collector and `type`'s `send` pumped by the same scheduler to rendezvous at all.
 */
internal fun TestScope.attachOn(viewModel: TerminalPaneViewModel): Job =
  backgroundScope.launch(Dispatchers.Main) { viewModel.attach() }

internal fun firstRowOf(viewModel: TerminalPaneViewModel): String = rowAt(viewModel, 0)

// `spaceUsed` is the row's full column width, not its content's length - `RowText.kt`'s own
// KDoc: "only the first [used] chars are the row", and a row starts fully space-filled. Trimmed
// here, the way a caller comparing content rather than drawing cells wants it.
internal fun rowAt(viewModel: TerminalPaneViewModel, row: Int): String =
  viewModel.emulator.screen.grid.rowAt(row).let { String(it.text, 0, it.spaceUsed) }.trimEnd()

internal fun scrollDownOneLine(viewModel: TerminalPaneViewModel) {
  viewModel.emulator.screen.grid.scrollDownOneLine(0, viewModel.emulator.screen.rows, CellStyle.NORMAL)
}

/** `docs/PLAN.md`'s P4j-3d-4 - `CSI ? 1049 h`, the same sequence `AlternateScreenTest` transcribes. */
internal fun enterAlternateScreen(viewModel: TerminalPaneViewModel) {
  val bytes = "$ESC[?1049h".toByteArray()
  viewModel.emulator.append(bytes, bytes.size)
}
