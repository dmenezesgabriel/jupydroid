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
  /**
   * How many cells the rows will act on, which decides how they read.
   *
   * The reference pluralises with `trans._n` and only where the count changes the noun -
   * `'Delete Cell'`/`'Delete Cells'` (`packages/notebook-extension/src/index.ts:3733`) - while
   * `'Clear Cell Output'` and `'Collapse Selected Code'` have one form each (`:3365`, `:4741`).
   */
  count: Int = 1,
): List<Pair<String, CellAction>> {
  val several = count > 1
  return selectingItems(selecting) + addingItems() + shapingItems(cell, several) +
    clipboardItems(canPaste, several) + foldingItems(cell) + removingItems(several)
}

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

private fun shapingItems(cell: Cell, several: Boolean): List<Pair<String, CellAction>> = listOf(
  (if (several) "Move cells up" else "Move up") to CellAction.Move(CellMove.UP),
  (if (several) "Move cells down" else "Move down") to CellAction.Move(CellMove.DOWN),
  (if (several) "Duplicate cells" else "Duplicate") to CellAction.Duplicate,
) + changeKindItems(cell, several) + mergeItems(several) + listOf(
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

/**
 * The two types the cell is **not**, so each row reads as the change rather than as the state.
 * The head cell decides which two, and the whole run follows it.
 *
 * **Two rows and not one**, because there are three cell types and the reference has a command per
 * type: `notebook:change-cell-to-{code,markdown,raw}` (`packages/notebook-extension/src/index.ts:212-214`).
 * A single row could only name one of the two, and the other would be unreachable from the menu.
 *
 * In [CellKind]'s own order, so the pair a cell offers keeps its relative order whatever the cell
 * is - the row for a given type does not move about as the head changes.
 */
private fun changeKindItems(cell: Cell, several: Boolean): List<Pair<String, CellAction>> =
  CellKind.entries.filter { it != cell.kind }.map { kind -> changeKindItem(kind, several) }

/**
 * Exhaustive rather than derived from [CellKind.label], which is the header's `[ ]`/`markdown`
 * notation and not menu copy - and exhaustive for [transformed]'s reason: a fourth cell type has
 * to decide how it reads here, not inherit a spelling.
 */
private fun changeKindItem(kind: CellKind, several: Boolean): Pair<String, CellAction> = when (kind) {
  CellKind.CODE -> if (several) "Change cells to code" else CHANGE_TO_CODE
  CellKind.MARKDOWN -> if (several) "Change cells to markdown" else CHANGE_TO_MARKDOWN
  CellKind.RAW -> if (several) "Change cells to raw" else CHANGE_TO_RAW
} to CellAction.ChangeKind(kind)

/**
 * Two rows for one cell and **one** for a run, because the direction stops applying: `mergeCells`
 * consults `mergeAbove` only inside `if (toMerge.length === 1)`
 * (`packages/notebook/src/actions.tsx:390-425`), so both rows would do the same thing and one of
 * them would be lying about which.
 *
 * The single row is not invented to cover that up - it is the reference's own third merge command,
 * `notebook:merge-cells`, labelled *"Merge Selected Cells"*
 * (`packages/notebook-extension/src/index.ts:3953`). Which [MergeInto] it carries is arbitrary and
 * unread, exactly as `mergeAbove`'s default is there.
 */
private fun mergeItems(several: Boolean): List<Pair<String, CellAction>> = when {
  several -> listOf(MERGE_SELECTED to CellAction.Merge(MergeInto.ABOVE))
  else -> listOf(
    "Merge with above" to CellAction.Merge(MergeInto.ABOVE),
    "Merge with below" to CellAction.Merge(MergeInto.BELOW),
  )
}

private fun clipboardItems(canPaste: Boolean, several: Boolean): List<Pair<String, CellAction>> = buildList {
  add((if (several) "Cut cells" else "Cut") to CellAction.Cut)
  add((if (several) "Copy cells" else "Copy") to CellAction.Copy)
  // Offered only when there is something to paste. An item that silently does nothing teaches
  // the user that the menu lies, which is worse than the item being absent.
  if (canPaste) add("Paste below" to CellAction.Paste)
}

private fun foldingItems(cell: Cell): List<Pair<String, CellAction>> = buildList {
  if (cell.kind != CellKind.CODE) return@buildList
  add((if (cell.sourceHidden) EXPAND_CODE else COLLAPSE_CODE) to CellAction.ToggleSourceHidden)
  add((if (cell.outputsHidden) EXPAND_OUTPUTS else COLLAPSE_OUTPUTS) to CellAction.ToggleOutputsHidden)
}

private fun removingItems(several: Boolean): List<Pair<String, CellAction>> = listOf(
  // Beside folding them, because the two read as a pair and are not: this one throws the
  // outputs away, the one above only folds them. One form whatever the count, as
  // `'Clear Cell Output'` has there - its caption already says "for the selected cells".
  "Clear outputs" to CellAction.ClearOutputs,
  (if (several) "Delete cells" else "Delete") to CellAction.Delete,
)

internal const val SELECT_CELLS = "Select cells"

/** `notebook:merge-cells`, in this app's sentence case. */
internal const val MERGE_SELECTED = "Merge selected cells"

internal const val CHANGE_TO_MARKDOWN = "Change to markdown"
internal const val CHANGE_TO_CODE = "Change to code"
internal const val CHANGE_TO_RAW = "Change to raw"

internal const val COLLAPSE_CODE = "Collapse code"
internal const val EXPAND_CODE = "Expand code"
internal const val COLLAPSE_OUTPUTS = "Collapse outputs"
internal const val EXPAND_OUTPUTS = "Expand outputs"
