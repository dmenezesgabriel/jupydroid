package com.jupydroid.app.ui.environments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.isProjectFolderName
import com.jupydroid.app.domain.port.SpaceEnvironmentCreation
import com.jupydroid.app.domain.port.SpaceEnvironments
import com.jupydroid.app.domain.port.SpaceShellEvent
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Environments panel draws.
 *
 * @property environments the Space's environments as [SpaceEnvironments.environments] last
 *   answered, or null while unread or after a refused read.
 * @property projects the workspace's top-level project folders - the names
 *   `SpaceEnvironmentCreation.create` takes - or null while unread.
 * @property create the one create in flight or its last verdict; null while none has run or the
 *   last one exited clean.
 * @property refusal the last act's or read's refusal, verbatim, or null.
 */
data class EnvironmentsPanelState(
  val environments: List<PythonEnvironment>? = null,
  val projects: List<String>? = null,
  val create: EnvironmentCreateState? = null,
  val refusal: String? = null,
)

/** Where the panel's one create stands. */
sealed interface EnvironmentCreateState {
  /** The script is running for [project]; [lines] is its output so far, oldest first. */
  data class Running(val project: String, val lines: List<String>) : EnvironmentCreateState

  /** The script exited [exitCode]; [excerpt] is the tail of what it printed. */
  data class Failed(val project: String, val exitCode: Int, val excerpt: String) : EnvironmentCreateState

  /** The run never produced an exit - the port refused or the flow died; [message] says why. */
  data class Refused(val project: String, val message: String) : EnvironmentCreateState
}

/**
 * The Environments panel's asks of [SpaceEnvironments], [SpaceEnvironmentCreation] and the
 * workspace listing: [refresh] re-reads both halves - the panel polls it so an environment made
 * in a terminal appears without a relaunch - [register] writes a spec for an environment already
 * made, and [create] runs `jupydroid-env create` for a project folder, one at a time, its output
 * streamed into [EnvironmentsPanelState.create].
 *
 * The Space is fixed at construction: the panel is composed inside one Space's Explorer entry,
 * so [spaceId] never changes under it.
 *
 * ```
 * EnvironmentsPanelViewModel(space.id, appContainer.spaceEnvironments,
 *   appContainer.spaceEnvironmentCreation, spacePorts.workspaceRepository)
 * ```
 */
class EnvironmentsPanelViewModel(
  private val spaceId: String,
  private val spaceEnvironments: SpaceEnvironments,
  private val spaceEnvironmentCreation: SpaceEnvironmentCreation,
  private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(EnvironmentsPanelState())
  val uiState: StateFlow<EnvironmentsPanelState> = _uiState.asStateFlow()

  /** Re-reads the environments and the project folders; each lands as it answers. */
  fun refresh() {
    viewModelScope.launch { readEnvironments() }
    viewModelScope.launch { readProjects() }
  }

  /**
   * Makes the environment at [path] a kernel of the Space, then re-reads the environments so the
   * row shows the spec's name. A refusal is the port's own words and reads nothing.
   */
  fun register(path: String) {
    _uiState.update { it.copy(refusal = null) }
    viewModelScope.launch {
      reportingFailure {
        spaceEnvironments.register(spaceId, path)
        readEnvironments()
      }
    }
  }

  /**
   * Runs the create script for [project], streaming its lines into the state. A second call
   * while one runs is refused rather than queued - the script makes `<project>/.venv`, and two
   * runs for one project would race the same directory.
   */
  fun create(project: String) {
    if (_uiState.value.create is EnvironmentCreateState.Running) return
    _uiState.update { it.copy(refusal = null, create = EnvironmentCreateState.Running(project, emptyList())) }
    viewModelScope.launch { runCreate(project) }
  }

  private suspend fun readEnvironments() = reportingFailure {
    _uiState.update { it.copy(environments = spaceEnvironments.environments(spaceId)) }
  }

  private suspend fun readProjects() = reportingFailure {
    val projects = workspaceRepository.list("").projectFolderNames()
    _uiState.update { it.copy(projects = projects) }
  }

  /** The workspace's project folders: top-level directories that are neither hidden nor env containers. */
  private fun List<WorkspaceEntry>.projectFolderNames(): List<String> =
    filter { it.kind == WorkspaceEntryKind.DIRECTORY && it.name.isProjectFolderName() }.map { it.name }

  /** One create's whole life: stream the script, settle by its exit status. */
  // A port refusal's type is the adapter's to choose; the state reports whatever it threw.
  @Suppress("TooGenericExceptionCaught")
  private suspend fun runCreate(project: String) {
    try {
      settleCreate(project)
    } catch (cancelled: CancellationException) {
      _uiState.update { it.copy(create = null) }
      throw cancelled
    } catch (failure: Exception) {
      val message = failure.message ?: failure.toString()
      _uiState.update { it.copy(create = EnvironmentCreateState.Refused(project, message)) }
    }
  }

  /** The happy path of [runCreate]: exit 0 clears and re-reads, anything else keeps the output's tail. */
  private suspend fun settleCreate(project: String) {
    val (exitCode, lines) = collectCreate(project)
    if (exitCode != EXIT_OK) {
      // The failure's tail, the same 2000 characters a failed install's log keeps -
      // `INSTALL_EXCERPT_CHARS` in `app/src/main/java/com/jupydroid/app/ui/spaces/SpaceHarnessesViewModel.kt`.
      val excerpt = lines.joinToString("\n").takeLast(CREATE_EXCERPT_CHARS)
      _uiState.update { it.copy(create = EnvironmentCreateState.Failed(project, exitCode, excerpt)) }
      return
    }
    _uiState.update { it.copy(create = null) }
    readEnvironments()
  }

  /** Streams the run's output into the state line by line; returns its exit code and everything it said. */
  private suspend fun collectCreate(project: String): Pair<Int, List<String>> {
    val lines = mutableListOf<String>()
    var exitCode: Int? = null
    spaceEnvironmentCreation.create(spaceId, project).collect { event ->
      when (event) {
        is SpaceShellEvent.Output -> {
          lines += event.line
          _uiState.update { it.copy(create = EnvironmentCreateState.Running(project, lines.toList())) }
        }
        is SpaceShellEvent.Finished -> exitCode = event.exitCode
      }
    }
    val finished =
      exitCode ?: error("SpaceEnvironmentCreation.create ended without Finished; expected Output* then one Finished")
    return finished to lines
  }

  /** Any refusal is put on screen as the port worded it; a cancellation is not a refusal. */
  // A port refusal's type is the adapter's to choose; the state reports whatever it threw.
  @Suppress("TooGenericExceptionCaught")
  private suspend fun reportingFailure(ask: suspend () -> Unit) {
    try {
      ask()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (failure: Exception) {
      _uiState.update { it.copy(refusal = failure.message ?: failure.toString()) }
    }
  }

  private companion object {
    const val EXIT_OK = 0
    const val CREATE_EXCERPT_CHARS = 2000
  }
}
