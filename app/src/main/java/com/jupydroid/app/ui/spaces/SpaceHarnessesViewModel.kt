package com.jupydroid.app.ui.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.CHAT_HARNESS_CATALOG
import com.jupydroid.app.domain.model.ChatHarness
import com.jupydroid.app.domain.model.HarnessPresence
import com.jupydroid.app.domain.model.presenceFromProbe
import com.jupydroid.app.domain.model.probeScript
import com.jupydroid.app.domain.port.HarnessScripts
import com.jupydroid.app.domain.port.SpaceShell
import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The probe bound: the script bounds `--version` itself at 15 s, so a minute covers a Space that
 * cannot answer at all and still fails fast.
 */
internal val HARNESS_PROBE_TIMEOUT: Duration = 1.minutes

/**
 * The install bound: the cold installs `ChatHarnessInstallProbeDeviceTest`
 * (`app/src/androidTest/java/com/jupydroid/app/data/space/ChatHarnessInstallProbeDeviceTest.kt`)
 * measured on the tablet took ~49 s at worst, so the five minutes
 * `app/src/main/java/com/jupydroid/app/data/space/SpaceStartupScript.kt` gives a startup script is
 * kept - a measured install fits it several times over.
 */
internal val HARNESS_INSTALL_TIMEOUT: Duration = 5.minutes

/** What the harnesses section draws: one row per catalog harness. */
data class SpaceHarnessesState(val harnesses: List<HarnessRowState> = CHAT_HARNESS_CATALOG.map { HarnessRowState(it) })

/** One harness's row: what the probe last answered, and where its install stands. */
data class HarnessRowState(
  val harness: ChatHarness,
  val probe: HarnessProbeState = HarnessProbeState.Probing,
  val install: HarnessInstallState = HarnessInstallState.Idle,
)

/** What a harness probe said about the Space - or why it could not say. */
sealed interface HarnessProbeState {
  /** The probe is still running, or none has been asked yet. */
  data object Probing : HarnessProbeState

  /** The probe answered; [presence] is the persona's own verdict on it. */
  data class Answered(val presence: HarnessPresence) : HarnessProbeState

  /** The run threw, or ended without the probe's markers - [message] says which, in the port's words. */
  data class Failed(val message: String) : HarnessProbeState
}

/** Where one harness's install stands. */
sealed interface HarnessInstallState {
  /** Nothing is installing; the row offers Install (or reports the probe's answer). */
  data object Idle : HarnessInstallState

  /** The script is running; [lines] is its output so far, oldest first. */
  data class Running(val lines: List<String>) : HarnessInstallState

  /** The script exited [exitCode]; [excerpt] is the tail of what it printed. */
  data class Failed(val exitCode: Int, val excerpt: String) : HarnessInstallState

  /** The run never produced an exit - the port refused or the flow died; [message] says why. */
  data class Refused(val message: String) : HarnessInstallState
}

/**
 * The harnesses section's asks of [SpaceShell] and [HarnessScripts]: probe every catalog harness
 * when a stopped Space's Settings opens, and run one harness's install at a time with its output
 * streamed into the row.
 *
 * **"Installed" is a live probe, never stored state** - the personas' own `which` + `--version`
 * question re-asked on every [open] and again after each clean install, so a rootfs upgrade that
 * deletes the binary re-offers the install by itself.
 *
 * **An install is per harness and cancellable**: [dismiss] ends every run in flight, and the
 * scripts are idempotent, so a re-run resumes where the last stopped.
 *
 * ```
 * SpaceHarnessesViewModel(appContainer.spaceShell, appContainer.harnessScripts).open(space.id)
 * ```
 */
