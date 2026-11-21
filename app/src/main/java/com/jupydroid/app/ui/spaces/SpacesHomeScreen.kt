package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.port.HarnessScripts
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.SpaceEnvironments
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.domain.port.SpaceServers
import com.jupydroid.app.domain.port.SpaceShell
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.relativeTime
import com.jupydroid.extension.api.DeleteFileDialog
import com.jupydroid.extension.api.FocusRingIconButton
import com.jupydroid.extension.api.FocusRingTextButton
import com.jupydroid.extension.api.RenameFileDialog
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
  stoppedKernelSpecs: KernelSpecs,
  spaceShell: SpaceShell,
  harnessScripts: HarnessScripts,
  onOpen: (spaceId: String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SpacesViewModel = viewModel {
    SpacesViewModel(spaceRepository, spaceServers, spaceEnvironments, stoppedKernelSpecs)
  },
  harnessesViewModel: SpaceHarnessesViewModel = viewModel {
    SpaceHarnessesViewModel(spaceShell, harnessScripts)
  },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val harnessesState by harnessesViewModel.uiState.collectAsStateWithLifecycle()
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
          FocusRingIconButton(onClick = { creating = true }, enabled = !state.busy) {
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
        state.defaultKernelNotes,
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
    LaunchedEffect(space.id) { viewModel.openSpaceSettings(space.id) }
    // The harnesses' probes and installs live exactly as long as the dialog: open() re-asks
    // every catalog harness in this Space's shell, and dismiss() ends whatever is in flight.
    LaunchedEffect(space.id) { harnessesViewModel.open(space.id) }
    DisposableEffect(space.id) { onDispose { harnessesViewModel.dismiss() } }
    SpaceSettingsDialog(
      space = space,
      environments = state.environments,
      kernelChoices = state.kernelChoices,
      onSave = {
        configuring = null
        viewModel.configure(space.id, it)
      },
      onDismiss = { configuring = null },
      onRegister = { viewModel.registerEnvironment(space.id, it) },
      refusal = state.failure,
      harnesses = harnessesState,
      onHarnessInstall = harnessesViewModel::install,
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
private fun SpaceRows(
  spaces: List<SpaceConfig>,
  running: Set<String>,
  defaultKernelNotes: Map<String, String>,
  actsEnabled: Boolean,
  acts: SpaceRowActs,
) {
  val now = remember(spaces) { Instant.now() }
  spaces.forEach { space ->
    SpaceRow(space, now, RowStatus(space.id in running, defaultKernelNotes[space.id]), actsEnabled, acts)
  }
}

/** What a row says changes about its Space: whether it runs, and why its default kernel fell back. */
private class RowStatus(val isRunning: Boolean, val defaultKernelNote: String?)

/**
 * One Space: its name, and under it the id and age. The id is shown because names may repeat
 * (`SpaceRepositoryContract.twoSpacesMayShareAName`) and the id is what tells two such rows apart.
 * Base draws no rename or delete control at all: its directory is the rootfs every Space reads
 * (ADR-0074).
 *
 * **The controls drop under the text, together, when they would leave it less than its widest
 * status word.** They are fixed-width, so at twice the font scale on a 320 dp phone five of them
 * need about the whole row and "Stopped" was drawn "Stoppe / d" in what was left
 * (`SpacesHomeScreenFontScaleTest`). At the default size they share the text's line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpaceRow(space: SpaceConfig, now: Instant, status: RowStatus, actsEnabled: Boolean, acts: SpaceRowActs) {
  FlowRow(
    modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs),
    horizontalArrangement = Arrangement.End,
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    SpaceRowText(space, now, status, Modifier.weight(1f).widthIn(min = rememberStatusWordWidth()))
    Row(verticalAlignment = Alignment.CenterVertically) {
      SpaceRowControls(space, status.isRunning, actsEnabled, acts)
    }
  }
}

/** The widest of the two status words as the row draws them, so the text column is never narrower. */
@Composable
private fun rememberStatusWordWidth(): Dp {
  val measurer = rememberTextMeasurer()
  val style = MaterialTheme.typography.labelMedium
  val density = LocalDensity.current
  return remember(measurer, style, density) {
    val widestPx = listOf(RUNNING, STOPPED).maxOf { measurer.measure(it, style).size.width }
    with(density) { widestPx.toDp() }
  }
}

@Composable
private fun SpaceRowControls(space: SpaceConfig, isRunning: Boolean, actsEnabled: Boolean, acts: SpaceRowActs) {
  val isBase = space.id == BASE_SPACE.id
  RunOrStopButton(space, isRunning, actsEnabled, acts)
  FocusRingTextButton(
    onClick = { acts.onOpen(space) },
    enabled = actsEnabled && isRunning,
    modifier = Modifier.semantics { contentDescription = "Open ${space.name} (${space.id})" },
  ) {
    Text("Open")
  }
  FocusRingIconButton(onClick = { acts.onSettings(space) }, enabled = actsEnabled && !isRunning) {
    Icon(Icons.Filled.Settings, contentDescription = "Settings ${space.name} (${space.id})")
  }
  if (isBase) return
  // Named with the id, as the row is: "Rename Thesis" would not say which of two Thesis rows.
  FocusRingIconButton(onClick = { acts.onRename(space) }, enabled = actsEnabled) {
    Icon(Icons.Filled.Edit, contentDescription = "Rename ${space.name} (${space.id})")
  }
  FocusRingIconButton(onClick = { acts.onDelete(space) }, enabled = actsEnabled) {
    Icon(Icons.Filled.Delete, contentDescription = "Delete ${space.name} (${space.id})")
  }
}

/** The one of Run and Stop that applies; named with the id, as Open is, so same-named rows differ. */
@Composable
private fun RunOrStopButton(space: SpaceConfig, isRunning: Boolean, actsEnabled: Boolean, acts: SpaceRowActs) {
  val label = if (isRunning) "Stop" else "Run"
  FocusRingTextButton(
    onClick = { if (isRunning) acts.onStop(space) else acts.onRun(space) },
    enabled = actsEnabled,
    modifier = Modifier.semantics { contentDescription = "$label ${space.name} (${space.id})" },
  ) {
    Text(label)
  }
}

@Composable
private fun SpaceRowText(space: SpaceConfig, now: Instant, status: RowStatus, modifier: Modifier) {
  val isRunning = status.isRunning
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
    // Said on the row, before any notebook records the kernel the server falls back to.
    status.defaultKernelNote?.let {
      Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
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
