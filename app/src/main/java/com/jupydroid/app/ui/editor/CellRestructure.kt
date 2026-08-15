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
fun EditorUiState.withCellKindChanged(cellId: String, kind: CellKind): EditorUiState {
  val changing = cells.firstOrNull { it.id == cellId }?.takeIf { it.kind != kind } ?: return this
  return copy(
    cells = cells.map { if (it.id == changing.id) it.copy(kind = kind) else it },
    outputs = outputs - cellId,
    executionCounts = executionCounts - cellId,
    executions = executions - cellId,
    dirty = true,
  )
}

/**
 * Folds [cellId] together with the neighbour on the given side, into one cell where it was.
 *
 * Three rules, all from `mergeCells` (`actions.tsx:345-470`):
 *
 * - **The sources join with a blank line**, because `addExtraLine` defaults to true.
 * - **The cell you invoked it from decides the type.** `cell_type` is taken from the *primary*
 *   cell, so merging markdown into code gives code and the reverse gives markdown.
 * - **Outputs go.** *"We can trust this cell because the outputs will be removed."*
 *
 * The merged cell takes a new id, and that asymmetry with [withCellKindChanged] is the
 * reference's: merge inserts a model with no `id` field, so the shared model mints one.
 */
fun EditorUiState.withCellMerged(cellId: String, into: MergeInto, newId: String): EditorUiState {
  val at = cells.indexOfFirst { it.id == cellId }
  val other = if (into == MergeInto.ABOVE) at - 1 else at + 1
  if (at < 0 || other !in cells.indices) return this
  val first = cells[minOf(at, other)]
  val second = cells[maxOf(at, other)]
  val primary = cells[at]
  val merged = Cell(newId, primary.kind, "${first.source}\n\n${second.source}", preserved = primary.preserved)
  return copy(
    cells = cells.toMutableList().apply {
      set(minOf(at, other), merged)
      removeAt(maxOf(at, other))
    },
    outputs = outputs - first.id - second.id,
    executionCounts = executionCounts - first.id - second.id,
    executions = executions - first.id - second.id,
    dirty = true,
  )
}

/** Puts a cell on the notebook's clipboard, leaving the notebook as it was. */
fun EditorUiState.withCellCopied(cellId: String): EditorUiState {
  val cell = cells.firstOrNull { it.id == cellId } ?: return this
  return copy(clipboard = listOf(cell), clipboardWasCut = false)
}

/** Copy, then delete - which is the order `copyOrCut(cut = true)` does it in. */
fun EditorUiState.withCellCut(cellId: String, replacementId: String): EditorUiState {
  val cell = cells.firstOrNull { it.id == cellId } ?: return this
  return withCellDeleted(cellId, replacementId).copy(clipboard = listOf(cell), clipboardWasCut = true)
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
