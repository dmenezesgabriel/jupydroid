package com.jupydroid.app.domain.model

/**
 * Breakpoints set on any open notebook's cells, kept for the life of the app rather than one
 * notebook's `EditorViewModel`.
 *
 * **A store of its own, not a field on `NotebookDebugger` - because `EditorViewModel` is rebuilt
 * for the same notebook.** `NotebookBackStack` reveals rather than reopens, and each `NavEntry`
 * owns its own `ViewModelStore`, so re-opening a notebook after navigating away builds a brand new
 * `EditorViewModel` and every one of its collaborators. Mirrors [ActiveCellRuns]'/[ActiveTerminals]'
 * reasoning: what this answers - "which lines has this cell had breakpoints on" - spans the app's
 * life, not one screen's.
 *
 * Keyed by cell id rather than notebook id: a cell id is already unique across every open
 * notebook, so nesting a second key would answer a question nothing asks.
 *
 * Owned by `AppContainer`, moved by `NotebookDebugger`.
 *
 * ```
 * val breakpoints = NotebookBreakpoints()
 * breakpoints.toggle("cell-1", line = 3) // armed
 * breakpoints.toggle("cell-1", line = 3) // and gone again
 * ```
 */
class NotebookBreakpoints {
  private val byCell = mutableMapOf<String, List<Breakpoint>>()

  /**
   * Cells that have had a breakpoint at least once, kept even once [forCell] goes back to empty -
   * debugpy keeps a line paused in its own memory until a `setBreakpoints` call
   * tells it otherwise; a cell this app never armed can carry no such stale state, but one that was
   * armed and then cleared can, so [NotebookDebugger][com.jupydroid.app.ui.editor.NotebookDebugger]
   * still owes it a clearing round trip even with nothing left in [forCell].
   */
  private val everArmedCells = mutableSetOf<String>()

  fun forCell(cellId: String): List<Breakpoint> = byCell[cellId].orEmpty()

  /** Whether [cellId] has ever carried a breakpoint, even if none does now. */
  fun everArmed(cellId: String): Boolean = cellId in everArmedCells

  /** Adds a breakpoint at [line] if none is there yet, or removes it if one already is. */
  fun toggle(cellId: String, line: Int): List<Breakpoint> {
    val current = forCell(cellId)
    val updated = if (current.any { it.line == line }) {
      current.filterNot { it.line == line }
    } else {
      everArmedCells += cellId
      current + Breakpoint(line = line)
    }
    byCell[cellId] = updated
    return updated
  }

  /** Overwrites [cellId]'s whole list - what a `setBreakpoints` reply does, not an add/remove. */
  fun replace(cellId: String, breakpoints: List<Breakpoint>) {
    byCell[cellId] = breakpoints
  }

  /**
   * Attaches [condition] to [cellId]'s breakpoint at [line], replacing whatever condition
   * it carried before - `null` clears it back to an ordinary breakpoint. A no-op, [toggle]'s own
   * "nothing to touch" shape, when [line] carries no breakpoint at all.
   */
  fun setCondition(cellId: String, line: Int, condition: String?): List<Breakpoint> {
    val updated = forCell(cellId).map { if (it.line == line) it.copy(condition = condition) else it }
    byCell[cellId] = updated
    return updated
  }
}
