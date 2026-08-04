package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.port.NotebookRepository

@Composable
fun EditorScreen(
  notebookId: String,
  notebookRepository: NotebookRepository,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: EditorViewModel = viewModel { EditorViewModel(notebookId, notebookRepository) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = { EditorTopBar(title = state.title, onBack = onBack) },
  ) { innerPadding ->
    EditorContent(state = state, modifier = Modifier.padding(innerPadding))
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(title: String, onBack: () -> Unit) {
  TopAppBar(
    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    navigationIcon = {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
    },
  )
}

@Composable
private fun EditorContent(state: EditorUiState, modifier: Modifier = Modifier) {
  when {
    state.error != null -> ErrorMessage(message = state.error, modifier = modifier)
    !state.loaded -> EmptyMessage("Loading\u2026", modifier)
    state.cells.isEmpty() -> EmptyMessage("No cells yet", modifier)
    else ->
      LazyColumn(modifier = modifier.fillMaxSize()) {
        items(state.cells, key = { it.id }) { cell -> EditorCell(cell) }
      }
  }
}

@Composable
private fun EditorCell(cell: Cell, modifier: Modifier = Modifier) {
  Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(
      text = cell.kind.label(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = cell.source,
      style = MaterialTheme.typography.bodyMedium,
      fontFamily = if (cell.kind == CellKind.CODE) FontFamily.Monospace else FontFamily.Default,
      modifier =
        if (cell.kind == CellKind.CODE) {
          Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(8.dp)
        } else {
          Modifier.fillMaxWidth()
        },
    )
  }
}

@Composable
private fun ErrorMessage(message: String, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
    Text(
      text = message,
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodyLarge,
    )
  }
}

@Composable
private fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun CellKind.label(): String = when (this) {
  CellKind.CODE -> "code"
  CellKind.MARKDOWN -> "markdown"
}
