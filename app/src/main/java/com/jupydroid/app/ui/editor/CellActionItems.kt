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
  /**
   * Whether a session is there to answer [SYMBOL_INFO] - the [canPaste] rule verbatim: an item
   * that could only ever do nothing teaches the user the menu lies, and a hover with no
   * language server behind it is exactly that.
   */
  symbolInfo: Boolean = false,
): List<Pair<String, CellAction>> {
  val several = count > 1
  return selectingItems(selecting) + inspectingItems(cell, symbolInfo) + addingItems() +
    shapingItems(cell, several) + clipboardItems(canPaste, several) + foldingItems(cell) +
    removingItems(several)
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

/**
 * The two heading rows are in this group and not the change-type one, because that is what they do:
 * they add a cell rather than convert this one.
 *
 * **"Below" is the odd one and its label says so.** `notebook:insert-cell-below` puts a cell after
 * this one; `notebook:insert-heading-below` puts a heading at the end of the *section*
 * (`packages/notebook/src/actions.tsx:1208-1231`), so a row reading "Insert heading below" beside
 * "Insert code below" would promise the same place and mean a different one. The reference's own
 * labels are *"Insert Heading Above/Below"* (`notebook-extension/src/index.ts:4120,4137`); these
 * name the section instead, which is the adaptation and what it departs from.
 */
private fun addingItems(): List<Pair<String, CellAction>> = listOf(
  "Insert code above" to CellAction.InsertAbove(CellKind.CODE),
  "Insert code below" to CellAction.Insert(CellKind.CODE),
  "Insert markdown below" to CellAction.Insert(CellKind.MARKDOWN),
  INSERT_HEADING_ABOVE to CellAction.InsertHeading(SectionReach.ABOVE),
  INSERT_HEADING_BELOW to CellAction.InsertHeading(SectionReach.BELOW),
)

private fun shapingItems(cell: Cell, several: Boolean): List<Pair<String, CellAction>> = listOf(
  (if (several) "Move cells up" else "Move up") to CellAction.Move(CellMove.UP),
  (if (several) "Move cells down" else "Move down") to CellAction.Move(CellMove.DOWN),
  (if (several) "Duplicate cells" else "Duplicate") to CellAction.Duplicate,
) + changeKindItems(cell, several) + listOf(CHANGE_TO_HEADING to CellAction.ChooseHeadingLevel) +
  mergeItems(several) + listOf(
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
  // One fixed label whatever the state - the reference's own choice for this exact surface: its
  // menu row is registered with `args: {isMenu: true}`, which forces "Enable Scrolling for
  // Outputs" regardless of `isToggled` (`notebook-extension/src/index.ts:4963-4967`,
  // `tracker.json:328-335`). A tap can still *disable* an already-scrolled run - that is literal
  // parity with the reference's own row, not this app's usual flip-with-state label.
  add(ENABLE_OUTPUT_SCROLLING to CellAction.ToggleOutputsScrolled)
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

/**
 * The six `notebook:change-cell-to-heading-1..6` commands, flattened.
 *
 * What a surface with no room problem shows in place of [CHANGE_TO_HEADING] and its picker: the
 * cell menu is eighteen rows already, but a TalkBack context menu is a flat list with no width and
 * no fold, and a submenu is the worst shape there is to operate with one.
 *
 * The reference has no row for any of them - all six are command-palette entries
 * (`packages/notebook-extension/src/index.ts:5334-5339`) - so both shapes are adaptations, and this
 * is the one that departs least.
 */
internal fun headingLevelItems(): List<Pair<String, CellAction>> =
  (1..MARKDOWN_HEADING_LEVELS).map { level -> "Heading $level" to CellAction.ChangeToHeading(level) }

/**
 * One row for six commands, with the ellipsis that says it will ask.
 *
 * Beside the two change-type rows because it is the third of them - `setMarkdownHeader` *is*
 * `changeCellType(…, 'markdown', { headingLevel })` (`actions.tsx:2197-2214`) - and one form
 * whatever the count, since the reference's own labels have no plural (`index.ts:4614-4740`).
 */
internal const val CHANGE_TO_HEADING = "Change to heading…"

/** `notebook:insert-heading-above` - beside this cell, at its section's level. */
internal const val INSERT_HEADING_ABOVE = "Insert heading above"

/** `notebook:insert-heading-below` - at the **end of the section**, which is why it is not "below". */
internal const val INSERT_HEADING_BELOW = "Insert heading after section"

internal const val CHANGE_TO_MARKDOWN = "Change to markdown"
internal const val CHANGE_TO_CODE = "Change to code"
internal const val CHANGE_TO_RAW = "Change to raw"

internal const val COLLAPSE_CODE = "Collapse code"
internal const val EXPAND_CODE = "Expand code"
internal const val COLLAPSE_OUTPUTS = "Collapse outputs"
internal const val EXPAND_OUTPUTS = "Expand outputs"

/** `notebook:toggle-output-scrolling` on its menu surface - see [foldingItems]. */
internal const val ENABLE_OUTPUT_SCROLLING = "Enable output scrolling"

/**
 * P5e-3's row - what the surface asks the language server about the symbol under the caret.
 * VS Code's own command is "Show or Focus Hover"; the phrase names what a user gets rather
 * than the protocol verb, which is also how JupyterLab's inspector names it ("Contextual Help").
 */
