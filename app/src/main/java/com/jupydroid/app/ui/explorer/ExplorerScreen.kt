package com.jupydroid.app.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository

/**
 * The workspace, one directory at a time.
 *
 * JupyterLab's file browser is a single pane with a breadcrumb above it, not an expanding tree
 * (`packages/filebrowser`), and that is the shape a phone wants for the same reason it suits a
 * narrow sidebar: indentation would spend the width the names need.
 *
 * ```
 * ExplorerScreen(workspaceRepository, onOpenFile = { backStack.openNotebook(it.path) })
 * ```

 * @param onBack how to leave, or null when there is nowhere to leave to. Recents pushes the
 *   explorer today and the explorer becomes the root once Recents is a section of it; the arrow
 *   follows that rather than being drawn unconditionally.
 */
@Composable
fun ExplorerScreen(
  workspaceRepository: WorkspaceRepository,
  notebookRepository: NotebookRepository,
  onOpenFile: (WorkspaceEntry) -> Unit,
  onBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  viewModel: ExplorerViewModel =
    viewModel { ExplorerViewModel(workspaceRepository, notebookRepository) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  // Back climbs the workspace before it leaves the screen, which is `filebrowser:go-up` - the
  // reference binds Backspace to the same thing. Enabled only when there is a parent, so at the
  // root back still means "leave", and nothing swallows it.
  BackHandler(enabled = state.canGoUp) { viewModel.goUp() }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = { FilesBar(onBack = onBack) },
  ) { padding ->
    Column(modifier = Modifier.padding(padding)) {
      Breadcrumb(crumbs = state.crumbs, onGoTo = viewModel::goTo)
      HorizontalDivider()
      state.error?.let { ExplorerError(it) }
      LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Keyed by path, which is the identity of a workspace entry and a String, so it survives
        // the Bundle round trip Compose needs for state restoration. contentType by kind, so a
        // folder row and a file row reuse separate composition pools rather than each other's.
        items(state.entries, key = { it.path }, contentType = { it.kind }) { entry ->
          EntryRow(entry = entry, onOpen = { viewModel.open(entry, onOpenFile) })
        }
      }
    }
  }
}

/**
 * What the screen is, above the breadcrumb that says where in it you are.
 *
 * Two rows rather than one: the title is fixed and the crumbs scroll, and putting them together
 * would make the title scroll away with them - on a phone that is most of the bar's width gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesBar(onBack: (() -> Unit)?) {
  TopAppBar(
    title = { Text("Files") },
    navigationIcon = {
      onBack?.let {
        IconButton(onClick = it) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to recents")
        }
      }
    },
  )
}

/**
 * The ancestry, scrolling sideways rather than eliding.
 *
 * Horizontal inside a vertical list is the one nesting Compose allows without a fixed size - the
 * guidance forbids only *same-direction* nesting - and it is what keeps the folder you are **in**
 * on screen. Truncating the tail would hide precisely the crumb that matters.
 */
@Composable
private fun Breadcrumb(crumbs: List<Pair<String, String>>, onGoTo: (String) -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    crumbs.forEachIndexed { at, (path, label) ->
      if (at > 0) {
        Icon(
          Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          modifier = Modifier.size(SEPARATOR),
        )
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clickable { onGoTo(path) }.padding(horizontal = 12.dp, vertical = 14.dp),
      )
    }
  }
}

/**
 * One entry: an icon that says what it is, and its name.
 *
 * The icon carries the kind for a scanning eye and its `contentDescription` carries it for a
 * screen reader, which is the same split the reference makes between its file-type icons and the
 * `data-isdir` its own menu selectors read.
 */
@Composable
private fun EntryRow(entry: WorkspaceEntry, onOpen: () -> Unit) {
  val directory = entry.kind == WorkspaceEntryKind.DIRECTORY
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      // The reference gives every row a file-type icon from its icon registry; these are the
      // Material equivalents. AutoMirrored because the page-corner fold flips in a right-to-left
      // locale, the same way the breadcrumb's chevron does.
      imageVector = if (directory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
      contentDescription = if (directory) "Folder" else "File",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = entry.name,
      style = MaterialTheme.typography.bodyLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(start = 16.dp),
    )
  }
}

/** What went wrong reaching a directory - a shared workspace can lose one under the user. */
@Composable
private fun ExplorerError(message: String) {
  Text(
    text = message,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
  )
}

private val SEPARATOR = 16.dp
