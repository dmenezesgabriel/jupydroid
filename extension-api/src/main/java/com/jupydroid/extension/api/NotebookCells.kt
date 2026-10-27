package com.jupydroid.extension.api

/**
 * The open notebook's cells, handed to an extension so it can read where the notebook is and put a
 * cell into it - what jupyterlab-ai-commands@2743d32 `src/notebook-commands.ts` reads and writes for
 * `get-notebook-info` (path, cell ids and types, the active cell) and `add-cell`, and what a chat's
 * Insert needs, which is jupyter-chat's `IActiveCellManager` (jupyter-chat@2c224bc
 * `packages/jupyter-chat/src/active-cell-manager.ts`) cut to the one insert both it and VS Code
 * offer (vscode@3f3380c `src/vs/workbench/contrib/chat/browser/actions/codeBlockOperations.ts`).
 *
 * The insert takes an index, as VS Code's `NotebookEdit.insertCells(index, …)` does and as `add-cell`
 * turns its reference cell into before inserting; a caller that holds a cell id finds its index in
 * [cells].
 *
 * ```
 * val cells = host.notebookCells
 * TextButton(onClick = { cells.insertBelow(code) }, enabled = cells.available) { Text("Insert") }
 * val id = host.notebookCells.insert(0, NotebookCellType.CODE, "print(42)")
 * ```
 */
interface NotebookCells {
  /** Whether a notebook is open with an active cell, read as Compose state so a button follows it. */
  val available: Boolean

  /** The open notebook's path in the workspace, as the server's REST API takes it; null with none open. */
  val path: String?

  /** The open notebook's cells in order, read when asked rather than when the notebook opened. */
  val cells: List<NotebookCellSummary>

  /** The active cell's id, or null when no notebook is open or none of its cells is active. */
  val activeCellId: String?

  /**
   * Puts a cell of [type] holding [source] at [index], from 0 above the first cell to the cell count
   * below the last, and makes it the active cell.
   *
   * @return the new cell's id, or null when no notebook is open.
   * @throws IllegalArgumentException when [index] is outside 0 to the cell count.
   */
  fun insert(index: Int, type: NotebookCellType, source: String): String?

  /** Puts [code] in a new code cell below the active one, which then becomes active; nothing with no active cell. */
  fun insertBelow(code: String) {
    val active = cells.indexOfFirst { it.id == activeCellId }
    if (active < 0) return
    insert(active + 1, NotebookCellType.CODE, code)
  }
}

/** One cell as [NotebookCells] lists it: `get-notebook-info`'s `{cellId, cellType}`. */
data class NotebookCellSummary(val id: String, val type: NotebookCellType)

/** nbformat's three `cell_type`s. */
enum class NotebookCellType { CODE, MARKDOWN, RAW }
