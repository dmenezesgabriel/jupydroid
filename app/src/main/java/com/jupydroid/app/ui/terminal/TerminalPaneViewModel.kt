package com.jupydroid.app.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.terminal.TerminalEmulator
import com.jupydroid.app.terminal.TerminalNotices
import com.jupydroid.app.terminal.TerminalReplies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the terminal screen has to show, beyond the mutable grid [TerminalPaneViewModel.emulator]
 * already carries.
 *
 * @property redraw bumped on every [TerminalEvent.Output] and colour change, so the screen has a
 *   `State` to key its recomposition on - `emulator.screen.grid` is mutated in place and Compose
 *   cannot observe that on its own.
 */
data class TerminalPaneUiState(
  val ready: Boolean = false,
  val redraw: Int = 0,
  val title: String? = null,
  val endedReason: String? = null,
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
  val emulator = TerminalEmulator(columns = columns, rows = rows, notices = notices, replies = replies)

  init {
    // Collected for exactly as long as this ViewModel lives - cancelled by `viewModelScope` on
    // `onCleared`, which is what closes the socket without this class asking it to
    // (`TerminalGateway.attach`'s own KDoc: "cancelling the collection closes the socket").
    viewModelScope.launch {
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
  }

  /** A keystroke, typed verbatim - the other half of [replies], and the only caller `type` has. */
  fun type(text: String) = sendToShell(text)

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
