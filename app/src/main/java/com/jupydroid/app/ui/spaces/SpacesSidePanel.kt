package com.jupydroid.app.ui.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.SidePanel
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.theme.chromePalette
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
  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = chromePalette.sideBarBackground,
    topBar = {
      TopAppBar(
        colors = sideBarTopAppBarColors(),
        title = { Text(SidePanel.SPACES.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
      SpaceRows(state.spaces)
    }
  }
}

/** The rows, aged against one `now` per listing, the way the explorer's rows are. */
@Composable
private fun SpaceRows(spaces: List<SpaceConfig>) {
  val now = remember(spaces) { Instant.now() }
  spaces.forEach { space -> SpaceRow(space, now) }
}

/**
 * One Space: its name, and under it the id and age. The id is shown because names may repeat
 * (`SpaceRepositoryContract.twoSpacesMayShareAName`) and the id is what tells two such rows apart.
 */
@Composable
private fun SpaceRow(space: SpaceConfig, now: Instant) {
  // One accessible item, so a row reads as "Thesis, space-1 · just now" rather than two nodes.
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .semantics(mergeDescendants = true) {}
      .padding(horizontal = Spacing.lg, vertical = Spacing.md),
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

/** The bar's accessible name: a progress indicator carries no text of its own to find it by. */
internal const val SPACES_BUSY = "Working on Spaces"
