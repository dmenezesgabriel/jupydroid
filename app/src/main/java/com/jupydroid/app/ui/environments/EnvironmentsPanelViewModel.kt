package com.jupydroid.app.ui.environments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.SpaceInterpreter
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.isProjectFolderName
import com.jupydroid.app.domain.port.SpaceEnvironmentCreation
import com.jupydroid.app.domain.port.SpaceEnvironments
import com.jupydroid.app.domain.port.SpaceKernelRegistration
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
 * @property interpreters the Space's interpreters as [SpaceKernelRegistration.interpreters] last
 *   answered, or null while unread.
 * @property projects the workspace's top-level project folders - the names
 *   `SpaceEnvironmentCreation.create` takes - or null while unread.
 * @property create the one create in flight or its last verdict; null while none has run or the
 *   last one exited clean.
 * @property interpreterRegister the one interpreter register in flight or its last verdict; null
 *   while none has run or the last one exited clean.
 * @property refusal the last act's or read's refusal, verbatim, or null.
 */
data class EnvironmentsPanelState(
  val environments: List<PythonEnvironment>? = null,
  val interpreters: List<SpaceInterpreter>? = null,
  val projects: List<String>? = null,
  val create: ScriptActState? = null,
  val interpreterRegister: ScriptActState? = null,
  val refusal: String? = null,
)

/**
 * Where the panel's one streamed script act stands - a `jupydroid-env` run for its [subject],
 * a project folder or an interpreter's path.
 */
sealed interface ScriptActState {
  /** What the run is for - a project folder's name or an interpreter's path. */
  val subject: String

  /** The script is running for [subject]; [lines] is its output so far, oldest first. */
  data class Running(override val subject: String, val lines: List<String>) : ScriptActState

  /** The script exited [exitCode]; [excerpt] is the tail of what it printed. */
  data class Failed(override val subject: String, val exitCode: Int, val excerpt: String) : ScriptActState

  /** The run never produced an exit - the port refused or the flow died; [message] says why. */
  data class Refused(override val subject: String, val message: String) : ScriptActState
}

/**
 * The Environments panel's asks of [SpaceEnvironments], [SpaceEnvironmentCreation],
 * [SpaceKernelRegistration] and the workspace listing: [refresh] re-reads all three - the panel
 * polls it so an environment or a kernel made in a terminal appears without a relaunch -
 * [register] writes a spec for an environment already made, [create] runs `jupydroid-env create`
 * for a project folder, and [registerInterpreter] runs `jupydroid-env register` for one of the
 * Space's own interpreters, each streamed act run by the shared [ScriptActRunner].
 *
 * The Space is fixed at construction: the panel is composed inside one Space's Explorer entry,
 * so [spaceId] never changes under it.
 *
 * ```
 * EnvironmentsPanelViewModel(space.id, appContainer.spaceEnvironments,
 *   appContainer.spaceEnvironmentCreation, appContainer.spaceKernelRegistration,
 *   spacePorts.workspaceRepository)
 * ```
 */
class EnvironmentsPanelViewModel(
  private val spaceId: String,
  private val spaceEnvironments: SpaceEnvironments,
  private val spaceEnvironmentCreation: SpaceEnvironmentCreation,
  private val spaceKernelRegistration: SpaceKernelRegistration,
  private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(EnvironmentsPanelState())
  val uiState: StateFlow<EnvironmentsPanelState> = _uiState.asStateFlow()

  private val acts = ScriptActRunner(_uiState, viewModelScope)

  /** Re-reads the environments, the interpreters and the project folders; each lands as it answers. */
  fun refresh() {
    viewModelScope.launch { readEnvironments() }
    viewModelScope.launch { readInterpreters() }
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
  fun create(project: String) = acts.start(
    CREATE_SLOT,
    project,
    spaceEnvironmentCreation.create(spaceId, project),
    ::readEnvironments,
  )

  /**
   * Runs the register script for [interpreter] - a path a process in the Space execs - streaming
   * its lines into the state. One at a time, as a create is: two registers could name the same
   * kernel, and the script's own catalogue check would race.
   */
  fun registerInterpreter(interpreter: String) = acts.start(
    REGISTER_SLOT,
    interpreter,
    spaceKernelRegistration.register(spaceId, interpreter),
    ::readInterpreters,
  )

  private suspend fun readEnvironments() = reportingFailure {
    _uiState.update { it.copy(environments = spaceEnvironments.environments(spaceId)) }
  }

  private suspend fun readInterpreters() = reportingFailure {
    _uiState.update { it.copy(interpreters = spaceKernelRegistration.interpreters(spaceId)) }
  }

  private suspend fun readProjects() = reportingFailure {
    val projects = workspaceRepository.list("").projectFolderNames()
    _uiState.update { it.copy(projects = projects) }
  }

  /** The workspace's project folders: top-level directories that are neither hidden nor env containers. */
  private fun List<WorkspaceEntry>.projectFolderNames(): List<String> =
    filter { it.kind == WorkspaceEntryKind.DIRECTORY && it.name.isProjectFolderName() }.map { it.name }

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
    /** The create act's slot in the state, so the shared runner writes it. */
    val CREATE_SLOT = ActSlot({ it.create }, { s, act -> s.copy(create = act) })

    /** The interpreter register's slot in the state, so the shared runner writes it. */
    val REGISTER_SLOT = ActSlot({ it.interpreterRegister }, { s, act -> s.copy(interpreterRegister = act) })
  }
}
