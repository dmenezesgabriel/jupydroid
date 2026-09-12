package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.NotebookBreakpoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The gutter half of the debugger (P6a-6): breakpoints toggled and shown, nothing sent to a
 * kernel yet - that starts at P6a-7a. Beside [NotebookKernel]/[NotebookCellEdits] on
 * [EditorViewModel], and following the same split: this only touches
 * [EditorUiState.breakpointsByCellId], nothing else a cell operation or a run cares about.
 *
 * **Backed by [store], not by a field here.** `EditorViewModel` is rebuilt for the same notebook
 * whenever `NotebookBackStack` reveals it again, so anything kept only on this object or on the
 * state flow it shares would reset on every re-open. [store] is [store][NotebookBreakpoints]'s
 * whole reason to exist.
 *
 * ```
 * val debugger = NotebookDebugger(uiState, notebookBreakpoints)
 * debugger.toggle("c1", line = 3)
 * ```
 */
class NotebookDebugger(private val uiState: MutableStateFlow<EditorUiState>, private val store: NotebookBreakpoints) {
  /** Arms [line] of [cellId] if it carries no breakpoint yet, or disarms it if it already does. */
  fun toggle(cellId: String, line: Int) {
    val updated = store.toggle(cellId, line)
    uiState.update { it.copy(breakpointsByCellId = it.breakpointsByCellId + (cellId to updated)) }
  }

  /** Restores what [store] already held for [cells], so re-opening a notebook shows it again. */
  fun seed(cells: List<Cell>) {
    val seeded = cells.associate { it.id to store.forCell(it.id) }.filterValues { it.isNotEmpty() }
    uiState.update { it.copy(breakpointsByCellId = seeded) }
  }
}
