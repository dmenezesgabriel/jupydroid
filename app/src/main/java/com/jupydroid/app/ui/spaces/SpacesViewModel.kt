package com.jupydroid.app.ui.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.model.defaultKernelFallbackNote
import com.jupydroid.app.domain.model.spaceWithId
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.SpaceEnvironments
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.domain.port.SpaceServers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Spaces panel draws.
 *
 * @property spaces every Space on the device, in the port's own order - base first.
 * @property busy an act is in flight; the panel shows progress and holds its act controls.
 * @property failure the last act's or listing's refusal, verbatim, or null.
 * @property progress the in-flight create's copy fraction - null while an act is indeterminate.
 * @property running the ids of the Spaces whose server is alive, as [SpaceServers.running] has them.
 * @property environments the environments of the Space whose Settings is open, or null while they are
 *   read or when the read was refused.
 * @property kernelChoices the kernelspecs on the disk of the Space whose Settings is open, which its
 *   default kernel is picked among, or null while they are read or when the read was refused.
 * @property defaultKernelNotes by Space id, why its server starts another kernel than its stored
 *   default - `defaultKernelFallbackNote` - for each Space where it does.
 */
data class SpacesPanelState(
  val spaces: List<SpaceConfig> = emptyList(),
  val busy: Boolean = false,
  val failure: String? = null,
  val progress: Float? = null,
  val running: Set<String> = emptySet(),
  val environments: List<PythonEnvironment>? = null,
  val kernelChoices: List<KernelSpec>? = null,
  val defaultKernelNotes: Map<String, String> = emptyMap(),
)

/**
 * The Spaces panel's asks of [SpaceRepository] - list, create, rename, configure and delete - and of
 * [SpaceServers]: run, stop, and which are running.
 *
 * **One act at a time.** [busy] is raised before the act launches, so a second tap in the same
 * frame is refused rather than racing the first - two creates would otherwise both read the same
 * scan and mint the same id. vscode runs every profile action under one progress indicator for
 * the same reason (`vscode@3f3380c src/vs/workbench/contrib/userDataProfile/browser/userDataProfilesEditor.ts`,
 * `IEditorProgressService.showWhile`).
 *
 * **Every act re-lists, failed or not**: the scan is the source of truth (`DiskSpaceRepository`),
 * and a half-finished act on disk is only visible by reading it again.
 *
 * **Running follows the port, not the panel's calls**, so a cell's lazy start of base and an idle
 * server's self-stop reach the rows without a tap here.
 *
 * ```
 * SpacesViewModel(
 *   appContainer.spaceRepository, appContainer.spaceServers, appContainer.spaceEnvironments,
 *   appContainer.stoppedSpaceKernelSpecs,
 * ).run("space-1")
 * ```
 */
