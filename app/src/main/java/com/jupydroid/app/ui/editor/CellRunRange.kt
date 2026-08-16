package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell

/**
 * Which cells each run command works through, as pure functions over [EditorUiState].
 *
 * Beside [CellRangeSelection], which answers the same shape of question for the cell commands, and
 * split from it because these are ranges the *kernel* is asked about rather than cells a menu acts
 * on. Both derive their answer from one head - [EditorUiState.selectedCellId], JupyterLab's
 * `activeCellIndex` - and neither stores a range of its own.
 *
 * **The whole document range, markdown included.** `Private.runCells` is handed
 * `notebook.widgets` and decides per cell what running one means - a code cell reaches the kernel,
 * a markdown cell is rendered (`packages/notebook/src/cellexecutor.ts`, `case 'markdown'`). So the
 * filtering is [NotebookCellRuns]'s and not this file's, and a range stays a fact about the
 * document.
 */

/**
 * The cells [RunRange] names, in document order.
 *
 * Each branch carries the reference line that settles it, because two of the five are not what
 * their names suggest.
 *
 * ```
 * state.cellsToRun(RunRange.ABOVE)   // everything before the head, and not the head
 * ```
 */
fun EditorUiState.cellsToRun(range: RunRange): List<Cell> {
  val head = cells.indexOfFirst { it.id == selectedCellId }
  // No head, no range - the reference bails on a missing `activeCell` in all four of the
  // head-relative ones (`actions.tsx:1008`, `:1053`, `notebook-extension/src/index.ts:3269`).
  // [RunRange.ALL] is exempt and that is a departure: `runAll` bails too (`actions.tsx:946`),
  // while ours is a button on the notebook's own bar, reachable before any cell has been touched.
  // Refusing there would be a control that lights up and does nothing.
  if (range != RunRange.ALL && head < 0) return emptyList()
  return when (range) {
    // Every widget, markdown included (`actions.tsx:955-961`).
    RunRange.ALL -> cells
    // `slice(0, activeCellIndex)`, and `activeCellIndex < 1` returns early (`actions.tsx:1005-1045`).
    RunRange.ABOVE -> cells.subList(0, head)
    // `slice(activeCellIndex)` - inclusive, which is what *"Run Selected Cell and All Below"* says
    // out loud (`actions.tsx:1047-1090`).
    RunRange.BELOW -> cells.subList(head, cells.size)
    // `slice(0, activeCellIndex + 1)` (`packages/notebook-extension/src/index.ts:3273`).
    RunRange.UP_TO_SELECTED -> cells.subList(0, head + 1)
    // `Private.runSelected` filters `isSelectedOrActive` (`actions.tsx:3009-3040`), which is what
    // [selectedCellIds] already is - so an unmarked notebook gives the head alone, exactly as
    // every cell command already behaves.
    RunRange.MARKED -> selectedCellIds.toSet().let { marked -> cells.filter { it.id in marked } }
  }
}

/**
 * `notebook:run-all-above`'s `isEnabled` (`packages/notebook-extension/src/index.ts:3082-3089`).
 *
 * The same fact as [RunRange.ABOVE] being empty, stated separately because the menu has to grey
 * the row *before* anyone taps it - a row that lights up and does nothing teaches the user that
 * the menu lies.
 *
 * The reference's `isEnabledAndSingleSelected` half needs no reading here: [NotebookBar] is
 * replaced by [SelectionTopBar] the moment a run is marked, so this menu is only ever open over a
 * single selected cell.
 */
val EditorUiState.canRunAllAbove: Boolean
  get() = cells.indexOfFirst { it.id == selectedCellId } > 0

/**
 * `notebook:run-all-below`'s `isEnabled` (`index.ts:3113-3119`), and **not** simply
 * [RunRange.BELOW] being non-empty.
 *
 * On the last cell of a notebook the range is that one cell and the command is still refused,
 * because *"Run Selected Cell and All Below"* with nothing below it is the cell's own Run button
 * under a longer name. The exception the reference spells out is `widgets.length === 1`, where the
 * only cell is both the first and the last and the command is perfectly meaningful.
 */
val EditorUiState.canRunSelectedAndBelow: Boolean
  get() {
    val head = cells.indexOfFirst { it.id == selectedCellId }
    return head >= 0 && (cells.size == 1 || head != cells.lastIndex)
  }
