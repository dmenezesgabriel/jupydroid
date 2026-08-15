package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind

/**
 * Restructuring a notebook: insert above, change a cell's type, merge two, and cut/copy/paste.
 *
 * Beside [CellEdits] and under the same rules - pure, new ids passed in, a stale id is a no-op.
 * Semantics come from JupyterLab's `NotebookActions`; `CellRestructureTest` cites the line for
 * each, and several are not the obvious answer. Merge's inverse is next door in [CellSplit].
 */

fun EditorUiState.withCellInsertedAbove(cellId: String, kind: CellKind, newId: String): EditorUiState {
  val at = cells.indexOfFirst { it.id == cellId }
  if (at < 0) return this
  return copy(cells = cells.toMutableList().apply { add(at, Cell(newId, kind, "")) }, dirty = true)
}

/**
 * Turns a code cell into a markdown one or back.
 *
 * **The id survives** - `changeCellType` reinserts with `id: raw.id` (`actions.tsx:3282`) - and
 * **the outputs do not**: *"After change of type outputs are deleted"*. A markdown cell cannot
 * hold outputs at all, and a code cell's results stop describing its source the moment the source
 * changes meaning.
 */
fun EditorUiState.withCellsKindChanged(cellIds: List<String>, kind: CellKind): EditorUiState {
  val wanted = cellIds.toSet()
  val changing = cells.filter { it.id in wanted && it.kind != kind }.map { it.id }.toSet()
  if (changing.isEmpty()) return this
  return copy(
    cells = cells.map { if (it.id in changing) it.copy(kind = kind) else it },
    outputs = outputs - changing,
    executionCounts = executionCounts - changing,
    executions = executions - changing,
    dirty = true,
  )
}

/**
 * Folds a run of cells into one where it was, reaching for a neighbour only when the run is a
 * single cell.
 *
 * Four rules, all from `mergeCells` (`actions.tsx:345-470`):
 *
 * - **The direction is ignored once more than one cell is marked.** `mergeAbove` is consulted
 *   only inside `if (toMerge.length === 1)` (`:390-425`): with a run there is nothing to reach
 *   for, because the run *is* what merges.
 * - **The sources join with a blank line**, because `addExtraLine` defaults to true.
 * - **The first cell of the run decides the type.** `cell_type` comes from the *primary* cell, so
 *   folding markdown into code gives code and the reverse gives markdown.
 * - **Outputs go.** *"We can trust this cell because the outputs will be removed."*
 *
 * The merged cell takes a new id, and that asymmetry with [withCellsKindChanged] is the
 * reference's: merge inserts a model with no `id` field, so the shared model mints one.
 */
fun EditorUiState.withCellsMerged(cellIds: List<String>, into: MergeInto, newId: String): EditorUiState {
  val (folding, primary) = mergeSpan(cellIds, into) ?: return this
  val merged = Cell(
    id = newId,
    kind = cells[primary].kind,
    source = folding.joinToString("\n\n") { cells[it].source },
    preserved = cells[primary].preserved,
  )
  val gone = folding.map { cells[it].id }.toSet()
  return copy(
    cells = cells.toMutableList().apply {
      subList(folding.first, folding.last + 1).clear()
      add(folding.first, merged)
    },
    outputs = outputs - gone,
    executionCounts = executionCounts - gone,
    executions = executions - gone,
    dirty = true,
  )
}

/** Which cells fold together, and which of them decides the type of what comes out. */
private data class MergeSpan(val folding: IntRange, val primary: Int)

/**
 * The span a merge folds, or null when there is nothing to fold - a run of one at the end it is
 * reaching past, or ids that are all gone.
 *
 * The two cases are the reference's `if (toMerge.length === 1)` branch and its else
 * (`actions.tsx:390-425`): a run folds itself and ignores [into], one cell reaches for a neighbour.
 * The primary is the first cell of the *marked run* either way, which for a merge above is the
 * second of the two cells folding.
 */
private fun EditorUiState.mergeSpan(cellIds: List<String>, into: MergeInto): MergeSpan? {
  val span = spanOf(cellIds)
  if (span == null || span.first != span.last) return span?.let { MergeSpan(it, it.first) }
  val other = if (into == MergeInto.ABOVE) span.first - 1 else span.first + 1
  return if (other in cells.indices) {
    MergeSpan(minOf(span.first, other)..maxOf(span.first, other), span.first)
  } else {
    null
  }
}

/**
 * Puts a run of cells on the notebook's clipboard **in document order**, leaving the notebook as
 * it was - `Private.selectedCells` walks the widgets rather than the order they were marked in
 * (`actions.tsx:3103-3125`).
 */
fun EditorUiState.withCellsCopied(cellIds: List<String>): EditorUiState {
  val taken = cellsIn(cellIds)
  if (taken.isEmpty()) return this
  return copy(clipboard = taken, clipboardWasCut = false)
}

/** Copy, then delete - which is the order `copyOrCut(cut = true)` does it in. */
fun EditorUiState.withCellsCut(cellIds: List<String>, replacementId: String): EditorUiState {
  val taken = cellsIn(cellIds)
  if (taken.isEmpty()) return this
  return withCellsDeleted(cellIds, replacementId).copy(clipboard = taken, clipboardWasCut = true)
}

private fun EditorUiState.cellsIn(cellIds: List<String>): List<Cell> {
  val wanted = cellIds.toSet()
  return cells.filter { it.id in wanted }
}

/**
 * Drops the clipboard in below [cellId].
 *
 * **A pasted cell gets a fresh id, except a code cell that was cut** (`actions.tsx:1666-1677`).
 * That exception is not cosmetic: a cut-and-pasted code cell is the same cell moving, and keeping
 * its identity is what lets a running execution find it again. A *copied* cell cannot keep the id
 * the original still holds.
 *
 * [newIds] supplies one id per clipboard entry; the ones a cut code cell does not need are simply
 * unused, which keeps this function free of id generation like the rest of the file.
 */
fun EditorUiState.withClipboardPasted(cellId: String, newIds: List<String>): EditorUiState {
  val at = cells.indexOfFirst { it.id == cellId }
  if (at < 0 || clipboard.isEmpty()) return this
  val pasted = clipboard.mapIndexed { index, cell ->
    val keepsIdentity = clipboardWasCut && cell.kind == CellKind.CODE
    if (keepsIdentity) cell else cell.copy(id = newIds.getOrElse(index) { cell.id })
  }
  return copy(cells = cells.toMutableList().apply { addAll(at + 1, pasted) }, dirty = true)
}
