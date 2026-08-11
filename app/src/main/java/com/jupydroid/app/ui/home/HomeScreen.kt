package com.jupydroid.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.domain.model.NotebookSummary
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.RenameNotebookDialog
import java.time.Instant

private const val NEW_NOTEBOOK_TITLE = "Untitled"
private const val NEW_PYTHON_FILE_TITLE = "untitled.py"
private const val DEFAULT_KERNEL_ID = "python3"

@Composable
fun HomeScreen(
  notebookRepository: NotebookRepository,
  importTitle: String? = null,
  onImportConsumed: () -> Unit = {},
  onOpenNotebook: (String) -> Unit = {},
  onBrowseFiles: () -> Unit = {},
  // Null whenever the list has the window to itself, which is every compact window and every
  // wide one with nothing open. Marking a row then would point at a pane that is not there.
  openNotebookId: String? = null,
  modifier: Modifier = Modifier,
  viewModel: HomeViewModel = viewModel { HomeViewModel(notebookRepository) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(importTitle) {
    importTitle?.let { title ->
      viewModel.createNotebook(title, DEFAULT_KERNEL_ID)
      onImportConsumed()
    }
  }

  HomeScaffold(
    notebooks = state.notebooks,
    onCreateNotebook = { viewModel.createNotebook(NEW_NOTEBOOK_TITLE, DEFAULT_KERNEL_ID) },
    onCreatePythonFile = { viewModel.createNotebook(NEW_PYTHON_FILE_TITLE, DEFAULT_KERNEL_ID) },
    onRenameNotebook = { id, title -> viewModel.renameNotebook(id, title) },
    onDeleteNotebook = { id -> viewModel.deleteNotebook(id) },
    onOpenNotebook = onOpenNotebook,
    onBrowseFiles = onBrowseFiles,
    openNotebookId = openNotebookId,
    modifier = modifier,
  )
}

@Composable
private fun HomeScaffold(
  notebooks: List<NotebookSummary>,
  onCreateNotebook: () -> Unit,
  onCreatePythonFile: () -> Unit,
  onRenameNotebook: (String, String) -> Unit,
  onDeleteNotebook: (String) -> Unit,
  onOpenNotebook: (String) -> Unit,
  onBrowseFiles: () -> Unit,
  openNotebookId: String?,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = { RecentsBar(onBrowseFiles = onBrowseFiles) },
    floatingActionButton = {
      HomeFab(onCreateNotebook = onCreateNotebook, onCreatePythonFile = onCreatePythonFile)
    },
  ) { innerPadding ->
    HomeContent(
      notebooks = notebooks,
      now = Instant.now(),
      onRenameNotebook = onRenameNotebook,
      onDeleteNotebook = onDeleteNotebook,
      onOpenNotebook = onOpenNotebook,
      openNotebookId = openNotebookId,
      modifier = Modifier.padding(innerPadding),
    )
  }
}