class SpacesViewModel(
  private val spaceRepository: SpaceRepository,
  private val spaceServers: SpaceServers,
  private val spaceEnvironments: SpaceEnvironments,
  /** Any Space's kernelspecs as its disk holds them - Settings is only open on a stopped Space. */
  private val stoppedKernelSpecs: KernelSpecs,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SpacesPanelState())
  val uiState: StateFlow<SpacesPanelState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch { relist() }
    viewModelScope.launch {
      spaceServers.running.collect { ids -> _uiState.update { it.copy(running = ids) } }
    }
  }

  /** Makes a Space called [name]; the port mints its id and reports the image copy's fraction. */
  fun create(name: String) = act {
    spaceRepository.create(name) { fraction ->
      _uiState.update { it.copy(progress = fraction) }
    }
  }

  /** Renames the Space [id] to [name]; its directory keeps the id. */
  fun rename(id: String, name: String) = act { spaceRepository.rename(id, name) }

  /**
   * Saves [settings] as the Space [id]'s, for its next start. Refused while the Space runs: nothing
   * is applied to a running process, and a save it would not honour until some later restart is a
   * change the row would misreport. The port refuses an invalid value in its own words.
   */
  fun configure(id: String, settings: SpaceSettings) = act {
    check(id !in spaceServers.running.value) {
      "Space '$id' is running; expected it stopped before its settings change"
    }
    spaceRepository.configure(id, settings)
  }

  /**
   * Reads what the Space [id]'s Settings offers: its environments and the kernels its default is
   * picked among. Not an act: it reads a disk and changes nothing, so it never holds the panel. The
   * previous Space's lists are dropped first, so a dialog never shows another Space's while this
   * one's are read, and so is an earlier refusal, since Settings shows the refusals of its own acts.
   */
  fun openSpaceSettings(id: String) {
    _uiState.update { it.copy(environments = null, kernelChoices = null, failure = null) }
    readSpaceSettings(id)
  }

  /**
   * Makes the environment at [path] a kernel of the Space [id], then re-reads what Settings read when
   * it opened, so the row and the default's choices show what the disk now has. The lists already
   * shown stay until the new ones arrive: dropping them collapsed the dialog and scrolled the row
   * just registered out of view. A refused registration reads nothing.
   */
  fun registerEnvironment(id: String, path: String) = act {
    spaceEnvironments.register(id, path)
    readSpaceSettings(id)
  }

  /** Reads the Space [id]'s environments and kernel choices into the state, each as it arrives. */
  private fun readSpaceSettings(id: String) {
    viewModelScope.launch {
      _uiState.reportingFailure {
        val found = spaceEnvironments.environments(id)
        _uiState.update { it.copy(environments = found) }
      }
    }
    viewModelScope.launch {
      _uiState.reportingFailure {
        val listed = stoppedKernelSpecs.specs(spaceWithId(id)).specs
        _uiState.update { it.copy(kernelChoices = listed) }
      }
    }
  }

  /** Starts the Space [id]'s server; the act lasts until it serves. */
  fun run(id: String) = act { spaceServers.run(id) }

  /** Stops the Space [id]'s server, keeping its files. */
  fun stop(id: String) = act { spaceServers.stop(id) }

  /**
   * Stops the Space [id], then removes it and everything in it. The port refuses base. A server
   * that outlives its stop - `JupyterServerLauncher` keeps one rather than signal it - keeps its
   * files too, since deleting them would pull the tree out from under a live process.
   */
  fun delete(id: String) = act {
    spaceServers.stop(id)
    check(id !in spaceServers.running.value) {
      "Space '$id' is still running after Stop; expected it stopped before delete"
    }
    spaceRepository.delete(id)
  }

  private fun act(change: suspend () -> Unit) {
    if (_uiState.value.busy) return
    _uiState.update { it.copy(busy = true, failure = null) }
    viewModelScope.launch {
      _uiState.reportingFailure { change() }
      relist()
      _uiState.update { it.copy(busy = false, progress = null) }
    }
  }

  /** What the disk holds now; a refused listing keeps the rows it would have replaced. */
  private suspend fun relist() {
    _uiState.reportingFailure {
      val listed = spaceRepository.spaces()
      _uiState.update { it.copy(spaces = listed) }
      val notes = defaultKernelNotesOf(listed, stoppedKernelSpecs, spaceEnvironments)
      _uiState.update { it.copy(defaultKernelNotes = notes) }
    }
  }
}

/**
 * Why each of [spaces]' servers starts another kernel than its stored default, by id, read off the
 * disk its launch reads. A stored `python3` reads nothing: the catalogue always lists it, so it never
 * falls back.
 */
private suspend fun defaultKernelNotesOf(
  spaces: List<SpaceConfig>,
  kernelSpecs: KernelSpecs,
  environments: SpaceEnvironments,
): Map<String, String> = spaces.filter { it.settings.defaultKernel != DEFAULT_KERNEL_ID }.mapNotNull { space ->
  val stored = space.settings.defaultKernel
  val effective = kernelSpecs.specs(spaceWithId(space.id)).default
  if (effective == stored) return@mapNotNull null
  defaultKernelFallbackNote(stored, effective, environments.environments(space.id))?.let { space.id to it }
}.toMap()

/**
 * Any refusal is put on screen as the port worded it - its messages name the path or id at fault.
 * A cancellation is not a refusal and is never swallowed.
 */
private suspend fun MutableStateFlow<SpacesPanelState>.reportingFailure(ask: suspend () -> Unit) {
  runCatching { ask() }.onFailure { refused ->
    if (refused is CancellationException) throw refused
    update { it.copy(failure = refused.message) }
  }
}
