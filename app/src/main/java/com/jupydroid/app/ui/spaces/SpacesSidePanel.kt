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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.jupydroid.app.SidePanel
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.app.ui.DeleteFileDialog
import com.jupydroid.app.ui.RenameFileDialog
import com.jupydroid.app.ui.relativeTime
import com.jupydroid.app.ui.shell.label
import com.jupydroid.app.ui.shell.sideBarTopAppBarColors
import java.time.Instant

/**
 * Every Space on the device, base first, each row its name over `id · created`.
 *
 * **Progress is a bar across the panel's top, not a modal**, which is VS Code's choice for its
 * profiles over Winlator's full-screen preloader: an act here runs beside an open document, and
 * an inline bar leaves the document usable
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
 * The same chrome as the panels beside it: a `Scaffold` on the side bar's surface under
 * [sideBarTopAppBarColors].
 *
 * ```
 * SpacesSidePanel(spaceRepository = appContainer.spaceRepository)
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacesSidePanel(
  spaceRepository: SpaceRepository,
  modifier: Modifier = Modifier,
  viewModel: SpacesViewModel = viewModel { SpacesViewModel(spaceRepository) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var creating by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf<SpaceConfig?>(null) }
  var deleting by remember { mutableStateOf<SpaceConfig?>(null) }
  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = chromePalette.sideBarBackground,
    topBar = {
      TopAppBar(
        colors = sideBarTopAppBarColors(),
        title = { Text(SidePanel.SPACES.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
          IconButton(onClick = { creating = true }, enabled = !state.busy) {
            Icon(Icons.Filled.Add, contentDescription = NEW_SPACE)
          }
        },
      )
    },
  ) { padding ->
    // A `Column` rather than a `LazyColumn`: a device holds a handful of Spaces, and a lazy list
    // composes nothing inside a pane laid out at zero height, which is what the list pane is under
    // `ForcedSize` (`SidePaneNavigationTest.whatASearchFoundSurvivesLeavingThePanelAndComingBack`).
    Column(modifier = Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState())) {
      if (state.busy) {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth().semantics { contentDescription = SPACES_BUSY },
        )
      }
      state.failure?.let { SpacesFailure(it) }
      SpaceRows(state.spaces, actsEnabled = !state.busy, onRename = { renaming = it }, onDelete = { deleting = it })
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

/** The rows, aged against one `now` per listing, the way the explorer's rows are. */
@Composable
private fun SpaceRows(
  spaces: List<SpaceConfig>,
  actsEnabled: Boolean,
  onRename: (SpaceConfig) -> Unit,
  onDelete: (SpaceConfig) -> Unit,
) {
  val now = remember(spaces) { Instant.now() }
  spaces.forEach { space ->
    val isBase = space.id == BASE_SPACE.id
    SpaceRow(
      space,
      now,
      actsEnabled,
      onRename = { onRename(space) }.takeUnless { isBase },
      onDelete = { onDelete(space) }.takeUnless { isBase },
    )
  }
}

/**
 * One Space: its name, and under it the id and age. The id is shown because names may repeat
 * (`SpaceRepositoryContract.twoSpacesMayShareAName`) and the id is what tells two such rows apart.
 * A null [onRename] or [onDelete] draws no control at all, which is base's case.
 */
@Composable
private fun SpaceRow(
  space: SpaceConfig,
  now: Instant,
  actsEnabled: Boolean,
  onRename: (() -> Unit)?,
  onDelete: (() -> Unit)?,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SpaceRowText(space, now, Modifier.weight(1f))
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

@Composable
private fun SpaceRowText(space: SpaceConfig, now: Instant, modifier: Modifier) {
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

/** The add action's accessible name, and the heading of the dialog it opens. */
internal const val NEW_SPACE = "New Space"

/** The bar's accessible name: a progress indicator carries no text of its own to find it by. */
internal const val SPACES_BUSY = "Working on Spaces"