/**
 * Names the list, and offers the workspace it is a view of.
 *
 * Recents is not a place - it is these notebooks ordered by when they were touched, and it cannot
 * show a folder or the `data.csv` a cell wrote. The explorer is the tree itself, which is what
 * JupyterLab puts in its sidebar; this is the door to it until the two swap places.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentsBar(onBrowseFiles: () -> Unit) {
  TopAppBar(
    title = { Text("Recents") },
    actions = {
      IconButton(onClick = onBrowseFiles) {
        Icon(Icons.Filled.FolderOpen, contentDescription = "Browse files")
      }
    },
  )
}

@Composable
private fun HomeFab(
  onCreateNotebook: () -> Unit,
  onCreatePythonFile: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var menuExpanded by remember { mutableStateOf(false) }
  Box(modifier = modifier) {
    FloatingActionButton(onClick = { menuExpanded = true }) {
      Text(text = "+", style = MaterialTheme.typography.headlineMedium)
    }
    CreateMenu(
      expanded = menuExpanded,
      onDismiss = { menuExpanded = false },
      onCreateNotebook = onCreateNotebook,
      onCreatePythonFile = onCreatePythonFile,
    )
  }
}

@Composable
private fun CreateMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  onCreateNotebook: () -> Unit,
  onCreatePythonFile: () -> Unit,
  modifier: Modifier = Modifier,
) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = modifier) {
    DropdownMenuItem(
      text = { Text("New notebook") },
      onClick = {
        onCreateNotebook()
        onDismiss()
      },
    )
    DropdownMenuItem(
      text = { Text("New Python file") },
      onClick = {
        onCreatePythonFile()
        onDismiss()
      },
    )
  }
}

@Composable
private fun HomeContent(
  notebooks: List<NotebookSummary>,
  now: Instant,
  onRenameNotebook: (String, String) -> Unit,
  onDeleteNotebook: (String) -> Unit,
  onOpenNotebook: (String) -> Unit,
  openNotebookId: String?,
  modifier: Modifier = Modifier,
) {
  if (notebooks.isEmpty()) {
    EmptyRecents(modifier)
  } else {
    val listState = rememberLazyListState()
    ShowTheMostRecent(notebooks.first().id, listState)
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
      items(notebooks, key = { it.id }) { notebook ->
        NotebookRow(
          notebook = notebook,
          now = now,
          isOpen = notebook.id == openNotebookId,
          onOpen = { onOpenNotebook(notebook.id) },
          onRename = { newTitle -> onRenameNotebook(notebook.id, newTitle) },
          onDelete = { onDeleteNotebook(notebook.id) },
        )
      }
    }
  }
}

/**
 * Brings a newly-topmost notebook into view.
 *
 * Without this, creating one looked like it did nothing: the row **was** added, but a
 * `LazyColumn` keeps the items already on screen anchored where they are, so a row prepended
 * above them lands above the viewport - and is never even composed. It only showed up once the
 * list was long enough to scroll, which is why every test of create-then-appear passed while
 * the bug was live.
 */
@Composable
private fun ShowTheMostRecent(topNotebookId: String, listState: LazyListState) {
  LaunchedEffect(topNotebookId) { listState.scrollToItem(0) }
}

@Composable
private fun EmptyRecents(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      text = "No recent notebooks yet",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookRow(
  notebook: NotebookSummary,
  now: Instant,
  isOpen: Boolean,
  onOpen: () -> Unit,
  onRename: (String) -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var menuExpanded by remember { mutableStateOf(false) }
  var renameOpen by remember { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        // One accessible item, not two Texts and a click target: a row reads as
        // "Switch Alpha, just now, selected" instead of making TalkBack walk three nodes. It is
        // also what lets `selected` below be found on the node the title is on.
        .semantics(mergeDescendants = true) { selected = isOpen }
        .background(if (isOpen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
        .combinedClickable(onClick = onOpen, onLongClick = { menuExpanded = true })
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Text(
        text = notebook.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = relativeTime(notebook.updatedAt, now),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
      DropdownMenuItem(
        text = { Text("Rename") },
        onClick = {
          menuExpanded = false
          renameOpen = true
        },
      )
      DropdownMenuItem(
        text = { Text("Delete") },
        onClick = {
          menuExpanded = false
          onDelete()
        },
      )
    }
  }

  if (renameOpen) {
    RenameNotebookDialog(
      currentTitle = notebook.title,
      onConfirm = { newTitle ->
        renameOpen = false
        onRename(newTitle)
      },
      onDismiss = { renameOpen = false },
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun HomeEmptyPreview() {
  JupyterDroidTheme {
    HomeContent(
      notebooks = emptyList(),
      now = Instant.now(),
      onRenameNotebook = { _, _ -> },
      onDeleteNotebook = {},
      onOpenNotebook = {},
      openNotebookId = null,
    )
  }
}
