package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.port.CompletionResult

/**
 * The notebook's own cell list, split out of [EditorScreen] for the same reason
 * [EditorTopBars]/[EditorBottomBars] were: a file-length gate, not a change in responsibility -
 * this is still [EditorScreen]'s own body, drawn by the `Scaffold` slot that used to hold it
 * inline.
 */
@Composable
internal fun EditorContent(
  state: EditorUiState,
  onRunCell: (Cell) -> Unit,
  onStopCell: () -> Unit,
  onEditCell: (String, String) -> Unit,
  /** A tap in a cell's gutter, reporting which cell and which one-based line it hit. */
  onToggleBreakpoint: (String, Int) -> Unit,
  /** A long-press on an armed line's gutter (P6a-16), confirmed through [BreakpointConditionDialog]. */
  onSetCondition: (cellId: String, line: Int, condition: String?) -> Unit,
  /** `(cellId, code, cursorOffset)` - the cell keys the language server's document (P5c). */
  completionsAt: suspend (cellId: String, code: String, cursorOffset: Int) -> CompletionResult,
  onCellAction: (String, CellAction, CellEditorText) -> Unit,
  onCellFocusChanged: (String, Boolean) -> Unit,
  onToggleSelected: (String) -> Unit,
  onMarkdownEditRequested: (String) -> Unit,
  cellInput: FocusedCellInput,
  /** `notebook:toggle-autoclosing-brackets`, universal - code cells only, like the reference's. */
  autoClosingBrackets: Boolean,
  /** `notebook:toggle-all-cell-line-numbers` - app-wide and persisted since P6c-2. */
  lineNumbersShown: Boolean,
  /** `docs/PLAN.md` P6c-4 - app-wide and persisted, see [codeSourceStyle]. */
  codeFontSizeSp: Int,
  /** `docs/PLAN.md` P6c-5 - app-wide and persisted, code cells only. See [EditorCell]. */
  wrapEnabled: Boolean,
  /** `docs/PLAN.md` P6c-6 - app-wide and persisted, code cells only. See [EditorCell]. */
  completionEnabled: Boolean,
  /** `toc:display-numbering` / `toc:display-h1-numbering` - see [MarkdownCell]. */
  tocNumbering: TocNumbering,
  /** Every heading in the notebook, numbered as one document - see [tableOfContentsHeadings]. */
  tocHeadings: List<TocHeading>,
  /** Hoisted so P2h-2's click-to-jump can drive the same list a tap in the table of contents. */
  listState: LazyListState,
  modifier: Modifier = Modifier,
) {
  // Which cell/line's condition is being edited, or null while the dialog is closed - ephemeral
  // UI state, not something `EditorUiState` itself needs to carry: nothing outside this composable
  // reads it, the same reason `settled`/`tocOpen`-shaped flags in this file's siblings stay local.
  var conditionPrompt by remember { mutableStateOf<Pair<String, Int>?>(null) }
  when {
    state.error != null -> ErrorMessage(message = state.error, modifier = modifier)
    !state.loaded -> EmptyMessage("Loading…", modifier)
    // No empty branch: a loaded notebook always has at least one cell, given one by
    // toEditorUiState if the file had none. A "No cells yet" message would advertise a state
    // that cannot happen, and used to be a dead end when it did.
    else -> {
      // Computed once for the whole list rather than per cell: `selectedCellIds` walks the cells
      // to find both ends, and asking it inside the item lambda would make drawing the notebook
      // quadratic in its length.
      val marked = state.selectedCellIds.toSet()
      // Walks the whole notebook, so it is computed once for the list rather than per item -
      // asking inside the item lambda would make drawing a notebook quadratic in its length.
      val onScreen = state.cellsOnScreen
      // Numbered once for the whole notebook rather than per cell, for the reason the two above
      // are: the numbers are the *document's* - one running counter over every heading - so asking
      // inside the item lambda would be both wrong and quadratic. Keyed by cell, so a heading a
      // code cell printed is counted but leaves no prefix for a markdown cell to draw.
      val headingPrefixes = tocHeadings.map { it.cellId }.zip(tocHeadings.tocPrefixes(tocNumbering)).toMap()
      // **Beside the notebook, never instead of it** - see [EditorUiState.saveFailure]. A failure to
      // save is a state that goes on being true, so it is a banner over the document rather than
      // the snackbar an export's outcome gets, and there is nothing to remember having shown.
      Column(modifier = modifier.fillMaxSize()) {
        state.saveFailure?.let { SaveFailureBanner(it) }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
          // contentType as well as key: a code cell and a markdown cell compose to very different
          // trees, and telling the list so lets it reuse each kind against its own pool instead of
          // rebuilding whenever the two alternate.
          itemsIndexed(onScreen, key = { _, cell -> cell.id }, contentType = { _, cell -> cell.kind }) { at, cell ->
            EditorCell(
              cell = cell,
              outputs = state.outputs[cell.id].orEmpty(),
              executionCount = state.executionCounts[cell.id],
              sourceRevision = state.sourceRevisions[cell.id] ?: 0,
              execution = state.executionOf(cell.id),
              canPaste = state.clipboard.isNotEmpty(),
              selected = state.selectedCellId == cell.id,
              // Null unless a run is being marked, which is what keeps the tick and the `⋮` from
              // ever being on screen together. The position is 1-based and is the screen reader's,
              // not the eye's - see [CellMark].
              mark = if (state.selecting) CellMark(cell.id in marked, position = at + 1) else null,
              markedCount = marked.size,
              // The reference's rule, read literally: the tint is on the cell node and the active
              // cell only joins it once the run holds more than one - `jp-mod-multiSelected` is
              // added at `count > 1` (`packages/notebook/src/widget.ts:2763-2765`).
              tinted = marked.size > 1 && cell.id in marked,
              mode = state.mode,
              onRun = { onRunCell(cell) },
              onStop = onStopCell,
              onSourceEdited = { source -> onEditCell(cell.id, source) },
              completionsAt = completionsAt,
              onCellAction = { action, editor -> onCellAction(cell.id, action, editor) },
              onFocusChanged = { focused -> onCellFocusChanged(cell.id, focused) },
              onToggleSelected = { onToggleSelected(cell.id) },
              // Rendered unless somebody opened this one - see [MarkdownEditing]. Read from the state
              // rather than remembered in the cell, because a run has to be able to close the editor.
              markdownRendered = state.isMarkdownRendered(cell.id),
              // Only the cell a tap asked for - a command that unrenders leaves the caret alone.
              markdownTakesCaret = state.markdownTakingCaret == cell.id,
              onMarkdownEditRequested = { onMarkdownEditRequested(cell.id) },
              // Only ever non-zero on a collapsed heading, which is the reference's own condition for
              // drawing the row at all (`packages/cells/src/widget.ts:2424-2427`).
              hiddenBelow = if (cell.headingCollapsed) state.headingSectionOf(cell.id).size else 0,
              // One flag for the whole notebook and all three cell types, which is what the command
              // is: `toggleAllLineNumbers` writes code, markdown and raw together.
              lineNumbersShown = lineNumbersShown,
              codeFontSizeSp = codeFontSizeSp,
              wrapEnabled = wrapEnabled,
              completionEnabled = completionEnabled,
              breakpoints = state.breakpointsByCellId[cell.id].orEmpty(),
              onGutterTap = { line -> onToggleBreakpoint(cell.id, line) },
              onGutterLongPress = { line -> conditionPrompt = cell.id to line },
              stoppedLine = state.stoppedLineOf(cell.id),
              headingPrefix = headingPrefixes[cell.id].orEmpty(),
              // Per cell rather than sliced once for the list, unlike the numbers above: a cell's
              // matches depend only on that cell - see [searchHighlightOf].
              search = state.searchHighlightOf(cell.id),
              outputSearch = state.outputSearchHighlightsOf(cell.id),
              autoClosingBrackets = autoClosingBrackets,
              cellInput = cellInput,
            )
          }
        }
      }
    }
  }
  conditionPrompt?.let { (cellId, line) ->
    val existing = state.breakpointsByCellId[cellId]?.firstOrNull { it.line == line }?.condition
    BreakpointConditionDialog(
      currentCondition = existing,
      onConfirm = { condition ->
        onSetCondition(cellId, line, condition)
        conditionPrompt = null
      },
      onDismiss = { conditionPrompt = null },
    )
  }
}
