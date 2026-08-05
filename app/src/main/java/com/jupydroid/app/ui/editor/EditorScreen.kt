package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.NotebookRepository

@Composable
fun EditorScreen(
  notebookId: String,
  notebookRepository: NotebookRepository,
  kernelGateway: KernelGateway,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: EditorViewModel = viewModel { EditorViewModel(notebookId, notebookRepository, kernelGateway) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      EditorTopBar(
        title = state.title,
        busy = state.runningCellId != null,
        onRunAll = viewModel::runAll,
        onBack = onBack,
      )
    },
  ) { innerPadding ->
    EditorContent(
      state = state,
      onRunCell = viewModel::runCell,
      onStopCell = viewModel::stopRunningCell,
      modifier = Modifier.padding(innerPadding),
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(title: String, busy: Boolean, onRunAll: () -> Unit, onBack: () -> Unit) {
  TopAppBar(
    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    actions = {
      // Disabled while a cell runs: a second Run All would queue every cell again behind
      // the one already going.
      IconButton(onClick = onRunAll, enabled = !busy) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Run all cells")
      }
    },
    navigationIcon = {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
    },
  )
}

@Composable
private fun EditorContent(
  state: EditorUiState,
  onRunCell: (Cell) -> Unit,
  onStopCell: () -> Unit,
  modifier: Modifier = Modifier,
) {
  when {
    state.error != null -> ErrorMessage(message = state.error, modifier = modifier)
    !state.loaded -> EmptyMessage("Loading\u2026", modifier)
    state.cells.isEmpty() -> EmptyMessage("No cells yet", modifier)
    else ->
      LazyColumn(modifier = modifier.fillMaxSize()) {
        items(state.cells, key = { it.id }) { cell ->
          EditorCell(
            cell = cell,
            outputs = state.outputs[cell.id].orEmpty(),
            executionCount = state.executionCounts[cell.id],
            running = state.runningCellId == cell.id,
            onRun = { onRunCell(cell) },
            onStop = onStopCell,
          )
        }
      }
  }
}

@Composable
private fun EditorCell(
  cell: Cell,
  outputs: List<CellOutput>,
  executionCount: Int?,
  running: Boolean,
  onRun: () -> Unit,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    CellHeader(
      cell = cell,
      executionCount = executionCount,
      running = running,
      onRun = onRun,
      onStop = onStop,
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
    CellOutputs(outputs)
  }
}

@Composable
private fun CellHeader(
  cell: Cell,
  executionCount: Int?,
  running: Boolean,
  onRun: () -> Unit,
  onStop: () -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      // The bracket notation a Jupyter user already reads as "this cell has run N times".
      text = executionCount?.let { "[$it]" } ?: cell.kind.label(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
    )
    if (cell.kind != CellKind.CODE) return@Row
    if (running) {
      // Spinner *and* Stop: the spinner says the kernel is working, the button is the only
      // way to take it back, since the cell has no handle on the kernel's work.
      CircularProgressIndicator(
        modifier = Modifier.padding(start = 8.dp).size(16.dp).semantics { contentDescription = "Running" },
        strokeWidth = 2.dp,
      )
      IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
        // The universal stop square, drawn rather than imported: it is the only glyph this
        // screen would need from material-icons-extended.
        Box(
          modifier = Modifier
            .size(12.dp)
            .background(MaterialTheme.colorScheme.error, MaterialTheme.shapes.extraSmall)
            .semantics { contentDescription = "Stop cell" },
        )
      }
    } else {
      IconButton(onClick = onRun, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Filled.PlayArrow, contentDescription = "Run cell")
      }
    }
  }
}

/**
 * Plain text for every output kind. Rich rendering - images, HTML, themed error tracebacks -
 * is Step 7; this exists so a run has something visible to prove it worked.
 */
@Composable
private fun CellOutputs(outputs: List<CellOutput>) {
  if (outputs.isEmpty()) return
  Spacer(modifier = Modifier.height(4.dp))
  Column(modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Cell output" }) {
    outputs.forEach { output ->
      Text(
        text = output.asText(),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = if (output.isError()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

private fun CellOutput.isError(): Boolean =
  this is CellOutput.Error || (this is CellOutput.Stream && name == StreamName.STDERR)

private fun CellOutput.asText(): String = when (this) {
  is CellOutput.Stream -> text.trimEnd()
  is CellOutput.Error -> "$name: $value"
  is CellOutput.ExecuteResult -> mimeBundle["text/plain"].orEmpty()
  is CellOutput.DisplayData -> mimeBundle["text/plain"] ?: mimeBundle.keys.joinToString()
  CellOutput.ClearOutput -> ""
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
