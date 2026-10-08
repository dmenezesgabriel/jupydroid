package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.port.SpaceEnvironments
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.domain.port.SpaceServers
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.DeleteFileDialog
import com.jupydroid.app.ui.RenameFileDialog
import com.jupydroid.app.ui.relativeTime
import java.time.Instant

/**
 * The app's first screen: every Space on the device, base first, each row its name over
 * `id · created` and an **Open** that enters it.
 *
 * **Every row reads Running or Stopped, and offers Run or Stop**: SageMaker Studio's shape, where
 * several spaces run at once and each is run and stopped by its own control (ADR-0074). The state
 * is the port's ([SpaceServers.running]), so a server that stopped itself when idle reads Stopped.
 *
 * **Open is enabled only while the row reads Running**, SageMaker Studio's *Run space* then *Open
 * JupyterLab* (ADR-0074): the row's state is the one gate, so a Space is never entered stopped.
 * [onOpen] receives the Space's id, which the workspace carries on its key.
 *
 * **Settings is Open's mirror, enabled only while the row reads Stopped**, base included: a Space's
 * settings are read at its next start, so nothing is ever applied to a running process. It opens
 * [SpaceSettingsDialog].
 *
 * **Progress is a bar across the screen's top, not a modal**, which is VS Code's choice for its
 * profiles over Winlator's full-screen preloader: an inline bar leaves the list readable while an
 * act runs
 * (`vscode@3f3380c src/vs/workbench/contrib/userDataProfile/browser/userDataProfilesEditor.ts`,
 * `IEditorProgressService.showWhile`).
 *
 * **A refusal is shown as the port worded it**, above the rows, so a failed act does not leave a
 * list that looks done - see [SpacesViewModel].
 *
 * **Made and renamed by name alone**, in [RenameFileDialog]: a Space's one user-set field is its
 * name, which is what VS Code, IntelliJ and JupyterLab ask for, against Winlator's 18-key form
 * (`winlator@fb66541 app/src/main/java/com/winlator/ContainerDetailFragment.java`). Only a blank
 * name is refused; two Spaces may share one (`SpaceRepositoryContract.twoSpacesMayShareAName`).
 * **Base has no rename control**, VS Code's rule for its built-in profile
 * (`vscode@3f3380c src/vs/workbench/contrib/userDataProfile/browser/userDataProfilesEditor.ts`,
 * `renderName`).
 *
 * **Deleted only after asking**, in [DeleteFileDialog], which names the Space as `"<name> (<id>)"`:
 * VS Code names the profile it would delete
 * (`vscode@3f3380c src/vs/workbench/contrib/userDataProfile/browser/userDataProfilesEditorModel.ts`,
 * `removeProfile`), and the id keeps two same-named Spaces apart. **Base has no delete control**;
 * the port refuses it anyway (`requireDeletableSpace`).
 *
 * ```
 * SpacesHomeScreen(
 *   appContainer.spaceRepository, appContainer.spaceServers, appContainer.spaceEnvironments,
 *   onOpen = backStack::enterWorkspace,
 * )
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacesHomeScreen(
  spaceRepository: SpaceRepository,
  spaceServers: SpaceServers,
  spaceEnvironments: SpaceEnvironments,
  onOpen: (spaceId: String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SpacesViewModel = viewModel { SpacesViewModel(spaceRepository, spaceServers, spaceEnvironments) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var creating by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf<SpaceConfig?>(null) }
  var deleting by remember { mutableStateOf<SpaceConfig?>(null) }
  var configuring by remember { mutableStateOf<SpaceConfig?>(null) }
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(SPACES_TITLE, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
          IconButton(onClick = { creating = true }, enabled = !state.busy) {
            Icon(Icons.Filled.Add, contentDescription = NEW_SPACE)
          }
        },
      )
    },
  ) { padding ->
    // A `Column` rather than a `LazyColumn`: a device holds a handful of Spaces.
    Column(modifier = Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState())) {
      if (state.busy) {
        // Determinate while the act can say how far it is - a create's copy reports its fraction
        // (SpaceRepository.create); rename and delete land in milliseconds and report nothing.
        state.progress?.let { fraction ->
          LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = SPACES_BUSY },
          )
        } ?: LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth().semantics { contentDescription = SPACES_BUSY },
        )
      }
      state.failure?.let { SpacesFailure(it) }
      SpaceRows(
        state.spaces,
        state.running,
        actsEnabled = !state.busy,
        SpaceRowActs(
          onOpen = { onOpen(it.id) },
          onRun = { viewModel.run(it.id) },
          onStop = { viewModel.stop(it.id) },
          onSettings = { configuring = it },
          onRename = { renaming = it },
          onDelete = { deleting = it },
        ),
      )
    }
  }
  if (creating) {
    SpaceNameDialog(heading = NEW_SPACE, currentName = "", confirmLabel = "Create", onDone = { creating = false }) {
      viewModel.create(it)
    }
  }
  renaming?.let { space ->
    SpaceNameDialog(heading = "Rename Space", currentName = space.name, onDone = { renaming = null }) {
      viewModel.rename(space.id, it)
    }
  }
  configuring?.let { space ->
    LaunchedEffect(space.id) { viewModel.listEnvironments(space.id) }
    SpaceSettingsDialog(
      space = space,
      environments = state.environments,
      onSave = {
        configuring = null
        viewModel.configure(space.id, it)
      },
      onDismiss = { configuring = null },
      onRegister = { viewModel.registerEnvironment(space.id, it) },
      refusal = state.failure,
    )
  }
  deleting?.let { space ->
    DeleteFileDialog(
      name = "${space.name} (${space.id})",
      onDelete = {
        deleting = null
        viewModel.delete(space.id)
      },
      onDismiss = { deleting = null },
    )
  }
}

/**
 * The name dialog with a Space's rule: refuse only a blank name, and open with all of it selected,
 * since a Space's name has no extension to keep. [onDone] closes it either way, [onNamed] only on
 * a changed name.
 */
