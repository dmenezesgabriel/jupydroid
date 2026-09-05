package com.jupydroid.app.ui.terminal

import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.terminal.TerminalEmulator
import com.jupydroid.app.terminal.TerminalNotices
import com.jupydroid.app.terminal.TerminalPalette
import com.jupydroid.app.terminal.TerminalReplies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** `ESC`, for the cursor-home/clear-screen/clear-scrollback sequence [TerminalPaneViewModel.attach] sends itself. */
private const val ESC = ""

/**
 * What the terminal screen has to show, beyond the mutable grid [TerminalPaneViewModel.emulator]
 * already carries.
 *
 * @property redraw bumped on every [TerminalEvent.Output] and colour change, so the screen has a
 *   `State` to key its recomposition on - `emulator.screen.grid` is mutated in place and Compose
 *   cannot observe that on its own.
 * @property latches [TerminalExtraKeysRow]'s own Ctrl/Alt state - `docs/PLAN.md`'s P4j-3a.
 */
data class TerminalPaneUiState(
  val ready: Boolean = false,
  val redraw: Int = 0,
  val title: String? = null,
  val endedReason: String? = null,
  val latches: TerminalModifierLatches = TerminalModifierLatches(),
)

/**
 * Binds an already-open [TerminalSession] to the `terminal/` emulator that P4a-P4h already built.
 *
 * Two directions, and [TerminalReplies]/[TerminalNotices]'s own KDoc names this ViewModel as the
 * one that tells them apart: what the emulator writes back to the shell (a query it answered on
 * its own, such as DA1) goes to [TerminalGateway.send] exactly as a keystroke does; what it tells
 * the surface around it (bell, title, colours, clipboard) updates [uiState] instead.
 *
 * ```
 * val viewModel = TerminalPaneViewModel(TerminalSession("1"), terminalGateway)
 * viewModel.type("ls\n")
 * ```
 */