class SpaceHarnessesViewModel(private val spaceShell: SpaceShell, private val harnessScripts: HarnessScripts) :
  ViewModel() {
  private val _uiState = MutableStateFlow(SpaceHarnessesState())
  val uiState: StateFlow<SpaceHarnessesState> = _uiState.asStateFlow()

  private var spaceId: String? = null
  private val probeJobs = mutableMapOf<String, Job>()
  private val installJobs = mutableMapOf<String, Job>()

  /**
   * Probes every catalog harness in [spaceId]'s shell - the section's "on open". Any probe or
   * install still in flight is cancelled first: its answer would describe another Space's disk.
   */
  fun open(spaceId: String) {
    this.spaceId = spaceId
    cancelJobs()
    _uiState.value = SpaceHarnessesState()
    CHAT_HARNESS_CATALOG.forEach { harness ->
      probeJobs[harness.id] = viewModelScope.launch { probeHarness(spaceId, harness) }
    }
  }

  /**
   * Runs [harness]'s install script in the opened Space, streaming its output into the row. A
   * second call while that harness's install runs is refused; a probe still in flight for it is
   * cancelled, since its answer would predate the install. Before [open] there is no Space, so a
   * stray call does nothing.
   */
  fun install(harness: ChatHarness) {
    val id = spaceId ?: return
    if (installJobs[harness.id]?.isActive == true) return
    probeJobs[harness.id]?.cancel()
    installJobs[harness.id] = viewModelScope.launch { runInstall(id, harness) }
  }

  /**
   * Cancels every probe and install in flight - the dialog's dismiss. A cancelled install reads
   * idle again; the port's contract ends the Space-side run with the collection.
   */
  fun dismiss() {
    cancelJobs()
    _uiState.update { state ->
      state.copy(
        harnesses = state.harnesses.map { row ->
          if (row.install is HarnessInstallState.Running) row.copy(install = HarnessInstallState.Idle) else row
        },
      )
    }
  }

  /** Asks the persona's own question once: `command -v` plus `--version`, parsed by [presenceFromProbe]. */
  // A port refusal's type is the adapter's to choose; the row reports whatever it threw.
  @Suppress("TooGenericExceptionCaught")
  private suspend fun probeHarness(spaceId: String, harness: ChatHarness) {
    updateRow(harness.id) { it.copy(probe = HarnessProbeState.Probing) }
    try {
      val presence = harness.presenceFromProbe(probeLines(spaceId, harness))
      val answer = presence?.let(HarnessProbeState::Answered) ?: HarnessProbeState.Failed(PROBE_NO_ANSWER)
      updateRow(harness.id) { it.copy(probe = answer) }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (failure: Exception) {
      updateRow(harness.id) { it.copy(probe = HarnessProbeState.Failed(failure.message ?: failure.toString())) }
    }
  }

  /** The probe script's output lines; the terminal event is not a line and is dropped here. */
  private suspend fun probeLines(spaceId: String, harness: ChatHarness): List<String> =
    spaceShell.run(spaceId, harness.probeScript(), HARNESS_PROBE_TIMEOUT)
      .filterIsInstance<SpaceShellEvent.Output>()
      .map { it.line }
      .toList()

  /** One install's whole life: fetch the script, stream it, settle by its exit status. */
  // A port refusal's type is the adapter's to choose; the row reports whatever it threw.
  @Suppress("TooGenericExceptionCaught")
  private suspend fun runInstall(spaceId: String, harness: ChatHarness) {
    try {
      installAndSettle(spaceId, harness)
    } catch (cancelled: CancellationException) {
      updateRow(harness.id) { it.copy(install = HarnessInstallState.Idle) }
      throw cancelled
    } catch (failure: Exception) {
      updateRow(harness.id) { it.copy(install = HarnessInstallState.Refused(failure.message ?: failure.toString())) }
    }
  }

  /** The happy path of [runInstall]: exit 0 re-probes, anything else keeps the output's tail. */
  private suspend fun installAndSettle(spaceId: String, harness: ChatHarness) {
    val script = harnessScripts.installScript(harness)
    updateRow(harness.id) { it.copy(install = HarnessInstallState.Running(emptyList())) }
    val (exitCode, lines) = collectInstall(spaceId, harness.id, script)
    if (exitCode != EXIT_OK) {
      val excerpt = excerptOf(lines)
      updateRow(harness.id) { it.copy(install = HarnessInstallState.Failed(exitCode, excerpt)) }
      return
    }
    updateRow(harness.id) { it.copy(install = HarnessInstallState.Idle) }
    probeHarness(spaceId, harness)
  }

  /** Streams the run's output into the row line by line; returns its exit code and everything it said. */
  private suspend fun collectInstall(spaceId: String, harnessId: String, script: String): Pair<Int, List<String>> {
    val lines = mutableListOf<String>()
    var exitCode: Int? = null
    spaceShell.run(spaceId, script, HARNESS_INSTALL_TIMEOUT).collect { event ->
      when (event) {
        is SpaceShellEvent.Output -> {
          lines += event.line
          updateRow(harnessId) { it.copy(install = HarnessInstallState.Running(lines.toList())) }
        }
        is SpaceShellEvent.Finished -> exitCode = event.exitCode
      }
    }
    val finished = exitCode ?: error("SpaceShell.run ended without Finished; expected Output* then one Finished")
    return finished to lines
  }

  /**
   * The failure's tail, the same 2000 characters a failed start's log keeps - `LOG_EXCERPT` in
   * `app/src/main/java/com/jupydroid/app/data/space/SpaceStartupScript.kt`.
   */
  private fun excerptOf(lines: List<String>): String = lines.joinToString("\n").takeLast(INSTALL_EXCERPT_CHARS)

  private fun updateRow(harnessId: String, change: (HarnessRowState) -> HarnessRowState) {
    _uiState.update { state ->
      state.copy(
        harnesses = state.harnesses.map { row ->
          if (row.harness.id == harnessId) change(row) else row
        },
      )
    }
  }

  private fun cancelJobs() {
    (probeJobs.values + installJobs.values).toList().forEach { it.cancel() }
  }

  private companion object {
    const val EXIT_OK = 0
    const val INSTALL_EXCERPT_CHARS = 2000
    const val PROBE_NO_ANSWER = "the probe did not answer; expected harness-probe: markers then version-rc="
  }
}
