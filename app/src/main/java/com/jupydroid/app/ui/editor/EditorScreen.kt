package com.jupydroid.app.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jupydroid.app.theme.editorPalette
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.CoroutineScope

@Composable
fun EditorScreen(
  notebookId: String,
  notebookRepository: NotebookRepository,
  kernelGateway: KernelGateway,
  applicationScope: CoroutineScope,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: EditorViewModel = viewModel {
    EditorViewModel(notebookId, notebookRepository, kernelGateway, applicationScope)
  },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val focusManager = LocalFocusManager.current
  // Back in edit mode returns to command mode instead of leaving the notebook - the touch
  // analogue of `Escape`, which is what both references bind it to. Dropping focus is what
  // actually does it: the field loses the caret, the keyboard goes, and the focus listener below
  // reports the mode change, so there is one path out of edit mode rather than two.
  BackHandler(enabled = state.mode == CellMode.EDIT) { focusManager.clearFocus() }
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      EditorTopBar(
        title = state.title,
        busy = state.runningCellId != null,
        onRunAll = viewModel.kernel::runAll,
        onRename = viewModel::renameNotebook,
        onBack = onBack,
      )
    },
  ) { innerPadding ->
    EditorContent(
      state = state,
      onRunCell = viewModel.kernel::runCell,
      onStopCell = viewModel.kernel::stopRunningCell,
      onEditCell = viewModel::editCell,
      completionsAt = viewModel.kernel::completionsAt,
      onCellAction = viewModel::applyToCell,
      onCellFocusChanged = viewModel::cellFocusChanged,
      modifier = Modifier.padding(innerPadding),
    )
  }
}

