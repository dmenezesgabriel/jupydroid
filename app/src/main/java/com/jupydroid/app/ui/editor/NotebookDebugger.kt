package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.model.DebugStop
import com.jupydroid.app.domain.model.DebugVariableGroup
import com.jupydroid.app.domain.model.NotebookBreakpoints
import com.jupydroid.app.domain.port.DebugEvent
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
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
   *
   * @return whether a debug session actually attached - **[NotebookCellRuns]'s own signal for
   *   whether [watchForStop] has anything to watch.** `BaseSpaceKernelDebugger.events` throws
   *   [com.jupydroid.app.domain.port.StaleKernelSessionException] for a session that was never
   *   attached, and until this returned a value there was nothing stopping [NotebookCellRuns] from
   *   launching [watchForStop] anyway - crashing the app on every ordinary run, found on a tablet
   *   in P6a-8's own Maestro run.
   */
  suspend fun startSession(session: KernelSession, cell: Cell): Boolean {
    val armed = store.forCell(cell.id)
    if (armed.isEmpty()) return false
    kernelDebugger.attach(session)
    val codeId = kernelDebugger.dumpCell(session, cell.source)
    val verified = kernelDebugger.setBreakpoints(session, codeId, armed.map { it.line })
    kernelDebugger.configurationDone(session)
    val updated = verified.map { Breakpoint(line = it.line, verified = it.verified, codeId = codeId) }
    store.replace(cell.id, updated)
    uiState.update { it.copy(breakpointsByCellId = it.breakpointsByCellId + (cell.id to updated)) }
    return true
  }

  /** [session]'s own `debug_event`s - a thin pass-through, since [NotebookCellRuns] holds no [KernelDebugger]. */
  fun events(session: KernelSession): Flow<DebugEvent> = kernelDebugger.events(session)

  /**
   * Follows [session]'s debug events for as long as this is collected, reflecting a [DebugEvent.Stopped]
   * into [EditorUiState.stoppedAt]/[EditorUiState.debugStack] and clearing both on [DebugEvent.Continued].
   *
   * **Runs until the caller cancels it**, the same shape as any other subscription onto a hot
   * flow: [NotebookCellRuns] owns how long "for the length of this run" is.
   */
  suspend fun watchForStop(session: KernelSession, cell: Cell) {
    events(session).collect { event ->
      val resolved = resolvedStop(session, cell, event)
      uiState.update { it.copy(stoppedAt = resolved?.first, debugStack = resolved?.second) }
    }
  }

  /**
   * `stackTrace -> scopes -> variables`, DAP's own dependency order (P6a-8a) - `null` on
   * [DebugEvent.Continued] or when a `Stopped` thread's stack came back empty. Only the **top**
   * frame's scopes/variables are fetched, matching JupyterLab's own default selection on a fresh
   * stop (`packages/debugger/src/panels/callstack/model.ts:31-44`) - [DebugStack]'s own doc names
   * why refocusing another frame is not built here.
   */
  private suspend fun resolvedStop(
    session: KernelSession,
    cell: Cell,
    event: DebugEvent,
  ): Pair<DebugStop, DebugStack>? {
    val stopped = event as? DebugEvent.Stopped ?: return null
    val frames = kernelDebugger.stackTrace(session, stopped.threadId)
    return frames.firstOrNull()?.let { top ->
      val stop = DebugStop(cellId = cell.id, line = top.line, threadId = stopped.threadId)
      stop to DebugStack(frames, variableGroups(session, top.id))
    }
  }

  private suspend fun variableGroups(session: KernelSession, frameId: Int): List<DebugVariableGroup> =
    kernelDebugger.scopes(session, frameId).map { scope ->
      DebugVariableGroup(scope = scope.name, variables = kernelDebugger.variables(session, scope.variablesReference))
    }
}
