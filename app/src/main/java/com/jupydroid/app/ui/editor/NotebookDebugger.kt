package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.NotebookBreakpoints
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The gutter half of the debugger (P6a-6), plus the handshake that gets a cell's breakpoints in
 * front of a real kernel (P6a-7a). Beside [NotebookKernel]/[NotebookCellEdits] on
 * [EditorViewModel], and following the same split: this only touches
 * [EditorUiState.breakpointsByCellId], nothing else a cell operation or a run cares about.
 *
 * **Backed by [store], not by a field here.** `EditorViewModel` is rebuilt for the same notebook
 * whenever `NotebookBackStack` reveals it again, so anything kept only on this object or on the
 * state flow it shares would reset on every re-open. [store] is [store][NotebookBreakpoints]'s
 * whole reason to exist.
 *
 * ```
 * val debugger = NotebookDebugger(uiState, notebookBreakpoints, kernelDebugger)
 * debugger.toggle("c1", line = 3)
 * ```
 */
class NotebookDebugger(
  private val uiState: MutableStateFlow<EditorUiState>,
  private val store: NotebookBreakpoints,
  private val kernelDebugger: KernelDebugger,
) {
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

  /**
   * The debug-session handshake for [cell] on [session]: `attach -> dumpCell -> setBreakpoints ->
   * configurationDone`, [KernelDebugger]'s own four calls for readying one cell's breakpoints. A
   * cell with none armed has nothing for a kernel to verify, so nothing is sent - the same reason
   * JupyterLab's own debug session only ever arms lines a user actually marked.
   *
   * The reply is what turns [Breakpoint.verified] real: [store] and [uiState] are rewritten from
   * [KernelDebugger.setBreakpoints]'s own answer, not merely from what was asked for.
   */
  suspend fun startSession(session: KernelSession, cell: Cell) {
    val armed = store.forCell(cell.id)
    if (armed.isEmpty()) return
    kernelDebugger.attach(session)
    val codeId = kernelDebugger.dumpCell(session, cell.source)
    val verified = kernelDebugger.setBreakpoints(session, codeId, armed.map { it.line })
    kernelDebugger.configurationDone(session)
    val updated = verified.map { Breakpoint(line = it.line, verified = it.verified, codeId = codeId) }
    store.replace(cell.id, updated)
    uiState.update { it.copy(breakpointsByCellId = it.breakpointsByCellId + (cell.id to updated)) }
  }
}
