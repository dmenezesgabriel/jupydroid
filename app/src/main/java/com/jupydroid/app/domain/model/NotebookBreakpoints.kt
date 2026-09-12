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

  fun forCell(cellId: String): List<Breakpoint> = byCell[cellId].orEmpty()

  /** Adds a breakpoint at [line] if none is there yet, or removes it if one already is. */
  fun toggle(cellId: String, line: Int): List<Breakpoint> {
    val current = forCell(cellId)
    val updated = if (current.any { it.line == line }) {
      current.filterNot { it.line == line }
    } else {
      current + Breakpoint(line = line)
    }
    byCell[cellId] = updated
    return updated
  }

  /** Overwrites [cellId]'s whole list - what a `setBreakpoints` reply does, not an add/remove. */
  fun replace(cellId: String, breakpoints: List<Breakpoint>) {
    byCell[cellId] = breakpoints
  }
}