@Composable
private fun EditorContent(
  state: EditorUiState,
  onRunCell: (Cell) -> Unit,
  onStopCell: () -> Unit,
  onEditCell: (String, String) -> Unit,
  completionsAt: suspend (String, Int) -> CompletionResult,
  onCellAction: (String, CellAction) -> Unit,
  onCellFocusChanged: (String, Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  when {
    state.error != null -> ErrorMessage(message = state.error, modifier = modifier)
    !state.loaded -> EmptyMessage("Loading\u2026", modifier)
    // No empty branch: a loaded notebook always has at least one cell, given one by
    // toEditorUiState if the file had none. A "No cells yet" message would advertise a state
    // that cannot happen, and used to be a dead end when it did.
    else ->
      LazyColumn(modifier = modifier.fillMaxSize()) {
        // contentType as well as key: a code cell and a markdown cell compose to very different
        // trees, and telling the list so lets it reuse each kind against its own pool instead of
        // rebuilding whenever the two alternate.
        items(state.cells, key = { it.id }, contentType = { it.kind }) { cell ->
          EditorCell(
            cell = cell,
            outputs = state.outputs[cell.id].orEmpty(),
            executionCount = state.executionCounts[cell.id],
            execution = state.executionOf(cell.id),
            canPaste = state.clipboard.isNotEmpty(),
            selected = state.selectedCellId == cell.id,
            mode = state.mode,
            onRun = { onRunCell(cell) },
            onStop = onStopCell,
            onSourceEdited = { source -> onEditCell(cell.id, source) },
            completionsAt = completionsAt,
            onCellAction = { action -> onCellAction(cell.id, action) },
            onFocusChanged = { focused -> onCellFocusChanged(cell.id, focused) },
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
  execution: CellExecution?,
  canPaste: Boolean,
  selected: Boolean,
  mode: CellMode,
  onRun: () -> Unit,
  onStop: () -> Unit,
  onSourceEdited: (String) -> Unit,
  completionsAt: suspend (String, Int) -> CompletionResult,
  onCellAction: (CellAction) -> Unit,
  onFocusChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Markdown never reaches a kernel: for those cells Run means "render", which is what running
  // one means in Jupyter too.
  val rendered = rememberMarkdownRendered(cell.id)
  val markdown = cell.kind == CellKind.MARKDOWN
  val stripe = cellStripe(
    selected = selected,
    mode = mode,
    status = execution?.status ?: ExecutionStatus.NOT_STARTED,
    palette = editorPalette,
  )
  CellFrame(stripe = stripe, modifier = modifier) {
    CellHeader(
      cell = cell,
      executionCount = executionCount,
      execution = execution,
      canPaste = canPaste,
      onRun = if (markdown) ({ rendered.value = true }) else onRun,
      onStop = onStop,
      onCellAction = onCellAction,
    )
    Spacer(modifier = Modifier.height(4.dp))
    // Wrapped so the focus listener sits on the *input area* rather than the whole cell, which
    // is the distinction JupyterLab draws in `_evtFocusIn`: focus anywhere in a cell selects it,
    // but only focus inside the editor means edit mode. Put on the Column, the header's buttons
    // would count as typing.
    Box(modifier = onCellInputFocus(onFocusChanged)) {
      if (cell.sourceHidden) {
        // Collapsed beats every other source state: there is nothing to type into, and the tap
        // that expands must not also land in a text field.
        CollapsedCell(
          preview = firstNonEmptyLine(cell.source),
          description = COLLAPSED_SOURCE,
          onExpand = { onCellAction(CellAction.ToggleSourceHidden) },
        )
      } else if (cell.kind == CellKind.CODE) {
        CodeCellField(
          source = cell.source,
          onSourceEdited = onSourceEdited,
          completionsAt = completionsAt,
          modifier = Modifier
            .fillMaxWidth()
            .background(editorPalette.cellBackground, MaterialTheme.shapes.small)
            .padding(8.dp),
        )
      } else {
        MarkdownCell(
          source = cell.source,
          rendered = rendered.value,
          onEditRequested = { rendered.value = false },
          onSourceEdited = onSourceEdited,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
    CellOutputs(
      outputs = outputs,
      hidden = cell.outputsHidden,
      onExpand = { onCellAction(CellAction.ToggleOutputsHidden) },
    )
  }
}

/** What each output kind becomes on screen is [RenderedOutput]'s decision, not this one's. */
@Composable
private fun CellOutputs(outputs: List<CellOutput>, hidden: Boolean, onExpand: () -> Unit) {
  val rendered = outputs.mapNotNull { it.render() }
  // Nothing to fold when there is nothing to show, so an empty cell looks the same either way.
  if (rendered.isEmpty()) return
  Spacer(modifier = Modifier.height(4.dp))
  if (hidden) {
    CollapsedCell(rendered.collapsedPreview(), COLLAPSED_OUTPUTS, onExpand)
    return
  }
  Column(modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Cell output" }) {
    rendered.forEach { output ->
      when (output) {
        is RenderedOutput.Text -> OutputText(output)
        is RenderedOutput.Image -> OutputImage(output)
        // The same renderer a markdown cell uses: `text/html` parses to the same blocks.
        is RenderedOutput.Document -> RichText(output.blocks, Modifier.fillMaxWidth())
      }
    }
  }
}

@Composable
private fun OutputText(output: RenderedOutput.Text) {
  if (!output.isError) {
    Text(
      text = output.text,
      style = MaterialTheme.typography.bodySmall,
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.onSurface,
    )
    return
  }
  // A traceback is long and needs to be findable at a glance, so it gets its own tinted
  // block rather than only a colour that a monospace wall of text swallows.
  Text(
    text = output.text,
    style = MaterialTheme.typography.bodySmall,
    fontFamily = FontFamily.Monospace,
    color = MaterialTheme.colorScheme.onErrorContainer,
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
      .padding(8.dp),
  )
}

@Composable
private fun OutputImage(output: RenderedOutput.Image) {
  // Decoded here rather than in RenderedOutput so that stays free of Android types and
  // unit-testable; remembered by content so scrolling does not re-decode every frame.
  val bitmap = remember(output) {
    BitmapFactory.decodeByteArray(output.png, 0, output.png.size)?.asImageBitmap()
  } ?: return
  Image(
    bitmap = bitmap,
    contentDescription = "Cell image output",
    modifier = Modifier.fillMaxWidth(),
    contentScale = ContentScale.Fit,
  )
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
