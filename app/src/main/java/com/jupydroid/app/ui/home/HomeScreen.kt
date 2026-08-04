package com.jupydroid.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.domain.model.NotebookSummary
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.theme.JupyterDroidTheme
import java.time.Instant

private const val NEW_NOTEBOOK_TITLE = "Untitled"
private const val NEW_PYTHON_FILE_TITLE = "untitled.py"
private const val DEFAULT_KERNEL_ID = "python3"
private const val RENAME_FIELD_TAG = "rename-field"

@Composable
fun HomeScreen(
  notebookRepository: NotebookRepository,
  importTitle: String? = null,
  onImportConsumed: () -> Unit = {},
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
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    floatingActionButton = {
      HomeFab(onCreateNotebook = onCreateNotebook, onCreatePythonFile = onCreatePythonFile)
    },
  ) { innerPadding ->
    HomeContent(
      notebooks = notebooks,
      now = Instant.now(),
      onRenameNotebook = onRenameNotebook,
      onDeleteNotebook = onDeleteNotebook,
      modifier = Modifier.padding(innerPadding),
    )
  }
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
  modifier: Modifier = Modifier,
) {
  if (notebooks.isEmpty()) {
    EmptyRecents(modifier)
  } else {
    LazyColumn(modifier = modifier.fillMaxSize()) {
      items(notebooks, key = { it.id }) { notebook ->
        NotebookRow(
          notebook = notebook,
          now = now,
          onRename = { newTitle -> onRenameNotebook(notebook.id, newTitle) },
          onDelete = { onDeleteNotebook(notebook.id) },
        )
      }
    }
  }
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
        .combinedClickable(onClick = {}, onLongClick = { menuExpanded = true })
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
    RenameDialog(
      notebook = notebook,
      onConfirm = { newTitle ->
        renameOpen = false
        onRename(newTitle)
      },
      onDismiss = { renameOpen = false },
    )
  }
}

@Composable
private fun RenameDialog(
  notebook: NotebookSummary,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  var title by remember(notebook.id) { mutableStateOf(notebook.title) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rename notebook") },
    text = {
      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        modifier = Modifier
          .semantics { testTagsAsResourceId = true }
          .testTag(RENAME_FIELD_TAG),
        singleLine = true,
        label = { Text("Title") },
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(title.trim()) }, enabled = title.isNotBlank()) {
        Text("Rename")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
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
    )
  }
}
