package com.jupydroid.app.ui.environments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.SidePanel
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.port.SpaceEnvironmentCreation
import com.jupydroid.app.domain.port.SpaceEnvironments
import com.jupydroid.app.domain.port.SpaceKernelRegistration
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.shell.SideBarScaffold
import com.jupydroid.app.ui.shell.label
import com.jupydroid.app.ui.shell.sideBarTopAppBarColors
import com.jupydroid.extension.api.FocusRingIconButton
import com.jupydroid.extension.api.FocusRingTextButton
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * `SidePanel.ENVIRONMENTS` - the Space's Python environments, grouped by the project folder each
 * belongs to, with the acts that make and register them.
 *
 * Every top-level project folder (`isProjectFolderName`'s rule, the same one the env scan finds
 * its bases by) is a section: its environments by the shared `EnvironmentRow`, then a Create that
 * runs `jupydroid-env create <project>` through [SpaceEnvironmentCreation] with the script's output
 * streamed into [ScriptActArea]. An environment outside every project - `envs/stats`, a
 * bare `myenv` - lands in the trailing Workspace group. The list polls while composed, the
 * reference's `Poll{interval: 10s, standby: 'when-hidden'}` shape `RunningSidePanel` already
 * keeps, so an environment `jupydroid-env` made in the Space's terminal appears without a
 * relaunch - the demo's own path.
 *
 * Trailing it all, the Space's own interpreters: a bare interpreter is no environment the scan
 * finds, so its Register runs `jupydroid-env register` through [SpaceKernelRegistration] - the
 * same bytes the terminal runs - rather than `SpaceEnvironments.register`, which a `pyvenv.cfg`
 * is owed for.
 *
 * ```
 * EnvironmentsSidePanel(appContainer.spaceEnvironments, appContainer.spaceEnvironmentCreation,
 *   appContainer.spaceKernelRegistration, spacePorts.workspaceRepository, spacePorts.space.id)
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentsSidePanel(
  spaceEnvironments: SpaceEnvironments,
  spaceEnvironmentCreation: SpaceEnvironmentCreation,
  spaceKernelRegistration: SpaceKernelRegistration,
  workspaceRepository: WorkspaceRepository,
  spaceId: String,
  modifier: Modifier = Modifier,
  viewModel: EnvironmentsPanelViewModel = viewModel {
    EnvironmentsPanelViewModel(
      spaceId,
      spaceEnvironments,
      spaceEnvironmentCreation,
      spaceKernelRegistration,
      workspaceRepository,
    )
  },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // Composed *is* visible here, so cancelling the effect when the rail switches away is the whole
  // of the standby half - and the poll is also how a terminal-made environment reaches the panel.
  LaunchedEffect(viewModel) {
    while (true) {
      viewModel.refresh()
      delay(POLL_INTERVAL)
    }
  }
  val creating = state.create is ScriptActState.Running
  val acting = creating || state.interpreterRegister is ScriptActState.Running
  SideBarScaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        colors = sideBarTopAppBarColors(),
        title = { Text(SidePanel.ENVIRONMENTS.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
          FocusRingIconButton(
            onClick = viewModel::refresh,
            modifier = Modifier.semantics { contentDescription = "Refresh list" },
          ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
          }
        },
      )
    },
  ) { padding ->
    Column(
      Modifier
        .padding(padding)
        .padding(Spacing.md)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .then(if (acting) Modifier.keepScreenOn() else Modifier),
    ) {
      if (acting) {
        Text(CREATE_KEEP_OPEN_HINT, style = MaterialTheme.typography.bodySmall)
      }
      state.refusal?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
      }
      state.create?.let {
        ScriptActArea(it, "Creating environment for ${it.subject}", "Create failed for ${it.subject}")
      }
      state.interpreterRegister?.let {
        ScriptActArea(it, "Registering kernel for ${it.subject}", "Register failed for ${it.subject}")
      }
      EnvironmentsBody(state, viewModel)
      InterpretersSection(state, viewModel)
    }
  }
}

/** The read states and the grouped list - what the scroll column draws under the chrome. */
@Composable
private fun EnvironmentsBody(state: EnvironmentsPanelState, viewModel: EnvironmentsPanelViewModel) {
  val environments = state.environments
  val projects = state.projects
  when {
    environments == null || projects == null ->
      Text("Reading…", style = MaterialTheme.typography.bodySmall)
    environments.isEmpty() && projects.isEmpty() ->
      Text(NO_ENVIRONMENTS, style = MaterialTheme.typography.bodySmall)
    else -> EnvironmentGroups(environments, projects, state.create, viewModel)
  }
}

/**
 * One section per project folder, then the environments outside every project under Workspace.
 * An environment's project is its first path segment when that segment is a project folder -
 * `proj-a/.venv` is proj-a's, `envs/stats` is the workspace's, and a bare `myenv` is too, having
 * no segment to qualify it.
 */
@Composable
private fun EnvironmentGroups(
  environments: List<PythonEnvironment>,
  projects: List<String>,
  create: ScriptActState?,
  viewModel: EnvironmentsPanelViewModel,
) {
  val creating = create is ScriptActState.Running
  val projectSet = projects.toSet()
  val envPaths = environments.filter { !it.isMissing }.map { it.path }.toSet()
  val byProject = environments.groupBy { it.projectNameIn(projectSet) }
  projects.forEach { project ->
    ProjectSection(
      project = project,
      environments = byProject[project].orEmpty(),
      // A `.venv` already there is the script's own refusal waiting to happen; a gone one's spec
      // is no `.venv` on disk, so it does not count (`isMissing` is filtered out of [envPaths]).
      createEnabled = !creating && "$project/.venv" !in envPaths,
      onCreate = { viewModel.create(project) },
      onRegister = viewModel::register,
    )
  }
  val workspace = byProject[null].orEmpty()
  if (workspace.isNotEmpty()) {
    Text("Workspace", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.lg))
    workspace.forEach { EnvironmentRow(it, viewModel::register) }
  }
}

/** The project folder [this] environment sits in, or null for one outside every project. */
private fun PythonEnvironment.projectNameIn(projects: Set<String>): String? {
  val first = path.substringBefore('/')
  return first.takeIf { first != path && first in projects }
}

/** One project's section: its name, its environments' rows, and the create its `.venv` is made by. */
@Composable
private fun ProjectSection(
  project: String,
  environments: List<PythonEnvironment>,
  createEnabled: Boolean,
  onCreate: () -> Unit,
  onRegister: (String) -> Unit,
) {
  Column(Modifier.padding(top = Spacing.lg)) {
    Text(project, style = MaterialTheme.typography.titleSmall)
    environments.forEach { EnvironmentRow(it, onRegister) }
    FocusRingTextButton(
      onClick = onCreate,
      enabled = createEnabled,
      modifier = Modifier.semantics { contentDescription = createLabel(project) },
    ) {
      Text("Create environment")
    }
  }
}

/**
 * The Space's own interpreters, one row each with its Register. While one register runs the
 * others' buttons wait, as a second create's does - the ViewModel refuses a concurrent one and
 * the row says so before it is pressed.
 */
@Composable
private fun InterpretersSection(state: EnvironmentsPanelState, viewModel: EnvironmentsPanelViewModel) {
  val interpreters = state.interpreters.orEmpty()
  if (interpreters.isEmpty()) return
  Text("Interpreters", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.lg))
  val registering = state.interpreterRegister is ScriptActState.Running
  interpreters.forEach { InterpreterRow(it, enabled = !registering, onRegister = viewModel::registerInterpreter) }
}

/** Create's accessible name on [project]'s section, for a test and a Maestro flow. */
internal fun createLabel(project: String): String = "Create environment for $project"

/**
 * Said once while a create runs: a cold one is measured in tens of seconds of pip, the panel
 * holds the screen on for it, and a stopped run leaves a half-made `.venv` the script refuses to
 * redo - the terminal's `rm -rf` is the retry (`ScriptActArea`'s own note).
 */
internal const val CREATE_KEEP_OPEN_HINT =
  "Keep Jupydroid open while an environment is created - it is a download; a stopped run leaves a half-made .venv to remove in a terminal"

/** `Poll`'s own cadence - `RunningSidePanel`'s `POLL_INTERVAL`, the reference's 10 s. */
private val POLL_INTERVAL = 10.seconds
