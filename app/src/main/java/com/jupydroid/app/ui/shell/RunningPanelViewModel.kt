package com.jupydroid.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The two lists the panel draws from the server - the `Kernels` and `Terminals` sections' own
 * `manager.running()` answers (`running/src/index.tsx`). Open Tabs is not here: it is the shell's
 * own back stack, which only the composition holds.
 */
data class RunningPanelState(
  /** `GET api/sessions` - every session the server is holding, newest rows reconciled out. */
  val sessions: List<KernelSessionModel> = emptyList(),
  /** `GET api/terminals` - every shell the server still holds. */
  val terminals: List<TerminalSession> = emptyList(),
)

/**
 * The Sessions and Tabs panel's asks of the server - `refreshRunning` and the `shutdown` verbs
 * behind the section toolbars (`packages/running-extension/src/index.ts`).
 *
 * **[kernelSessions.ended] is collected from `init`** rather than waited on at the next poll,
 * which is `manager.runningChanged`'s own reach: a notebook that shuts its kernel down from its
 * own menu leaves the list at once, not ten seconds later, and a row the panel itself shut down
 * goes by the same event as a row the server culled. The terminal port has no such signal, so its
 * rows relist after the call instead - which is where [relist] lives.
 */
class RunningPanelViewModel(private val kernelSessions: KernelSessions, private val terminalGateway: TerminalGateway) :
  ViewModel() {
  private val _uiState = MutableStateFlow(RunningPanelState())
  val uiState: StateFlow<RunningPanelState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch { kernelSessions.ended.collect { refresh() } }
  }

  /**
   * `managers.items().forEach { it.refreshRunning() }` - the toolbar refresh's own act, and the
   * poll's. A failed ask keeps the last good list the way the reference's section keeps its rows
   * when `refreshRunning` rejects - the launch's failure never empties [_uiState].
   */
  fun refresh() {
    viewModelScope.launch { relist() }
  }

  /**
   * `running:kernel-shut-down` - `DELETE api/sessions/<id>` on the row's own control. A refused
   * delete dies quietly: `ended`, not the call's return, is what removes a row, and a delete the
   * server never saw emits no end.
   */
  fun shutdownSession(session: KernelSessionModel) {
    viewModelScope.launch { ignoringFailure { kernelSessions.shutdown(session.serverSessionId) } }
  }

  /**
   * The section toolbar's `shutdown-all` - one DELETE per session. The reference shuts *kernels*
   * (`kernels.shutdownAll()` over `GET api/kernels`); this port's rows are sessions, and deleting
   * a session takes its kernel with it (`SessionManager.delete_session`), so the count the caller
   * confirmed against is [RunningPanelState.sessions]' own. Each shutdown is its own ask - one
   * refused DELETE does not skip the rows behind it.
   */
  fun shutdownAllSessions() {
    viewModelScope.launch {
      _uiState.value.sessions.forEach { ignoringFailure { kernelSessions.shutdown(it.serverSessionId) } }
    }
  }

  /** `terminal:close` - `DELETE api/terminals/<name>` on the row's own control. */
  fun closeTerminal(session: TerminalSession) {
    viewModelScope.launch {
      ignoringFailure { terminalGateway.close(session) }
      relist()
    }
  }

  /** The section toolbar's `shutdown-all` - `manager.shutdownAll()`, one DELETE per terminal. */
  fun shutdownAllTerminals() {
    viewModelScope.launch {
      _uiState.value.terminals.forEach { ignoringFailure { terminalGateway.close(it) } }
      relist()
    }
  }

  /** Both lists again - what the server answers now. */
  private suspend fun relist() {
    _uiState.value = RunningPanelState(
      sessions = keepingLastGood(_uiState.value.sessions) { kernelSessions.running() },
      terminals = keepingLastGood(_uiState.value.terminals) { terminalGateway.running() },
    )
  }

  /**
   * A refused ask keeps the list it would have replaced - the reference's per-manager
   * `refreshRunning` leaves a failed section's rows alone, and one dead endpoint is no reason to
   * empty the other's list too. Cancellation is never swallowed.
   */
  private suspend fun <T> keepingLastGood(current: List<T>, ask: suspend () -> List<T>): List<T> = try {
    ask()
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: Exception) {
    current
  }

  /** A verb the panel fired: its refusal is logged by the server's silence, not a crash. */
  private suspend fun ignoringFailure(ask: suspend () -> Unit) {
    try {
      ask()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
    }
  }
}
