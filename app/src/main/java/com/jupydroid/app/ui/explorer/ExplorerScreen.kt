package com.jupydroid.app.ui.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.ui.OverwriteFileDialog
import com.jupydroid.app.ui.RenameFileDialog

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
  // The entry whose name is being typed, held here rather than in the row: a `DropdownMenu` item
  // that opened its own dialog would take it down with itself the moment it dismissed.
  var renaming by remember { mutableStateOf<WorkspaceEntry?>(null) }

  // Back climbs the workspace before it leaves the screen, which is `filebrowser:go-up` - the
  // reference binds Backspace to the same thing. Enabled only when there is a parent, so at the
  // root back still means "leave", and nothing swallows it.
  BackHandler(enabled = state.canGoUp) { viewModel.goUp() }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      FilesBar(
        onBack = onBack,
        onCreateNotebook = viewModel::createNotebook,
        onCreatePythonFile = { viewModel.createFile("py") },
        onCreateFolder = viewModel::createFolder,
      )
    },
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
          EntryRow(
            entry = entry,
            onOpen = { viewModel.open(entry, onOpenFile) },
            onRename = { renaming = entry },
          )
        }
      }
    }
  }

  renaming?.let { entry ->
    RenameFileDialog(
      currentTitle = entry.name,
      heading = if (entry.kind == WorkspaceEntryKind.DIRECTORY) "Rename folder" else "Rename file",
      onConfirm = { name ->
        renaming = null
        viewModel.rename(entry, name)
      },
      onDismiss = { renaming = null },
    )
  }

  // Only a name already taken gets here: the repository refuses everything else outright, exactly
  // as `renameFile` rethrows anything that is not a 409.
  state.nameTaken?.let { name ->
    OverwriteFileDialog(
      name = name,
      onOverwrite = { viewModel.answerOverwrite(accepted = true) },
      onDismiss = { viewModel.answerOverwrite(accepted = false) },
    )
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
private fun FilesBar(
  onBack: (() -> Unit)?,
  onCreateNotebook: () -> Unit,
  onCreatePythonFile: () -> Unit,
  onCreateFolder: () -> Unit,
) {
  TopAppBar(
    title = { Text("Files") },
    navigationIcon = {
      onBack?.let {
        IconButton(onClick = it) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to recents")
        }
      }
    },
    actions = {
      NewMenu(
        onCreateNotebook = onCreateNotebook,
        onCreatePythonFile = onCreatePythonFile,
        onCreateFolder = onCreateFolder,
      )
    },
  )
}

/**
 * The reference's New drop-down, on a phone: a "+" that opens Notebook / Python file / Folder.
 *
 * The reference offers the same three from the filebrowser toolbar's New widget and then renames
 * the result in place (`browser.ts:_createNew` -> `rename()`). Ours lands the creation in the
 * browsed directory under the server's own `untitled{ext}` name and leaves the rename to the row's
 * own menu, which X2f gave it - one way to name a thing rather than a second one that only new
 * files can reach.
 */
@Composable
private fun NewMenu(
  onCreateNotebook: () -> Unit,
  onCreatePythonFile: () -> Unit,
  onCreateFolder: () -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  IconButton(onClick = { open = true }) {
    Icon(Icons.Filled.Add, contentDescription = "New")
  }
  DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
    DropdownMenuItem(text = { Text("New notebook") }, onClick = {
      open = false
      onCreateNotebook()
    })
    DropdownMenuItem(text = { Text("New Python file") }, onClick = {
      open = false
      onCreatePythonFile()
    })
    DropdownMenuItem(text = { Text("New folder") }, onClick = {
      open = false
      onCreateFolder()
    })
  }
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
private fun EntryRow(entry: WorkspaceEntry, onOpen: () -> Unit, onRename: () -> Unit) {
  val directory = entry.kind == WorkspaceEntryKind.DIRECTORY
  var menuOpen by remember { mutableStateOf(false) }
  // The same items the long press opens, in the local context menu TalkBack reads. A long press is
  // not reachable by a screen reader at all, which U5e already paid to learn on the cell actions.
  val actions = remember(entry.path, onRename) {
    listOf(CustomAccessibilityAction("Rename") { onRename(); true })
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      // Long press for the row's own actions, which is the touch equivalent of the reference's
      // right-click context menu (`filebrowser-extension`) and of Winlator's per-row menu - and is
      // already what a notebook row on Home opens.
      .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
      .semantics { customActions = actions }
      .padding(horizontal = 16.dp, vertical = 14.dp),
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
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      DropdownMenuItem(
        text = { Text("Rename") },
        onClick = {
          menuOpen = false
          onRename()
        },
      )
    }
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
