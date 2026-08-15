package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind

/**
 * What the cell menu offers, as data.
 *
 * Split out of [CellActions] on detekt's `TooManyFunctions`, and detekt was pointing at a real
 * seam rather than at a count: the surface beside it draws *any* list of labelled things to do,
 * while this is the one list of cell actions - and P2c gave it a third caller
 * ([SelectionTopBar]'s overflow) with a different argument from the other two.
 */

/**
 * Ordered the way the actions are reached for: adding, then re-ordering, then destroying - so
 * Delete is never next to the thing a user opened the menu to do.
 *
 * Collapse is offered on **code cells only**, which is JupyterLab's rule: every one of its
 * collapse actions checks `cell.model.type === 'code'`
 * (`packages/notebook/src/actions.tsx:1877`, `:1957`). A markdown cell already has its own
 * two states - rendered and source - and a third way to hide it would fight them.
 *
 * The label says what tapping will do rather than what the cell is, so the menu reads as a
 * verb list throughout.
 *
 * Internal rather than private because it is the one list three surfaces are built from: the
 * menu, the sheet, and the cell's accessibility context menu ([Modifier.cellCustomActions]).
 */
internal fun cellActionItems(
  cell: Cell,
  canPaste: Boolean,
  /** Whether a run of cells is already marked, which is the one row that then makes no sense. */
  selecting: Boolean,
): List<Pair<String, CellAction>> =
  selectingItems(selecting) + addingItems() + shapingItems(cell) + clipboardItems(canPaste) +
    foldingItems(cell) + removingItems()

/**
 * First, because it changes what every row below it will act on rather than doing something to
 * this cell - and because a list is read from the top, which is where "do this to several" has to
 * be if it is to be found at all.
 *
 * Absent once a run is marked: taking it there would drop the anchor back onto the head and
 * collapse the selection to one cell, which is a row that undoes what got you to it.
 */
private fun selectingItems(selecting: Boolean): List<Pair<String, CellAction>> =
  if (selecting) emptyList() else listOf(SELECT_CELLS to CellAction.StartSelecting)

private fun addingItems(): List<Pair<String, CellAction>> = listOf(
  "Insert code above" to CellAction.InsertAbove(CellKind.CODE),
  "Insert code below" to CellAction.Insert(CellKind.CODE),
  "Insert markdown below" to CellAction.Insert(CellKind.MARKDOWN),
)

private fun shapingItems(cell: Cell): List<Pair<String, CellAction>> = listOf(
  "Move up" to CellAction.Move(CellMove.UP),
  "Move down" to CellAction.Move(CellMove.DOWN),
  "Duplicate" to CellAction.Duplicate,
  // The type the cell is *not*, so the item reads as the change rather than as the state.
  if (cell.kind == CellKind.CODE) {
    CHANGE_TO_MARKDOWN to CellAction.ChangeKind(CellKind.MARKDOWN)
  } else {
    CHANGE_TO_CODE to CellAction.ChangeKind(CellKind.CODE)
  },
  "Merge with above" to CellAction.Merge(MergeInto.ABOVE),
  "Merge with below" to CellAction.Merge(MergeInto.BELOW),
  // Beside the merges, because split is their inverse and the three read as one group.
  //
  // **On every cell, and whether or not a caret was ever put in one.** Both are the reference's
  // `isEnabled: !!current.content.activeCell?.editor`: a markdown cell keeps its editor while it
  // is rendered - `renderInput`/`showEditor` swap only which widget is displayed
  // (`packages/cells/src/widget.ts:2489-2503`) - and `editor.getSelections()` answers with the
  // last cursor, or 0 for an editor nobody has touched. Splitting there puts an empty cell above,
  // and an undo takes it back.
  "Split cell" to CellAction.Split,
)

private fun clipboardItems(canPaste: Boolean): List<Pair<String, CellAction>> = buildList {
  add("Cut" to CellAction.Cut)
  add("Copy" to CellAction.Copy)
  // Offered only when there is something to paste. An item that silently does nothing teaches
  // the user that the menu lies, which is worse than the item being absent.
  if (canPaste) add("Paste below" to CellAction.Paste)
}

private fun foldingItems(cell: Cell): List<Pair<String, CellAction>> = buildList {
  if (cell.kind != CellKind.CODE) return@buildList
  add((if (cell.sourceHidden) EXPAND_CODE else COLLAPSE_CODE) to CellAction.ToggleSourceHidden)
  add((if (cell.outputsHidden) EXPAND_OUTPUTS else COLLAPSE_OUTPUTS) to CellAction.ToggleOutputsHidden)
}

private fun removingItems(): List<Pair<String, CellAction>> = listOf(
  // Beside folding them, because the two read as a pair and are not: this one throws the
  // outputs away, the one above only folds them.
  "Clear outputs" to CellAction.ClearOutputs,
  "Delete" to CellAction.Delete,
)

internal const val SELECT_CELLS = "Select cells"

internal const val CHANGE_TO_MARKDOWN = "Change to markdown"
internal const val CHANGE_TO_CODE = "Change to code"

internal const val COLLAPSE_CODE = "Collapse code"
internal const val EXPAND_CODE = "Expand code"
internal const val COLLAPSE_OUTPUTS = "Collapse outputs"
internal const val EXPAND_OUTPUTS = "Expand outputs"