class TerminalPaneViewModel(
  private val session: TerminalSession,
  private val terminalGateway: TerminalGateway,
  palette: TerminalPalette = TerminalPalette.VS_CODE_DARK,
  columns: Int = DEFAULT_COLUMNS,
  rows: Int = DEFAULT_ROWS,
) : ViewModel() {
  private val _uiState = MutableStateFlow(TerminalPaneUiState())
  val uiState: StateFlow<TerminalPaneUiState> = _uiState.asStateFlow()

  private val replies = object : TerminalReplies {
    override fun reply(text: String) = sendToShell(text)
  }
  private val notices = object : TerminalNotices {
    override fun onBell() = Unit
    override fun onTitleChanged(oldTitle: String?, newTitle: String?) {
      _uiState.update { it.copy(title = newTitle) }
    }
    override fun onColorsChanged() = bumpRedraw()
    override fun onClipboardCopy(text: String) = Unit
  }

  /** The VT100 state this screen draws - P4a-P4h's, unowned by this class beyond feeding it bytes. */
  val emulator = TerminalEmulator(
    columns = columns,
    rows = rows,
    notices = notices,
    replies = replies,
    palette = palette,
  )

  /** Whether [attach] has already run once - a second call is a re-attach, not the first connection. */
  private var hasAttachedBefore = false

  /**
   * Collects [terminalGateway]'s events for as long as this call is not cancelled - `docs/PLAN.md`'s
   * P4i-3b. No longer launched for the caller in `init`: `TerminalPaneScreen`'s own
   * `repeatOnLifecycle(Lifecycle.State.STARTED)` is what decides when, so backgrounding cancels this
   * (closing the socket - `TerminalGateway.attach`'s own KDoc) and coming back calls it again, which
   * is a re-attach the server answers by replaying what was missed.
   *
   * **A re-attach clears the screen and scrollback first, a device run rather than a spec found
   * necessary.** terminado's own `read_buffer` (`management.py`: `deque(maxlen=1000)`, "historical
   * messages for reconnection") replays the *whole* session so far, not only what changed while
   * this was cancelled - appending that onto an emulator that already drew it once duplicated the
   * screen (`evidence/p4i-terminal-background/`, `docs/DECISIONS.md`). `ESC[H` homes the cursor,
   * `ESC[2J` blanks the visible screen and `ESC[3J` forgets the scrollback - all three already
   * `terminal/`'s own (P4f/P4h), reused rather than a new "clear" primitive invented for this.
   *
   * ```
   * lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.attach() }
   * ```
   */
  suspend fun attach() {
    if (hasAttachedBefore) {
      val clear = "$ESC[H$ESC[2J$ESC[3J".toByteArray(Charsets.UTF_8)
      emulator.append(clear, clear.size)
      bumpRedraw()
    }
    hasAttachedBefore = true

    terminalGateway.attach(session).collect { event ->
      when (event) {
        TerminalEvent.Ready -> _uiState.update { it.copy(ready = true) }
        is TerminalEvent.Output -> {
          val bytes = event.text.toByteArray(Charsets.UTF_8)
          emulator.append(bytes, bytes.size)
          bumpRedraw()
        }
        is TerminalEvent.Ended -> _uiState.update { it.copy(endedReason = event.reason) }
      }
    }
  }

  /**
   * A pre-encoded keystroke, sent verbatim - the other half of [replies], and how a hardware
   * key's own [terminalKeystroke] bytes and [TerminalExtraKeysRow]'s own Esc/Tab/nav taps reach
   * the shell. [typeCharacters] is soft-keyboard text's own entry point instead - applying a
   * latch to bytes that are already an escape sequence would corrupt them character-by-character.
   */
  fun type(text: String) = sendToShell(text)

  /**
   * Literal soft-keyboard text (or the backspace run `sendTerminalDiff` turns a shortened prefix
   * into) - `docs/PLAN.md`'s P4j-3a: a tapped Ctrl/Alt latch applies to it the same way a
   * hardware key's own `ctrlPressed` applies to [terminalKeystroke].
   */
  fun typeCharacters(text: String) {
    val (encoded, next) = applyModifierLatches(text, _uiState.value.latches)
    _uiState.update { it.copy(latches = next) }
    sendToShell(encoded)
  }

  /** A tap on [TerminalExtraKeysRow]'s Ctrl/Alt key - [TerminalModifierLatch.tapped]. */
  fun tapModifier(key: TerminalModifierKey) = updateLatch(key) { it.tapped() }

  /** A long-press on [TerminalExtraKeysRow]'s Ctrl/Alt key - [TerminalModifierLatch.longPressed]. */
  fun longPressModifier(key: TerminalModifierKey) = updateLatch(key) { it.longPressed() }

  private fun updateLatch(key: TerminalModifierKey, transform: (TerminalModifierLatch) -> TerminalModifierLatch) {
    _uiState.update { state ->
      val latches = state.latches
      val next = when (key) {
        TerminalModifierKey.CTRL -> latches.copy(ctrl = transform(latches.ctrl))
        TerminalModifierKey.ALT -> latches.copy(alt = transform(latches.alt))
      }
      state.copy(latches = next)
    }
  }

  /**
   * The grid this call last resized to, so a repeated report from an unrelated recomposition
   * writes the wire once, not every frame.
   */
  private var lastGridSize: IntSize? = null

  /**
   * The container's own measured size, turned into both halves `TerminalGateway.resize`'s own
   * KDoc names: [TerminalEmulator.resize] (what this screen draws) and the wire `set_size` (what
   * the pty is told) - `docs/PLAN.md`'s P4i-3a. A no-op once [gridSize] repeats, which is what
   * keeps a redraw that changes nothing about the layout from writing the wire again.
   *
   * ```
   * viewModel.resize(terminalGridSize(containerPixels, cell), cell.width, cell.height)
   * ```
   */
  fun resize(gridSize: IntSize, cellWidthPixels: Int, cellHeightPixels: Int) {
    if (gridSize == lastGridSize) return
    lastGridSize = gridSize
    emulator.resize(gridSize.width, gridSize.height, cellWidthPixels, cellHeightPixels)
    // `TerminalCanvas`'s draw loop is bound to `screen.rows`/`columns` at its last composition -
    // only a fresh `key(state.redraw)` rebuild reads what the mutation above just changed, the
    // same reason every `Output` event already bumps this.
    bumpRedraw()
    viewModelScope.launch { terminalGateway.resize(session, rows = gridSize.height, cols = gridSize.width) }
  }

  private fun sendToShell(text: String) {
    viewModelScope.launch { terminalGateway.send(session, text) }
  }

  private fun bumpRedraw() = _uiState.update { it.copy(redraw = it.redraw + 1) }

  companion object {
    /** Overwritten by the first real layout - see sub-step 2/3; a shell has to start at *some* size. */
    const val DEFAULT_COLUMNS = 80
    const val DEFAULT_ROWS = 24
  }
}
