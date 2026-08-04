package com.jupydroid.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun HomeScreen(
  notebookRepository: NotebookRepository,
  modifier: Modifier = Modifier,
  viewModel: HomeViewModel = viewModel { HomeViewModel(notebookRepository) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  HomeScaffold(
    notebooks = state.notebooks,
    onCreateNotebook = { viewModel.createNotebook(NEW_NOTEBOOK_TITLE, DEFAULT_KERNEL_ID) },
    onCreatePythonFile = { viewModel.createNotebook(NEW_PYTHON_FILE_TITLE, DEFAULT_KERNEL_ID) },
    modifier = modifier,
  )
}

@Composable
private fun HomeScaffold(
  notebooks: List<NotebookSummary>,
  onCreateNotebook: () -> Unit,
  onCreatePythonFile: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    floatingActionButton = {
      HomeFab(onCreateNotebook = onCreateNotebook, onCreatePythonFile = onCreatePythonFile)
    },
  ) { innerPadding ->
    HomeContent(notebooks = notebooks, now = Instant.now(), modifier = Modifier.padding(innerPadding))
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
  modifier: Modifier = Modifier,
) {
  if (notebooks.isEmpty()) {
    EmptyRecents(modifier)
  } else {
    LazyColumn(modifier = modifier.fillMaxSize()) {
      items(notebooks, key = { it.id }) { notebook ->
        NotebookRow(notebook = notebook, now = now)
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

@Composable
private fun NotebookRow(
  notebook: NotebookSummary,
  now: Instant,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
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
}

@Preview(showBackground = true)
@Composable
private fun HomeEmptyPreview() {
  JupyterDroidTheme { HomeContent(notebooks = emptyList(), now = Instant.now()) }
}