@Composable
private fun SpaceNameDialog(
  heading: String,
  currentName: String,
  onDone: () -> Unit,
  confirmLabel: String = "Rename",
  onNamed: (String) -> Unit,
) {
  RenameFileDialog(
    currentTitle = currentName,
    onConfirm = {
      onDone()
      onNamed(it)
    },
    onDismiss = onDone,
    heading = heading,
    isValidName = String::isNotBlank,
    initialSelection = { TextRange(0, it.length) },
    confirmLabel = confirmLabel,
  )
}

/** What a row's controls do, held together so a row takes one parameter for all of them. */
private class SpaceRowActs(
  val onOpen: (SpaceConfig) -> Unit,
  val onRun: (SpaceConfig) -> Unit,
  val onStop: (SpaceConfig) -> Unit,
  val onSettings: (SpaceConfig) -> Unit,
  val onRename: (SpaceConfig) -> Unit,
  val onDelete: (SpaceConfig) -> Unit,
)

/** The rows, aged against one `now` per listing, the way the explorer's rows are. */
@Composable
private fun SpaceRows(spaces: List<SpaceConfig>, running: Set<String>, actsEnabled: Boolean, acts: SpaceRowActs) {
  val now = remember(spaces) { Instant.now() }
  spaces.forEach { space -> SpaceRow(space, now, space.id in running, actsEnabled, acts) }
}

/**
 * One Space: its name, and under it the id and age. The id is shown because names may repeat
 * (`SpaceRepositoryContract.twoSpacesMayShareAName`) and the id is what tells two such rows apart.
 * Base draws no rename or delete control at all: its directory is the rootfs every Space reads
 * (ADR-0074).
 */
@Composable
private fun SpaceRow(space: SpaceConfig, now: Instant, isRunning: Boolean, actsEnabled: Boolean, acts: SpaceRowActs) {
  val isBase = space.id == BASE_SPACE.id
  val onRename = { acts.onRename(space) }.takeUnless { isBase }
  val onDelete = { acts.onDelete(space) }.takeUnless { isBase }
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SpaceRowText(space, now, isRunning, Modifier.weight(1f))
    RunOrStopButton(space, isRunning, actsEnabled, acts)
    TextButton(
      onClick = { acts.onOpen(space) },
      enabled = actsEnabled && isRunning,
      modifier = Modifier.semantics { contentDescription = "Open ${space.name} (${space.id})" },
    ) {
      Text("Open")
    }
    IconButton(onClick = { acts.onSettings(space) }, enabled = actsEnabled && !isRunning) {
      Icon(Icons.Filled.Settings, contentDescription = "Settings ${space.name} (${space.id})")
    }
    onRename?.let {
      // Named with the id, as the row is: "Rename Thesis" would not say which of two Thesis rows.
      IconButton(onClick = it, enabled = actsEnabled) {
        Icon(Icons.Filled.Edit, contentDescription = "Rename ${space.name} (${space.id})")
      }
    }
    onDelete?.let {
      IconButton(onClick = it, enabled = actsEnabled) {
        Icon(Icons.Filled.Delete, contentDescription = "Delete ${space.name} (${space.id})")
      }
    }
  }
}

/** The one of Run and Stop that applies; named with the id, as Open is, so same-named rows differ. */
@Composable
private fun RunOrStopButton(space: SpaceConfig, isRunning: Boolean, actsEnabled: Boolean, acts: SpaceRowActs) {
  val label = if (isRunning) "Stop" else "Run"
  TextButton(
    onClick = { if (isRunning) acts.onStop(space) else acts.onRun(space) },
    enabled = actsEnabled,
    modifier = Modifier.semantics { contentDescription = "$label ${space.name} (${space.id})" },
  ) {
    Text(label)
  }
}

@Composable
private fun SpaceRowText(space: SpaceConfig, now: Instant, isRunning: Boolean, modifier: Modifier) {
  // One accessible item, so a row reads as "Thesis, space-1 · just now" rather than two nodes.
  // The act buttons stay outside it, each a control of its own.
  Column(
    modifier = modifier
      .semantics(mergeDescendants = true) {}
      .padding(vertical = Spacing.md),
  ) {
    Text(
      text = space.name,
      style = MaterialTheme.typography.bodyLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = "${space.id} · ${relativeTime(space.created, now)}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // A line of its own rather than a third field on the id's: it is the one that changes.
    Text(
      text = if (isRunning) RUNNING else STOPPED,
      style = MaterialTheme.typography.labelMedium,
      color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** The last refusal, verbatim - the port's messages name the id or path at fault. */
@Composable
private fun SpacesFailure(message: String) {
  Text(
    text = message,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
  )
}

/** The screen's title. */
private const val SPACES_TITLE = "Spaces"

/** The add action's accessible name, and the heading of the dialog it opens. */
internal const val NEW_SPACE = "New Space"

/** The bar's accessible name: a progress indicator carries no text of its own to find it by. */
internal const val SPACES_BUSY = "Working on Spaces"

/** A row's state while its Space's server is alive. */
internal const val RUNNING = "Running"

/** A row's state while it is not. */
internal const val STOPPED = "Stopped"
