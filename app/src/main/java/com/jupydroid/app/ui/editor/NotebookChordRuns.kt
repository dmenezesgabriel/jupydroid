package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The `Shift`+`Enter` and `Alt`+`Enter` run commands - `runSelected` plus the document move the
 * two transforms carry.
 *
 * Beside [NotebookCellRuns] rather than inside it for the same reason [NotebookHistory] is its
 * own class next door: these are two more commands over the same machinery, and the file counts
 * functions. Everything here is a `Private.runSelected` followed by one move
 * (`packages/notebook/src/actions.tsx:814-856`, `:864-903`) - the cells are claimed, the head is
 * moved by the transform, and the claimed cells run through the ordinary queue.
 *
 * ```
 * chords.runAndAdvance()   // Shift+Enter: run, head +1 - append under the caret at the last cell
 * ```
 */
internal class NotebookChordRuns(
  private val uiState: MutableStateFlow<EditorUiState>,
  private val scope: CoroutineScope,
  /**
   * What a chord's document edit owes - the debounced save, since the insert's own run may be a
   * markdown-only claim whose checkpoint never fires. See [NotebookKernelBindings.noteEdited].
   */
  private val noteEdited: () -> Unit,
  /** The run queue, [NotebookCellRuns.runCells] passed in - what runs the claimed cells stays one. */
  private val runCells: suspend (List<Cell>) -> Unit,
) {
  /** `notebook:run-and-advance` - `Shift`+`Enter`; see [EditorUiState.withRunAdvanced]. */
  fun runAndAdvance() = runSelectedChord(EditorUiState::withRunAdvanced)

  /** `notebook:run-and-insert` - `Alt`+`Enter`; see [EditorUiState.withRunInserted]. */
  fun runAndInsert() = runSelectedChord(EditorUiState::withRunInserted)

  /**
   * The body both chords share - the run, then the document move [transform] carries.
   *
   * **The run's cells are claimed before the selection moves**, [NotebookCellRuns.run]'s own
   * rule: the marked run is read off the selection `runSelected` is about to clear. And a
   * missing `activeCell` bails the whole command (`actions.tsx:820-822`, `:875-877`) before the
   * transform is asked anything.
   *
   * `recording(before)` makes the insert *the same undoable unit* the cell menu's Insert is -
   * *"The new cell creation can be undone"* (`:813`) - and [noteEdited] owes the debounced save
   * for it, since a run of nothing but markdown never reaches a checkpoint of its own.
   */
  private fun runSelectedChord(transform: EditorUiState.(String) -> EditorUiState) {
    val cells = uiState.value.cellsToRun(RunRange.MARKED)
    if (cells.isEmpty()) return
    val before = uiState.value
    uiState.update { it.transform(newCellId()).recording(before = it).withSearchRecomputed() }
    if (uiState.value.cells != before.cells) noteEdited()
    scope.launch { runCells(cells) }
  }
}
