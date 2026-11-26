package com.jupydroid.app.ui.environments

import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One streamed act's slot in [EnvironmentsPanelState] - `create` or `interpreterRegister` - so
 * [ScriptActRunner] writes either act without knowing which it runs.
 */
internal data class ActSlot(
  val get: (EnvironmentsPanelState) -> ScriptActState?,
  val put: (EnvironmentsPanelState, ScriptActState?) -> EnvironmentsPanelState,
)

/**
 * One streamed script act's whole life against the panel's state: start it only while its slot
 * is idle, stream the script's lines into a [ScriptActState.Running], and settle by the run's
 * exit status - exit 0 clears the slot and re-reads, anything else keeps the output's tail, and
 * a run that never produces an exit is refused rather than failed. Shared by the create and the
 * interpreter register, the same streamed shape either way.
 */
internal class ScriptActRunner(
  private val state: MutableStateFlow<EnvironmentsPanelState>,
  private val scope: CoroutineScope,
) {
  /**
   * Starts the act for [subject] unless [slot] already runs one - a second tap is refused rather
   * than queued, so two runs cannot race the script's own catalogue or directory checks.
   */
  fun start(slot: ActSlot, subject: String, events: Flow<SpaceShellEvent>, readAgain: suspend () -> Unit) {
    if (slot.get(state.value) is ScriptActState.Running) return
    state.update { slot.put(it.copy(refusal = null), ScriptActState.Running(subject, emptyList())) }
    scope.launch { runAct(slot, subject, events, readAgain) }
  }

  // A port refusal's type is the adapter's to choose; the state reports whatever it threw, and a
  // cancellation is not a refusal.
  @Suppress("TooGenericExceptionCaught")
  private suspend fun runAct(
    slot: ActSlot,
    subject: String,
    events: Flow<SpaceShellEvent>,
    readAgain: suspend () -> Unit,
  ) {
    try {
      settleAct(slot, subject, events, readAgain)
    } catch (cancelled: CancellationException) {
      state.update { slot.put(it, null) }
      throw cancelled
    } catch (failure: Exception) {
      state.update { slot.put(it, ScriptActState.Refused(subject, failure.message ?: failure.toString())) }
    }
  }

  /** The happy path of [runAct]: exit 0 clears and re-reads, anything else keeps the output's tail. */
  private suspend fun settleAct(
    slot: ActSlot,
    subject: String,
    events: Flow<SpaceShellEvent>,
    readAgain: suspend () -> Unit,
  ) {
    val (exitCode, lines) = collectAct(slot, subject, events)
    if (exitCode != EXIT_OK) {
      // The failure's tail, the same 2000 characters a failed install's log keeps -
      // `INSTALL_EXCERPT_CHARS` in `app/src/main/java/com/jupydroid/app/ui/spaces/SpaceHarnessesViewModel.kt`.
      val excerpt = lines.joinToString("\n").takeLast(ACT_EXCERPT_CHARS)
      state.update { slot.put(it, ScriptActState.Failed(subject, exitCode, excerpt)) }
      return
    }
    state.update { slot.put(it, null) }
    readAgain()
  }

  /** Streams the run's output into the slot line by line; returns its exit code and everything it said. */
  private suspend fun collectAct(
    slot: ActSlot,
    subject: String,
    events: Flow<SpaceShellEvent>,
  ): Pair<Int, List<String>> {
    val lines = mutableListOf<String>()
    var exitCode: Int? = null
    events.collect { event ->
      when (event) {
        is SpaceShellEvent.Output -> {
          lines += event.line
          state.update { slot.put(it, ScriptActState.Running(subject, lines.toList())) }
        }
        is SpaceShellEvent.Finished -> exitCode = event.exitCode
      }
    }
    val finished =
      exitCode ?: error("a script act ended without Finished; expected Output* then one Finished")
    return finished to lines
  }

  private companion object {
    const val EXIT_OK = 0

    /**
     * How many characters of a failed act's tail the state keeps - a failed install's own bound
     * (`SpaceHarnessesViewModel`'s `INSTALL_EXCERPT_CHARS`).
     */
    const val ACT_EXCERPT_CHARS = 2000
  }
}
