package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import java.util.UUID

/**
 * One cell operation: a [CellAction] and the cells it applies to, turned into a new
 * [EditorUiState] and recorded as one entry on the undo stack.
 *
 * Split out of [CellEdits] on detekt's `TooManyFunctions`, which found a seam the file's own
 * doc had already described: the transforms next door each do one thing to a notebook, and this
 * decides *which* of them a menu row means and *what it applies to*. That unit is a cell
 * operation, which is where the file's name comes from.
 */

/**
 * The one place a [CellAction] becomes a transform, so a fourteenth action cannot reach the menu
 * and stop there.
 *
 * Beside the transforms rather than inside the ViewModel: it is pure - state in, state out - and
 * the ViewModel was carrying it only because that is where the caller happened to live. detekt
 * asked for the split and detekt was right, as it was about `NotebookKernel`.
 *
 * **It is also the undoable unit**, which is why the recording is here and not at each call site.
 * JupyterLab's is one `sharedModel.transact { }` block - `deleteCells` inserts its replacement
 * cell *inside* the transaction "to make the deletion of a notebook's last cell undoable"
 * (`actions.tsx:3391`) - and this function is that boundary under another name. A fourteenth
 * action becomes undoable by existing; see [recording] for why no list of which ones count.
 */
/**
 * The same operation, asked for from a menu opened on [cellId].
 *
 * That cell decides the operand only when it is *outside* the marked run, in which case the run
 * collapses onto it first - the reference draws the line in `_evtMouseDown`, where a press
 * anywhere in a cell sets `activeCellIndex` and a press outside the selection deselects
 * (`packages/notebook/src/widget.ts:3160-3167`). Inside the run it changes nothing, so the whole
 * run is what the menu means.
 *
 * **A cell that has gone is a no-op**, which is [CellEdits]'s promise since 4b and which the
 * operand rule nearly broke: collapsing the run before finding out the cell was missing left the
 * old head selected, so the action landed on *that* cell instead. The guard is here rather than
 * inside [withCellSelected], which cannot tell "this cell is gone" from "nothing was asked for".
 */
internal fun EditorUiState.applyingFrom(
  cellId: String,
  action: CellAction,
  editor: CellEditorText = CellEditorText.NONE,
): EditorUiState {
  if (cells.none { it.id == cellId }) return this
  val on = if (cellId in selectedCellIds) this else withSelectionCollapsed().withCellSelected(cellId)
  return on.applying(action, editor)
}

internal fun EditorUiState.applying(
  action: CellAction,
  /** Only [CellAction.Split] reads it; see [CellEditorText] for why it cannot come from the state. */
  editor: CellEditorText = CellEditorText.NONE,
): EditorUiState = when {
  // Nothing is selected, so there is nothing a cell-scoped action could mean.
  selectedCellId == null -> this
  // Marking a run is the one menu item that is not an edit: it changes what the *next* action will
  // mean, and touches no cell. Answered here rather than in [transformed] so it never reaches the
  // undo machinery at all - `recording` would have declined it anyway, on the same rule that
  // covers `Copy`, but "not a document change" is a fact about the action rather than something
  // to rediscover from a diff each time.
  action == CellAction.StartSelecting -> withSelectionStartedAt(selectedCellId)
  // Neither is this one, and it never gets this far: the surface that raised it opens the picker
  // and dispatches the [CellAction.ChangeToHeading] the user then chose. Answered here rather than
  // left to `transformed` so a row that reaches the ViewModel by some route cannot record an
  // undo entry for having been tapped.
  action == CellAction.ChooseHeadingLevel -> this
  // The hover ask is surface-answered too: [EditorCell] turns it into a popup beside the caret
  // it asked about, so an action arriving here by any other route has nothing to change - the
  // picker's own guard above, kept for exactly this reachability.
  action == CellAction.SymbolInfo -> this
  // The definition, format and rename asks reach the language server through
  // [EditorViewModel.cellAction], which never dispatches them here - the same guard for the
  // same reachability reason. [CellAction.RenameSymbol] is the surface-answered row, and
  // [CellAction.RenameTo] the ask it resolves into; neither is an edit of its own.
  action == CellAction.GoToDefinition || action == CellAction.FormatCell -> this
  action == CellAction.RenameSymbol || action is CellAction.RenameTo -> this
  else -> operated(action, head = selectedCellId, editor = editor)
}

private fun EditorUiState.operated(action: CellAction, head: String, editor: CellEditorText): EditorUiState {
  // The document is brought up to the editor's text *before* the entry is recorded, so the entry
  // holds what the user can see rather than a copy up to one debounce old. Not an entry of its
  // own - typing is the other undo, and it has never had one.
  val current = if (action == CellAction.Split) withSource(head, editor.source) else this
  return current.transformed(action, operandOf(action), head, editor)
    .recording(before = current)
    .let { if (action.keepsTheRun) it else it.withSelectionCollapsed() }
}

/**
 * Every cell [action] applies to, in document order - which for four of them is **more than is
 * marked**: a collapsed heading stands for its whole section.
 *
 * `Private.selectedCells` adds a collapsed heading's children to what is copied, cut or duplicated
 * (`actions.tsx:3103-3130`) and `deleteCells` marks them for deletion (`:3340-3365`). Nothing else
 * does - `changeCellType`, `mergeCells`, `moveCells`, `clearOutputs` and every collapse iterate
 * `isSelectedOrActive` and stop there - and the asymmetry is deliberate rather than an oversight
 * there: those four *remove or reproduce* the heading, so leaving its section behind would orphan
 * cells under a heading that has gone, while the rest leave the structure standing.
 *
 * A `LinkedHashSet` because a run may already hold the section it is being given, and the order is
 * the document's either way: sections are contiguous and follow their heading, so inserting each
 * one directly after its own heading is document order. That is `selectedCells`'s shape too - it
 * walks the widgets and adds children as it meets them.
 */
internal fun EditorUiState.operandOf(action: CellAction): List<String> {
  val marked = selectedCellIds
  val collapsed = when {
    action.takesWholeSections -> cells.filter { it.headingCollapsed }.map { it.id }.toSet()
    else -> emptySet()
  }
  if (collapsed.none { it in marked }) return marked
  return marked
    .flatMapTo(LinkedHashSet()) { id -> if (id in collapsed) listOf(id) + headingSectionOf(id) else listOf(id) }
    .toList()
}

/**
 * The four that stand for a whole section, exhaustive for [keepsTheRun]'s reason: a nineteenth
 * action has to decide this, not inherit it.
 */
private val CellAction.takesWholeSections: Boolean
  get() = when (this) {
    CellAction.Copy, CellAction.Cut, CellAction.Duplicate, CellAction.Delete,
    -> true

    is CellAction.Insert, is CellAction.InsertAbove, is CellAction.InsertHeading,
    is CellAction.ChangeKind, is CellAction.ChangeToHeading, CellAction.ChooseHeadingLevel,
    is CellAction.Merge, CellAction.Split, is CellAction.Move, CellAction.StartSelecting,
    CellAction.Paste, CellAction.ClearOutputs, is CellAction.Fold, CellAction.SymbolInfo,
    CellAction.GoToDefinition, CellAction.FormatCell, CellAction.RenameSymbol,
    is CellAction.RenameTo,
    -> false
  }

/**
 * Whether the marked run survives this operation.
 *
 * **Three do and every other one does not**, and the division is read out of the reference rather
 * than reasoned about: `move` (`packages/notebook/src/actions.tsx:586-610`), `clearOutputs`
 * (`:1822-1840`) and the collapses (`:1877-2110`) are the only cell operations with no
 * `deselectAll()` in them. After each of those the same cells are still there and still worth
 * acting on together; after a delete, a merge or a change of type the run named cells that are
 * gone or different.
 *
 * Exhaustive rather than an `else`, for the reason [transformed] is: a sixteenth action has to
 * decide this, not inherit it.
 */
private val CellAction.keepsTheRun: Boolean
  get() = when (this) {
    // Every fold, `toggleCurrentHeadingCollapse` included - none has a `deselectAll()` in it
    // (`actions.tsx:1877-2110`, `:2440-2455`). Where the heading one *cannot* keep the run, because
    // a mark would be left on a cell nothing draws, `withHeadingCollapsed` drops it itself rather
    // than making this a fourth answer.
    is CellAction.Fold,
    is CellAction.Move, CellAction.ClearOutputs,
    // The seven that edit no structure: a run, a picker, a hover, a jump, a format, a rename
    // - none of them a reason a marked run would stop being one. The format and rename asks
    // edit text through their own routes and never reach [operated], but neither
    // `notebook.formatCell` nor `editor.action.rename` has a `deselectAll()` in it either.
    CellAction.StartSelecting, CellAction.ChooseHeadingLevel, CellAction.SymbolInfo,
    CellAction.GoToDefinition, CellAction.FormatCell, CellAction.RenameSymbol,
    is CellAction.RenameTo,
    -> true

    is CellAction.Insert, is CellAction.InsertAbove, is CellAction.InsertHeading,
    is CellAction.ChangeKind, is CellAction.Merge, is CellAction.ChangeToHeading,
    CellAction.Split, CellAction.Copy, CellAction.Cut, CellAction.Paste,
    CellAction.Duplicate, CellAction.Delete,
    -> false
  }

/**
 * [cellIds] is every cell the action applies to, in document order - JupyterLab's
 * `isSelectedOrActive`; [head] is the one that decides the *shape* of what happens, which is its
 * `activeCell`. Three actions read only the head, and each is the reference's own narrowing:
 * insert above and insert below act on the active cell and clear the selection
 * (`actions.tsx:545`, `:582`), paste drops the clipboard below it (`'below'`,
 * `notebook-extension/src/index.ts:3594`), and split reads its caret (`:206`). The two collapses
 * read the head's state so a mixed run comes out uniform rather than inverted.
 */
private fun EditorUiState.transformed(
  action: CellAction,
  cellIds: List<String>,
  head: String,
  editor: CellEditorText,
): EditorUiState = when (action) {
  is CellAction.Insert -> withCellInserted(head, action.kind, newId = newCellId())
  is CellAction.InsertAbove -> withCellInsertedAbove(head, action.kind, newId = newCellId())
  is CellAction.InsertHeading -> withHeadingInserted(action.reach, newId = newCellId())
  is CellAction.ChangeKind -> withCellsKindChanged(cellIds, action.kind)
  is CellAction.ChangeToHeading -> withCellsHeadingLevel(cellIds, action.level)
  is CellAction.Merge -> withCellsMerged(cellIds, action.into, newId = newCellId())
  // Two ids because a selection cuts into three pieces and a caret into two; the spare is unused,
  // exactly as a cut code cell's is in `withClipboardPasted`. Minting them here keeps
  // `withCellSplit` free of id generation like every other transform in the file.
  CellAction.Split -> withCellSplit(head, editor.caret, newIds = List(2) { newCellId() })
  is CellAction.Move -> withCellsMoved(cellIds, action.move)
  // All seven are answered by `applying` before they get here, none being a change this
  // transform can make. Kept as branches so the `when` stays exhaustive: a nineteenth action
  // has to decide, not inherit.
  CellAction.StartSelecting, CellAction.ChooseHeadingLevel, CellAction.SymbolInfo,
  CellAction.GoToDefinition, CellAction.FormatCell, CellAction.RenameSymbol, is CellAction.RenameTo,
  -> this
  CellAction.Copy -> withCellsCopied(cellIds)
  CellAction.Cut -> withCellsCut(cellIds, replacementId = newCellId())
  CellAction.Paste -> withClipboardPasted(head, newIds = clipboard.map { newCellId() })
  CellAction.Duplicate -> withCellsDuplicated(cellIds, newIds = cellIds.map { newCellId() })
  CellAction.Delete -> withCellsDeleted(cellIds, replacementId = newCellId())
  CellAction.ClearOutputs -> withOutputsCleared(cellIds)
  is CellAction.Fold -> folded(action, cellIds, head)
}

/**
 * The three folds, which share a shape as well as a category: each reads the **head's** own state
 * and sets from it, so a mixed run comes out uniform rather than inverted - `hideCode` and
 * `showCode` are separate commands over the selection there (`actions.tsx:1877-1910`), never a
 * toggle per cell.
 *
 * Split out of [transformed] on detekt's `LongMethod`, and it was pointing at the seam
 * [CellAction.Fold] then made a type: three of the dispatch's branches were one idea.
 */
private fun EditorUiState.folded(fold: CellAction.Fold, cellIds: List<String>, head: String): EditorUiState =
  when (fold) {
    CellAction.ToggleSourceHidden -> withSourceHiddenSet(cellIds, hidden = !headCell(head) { sourceHidden })
    CellAction.ToggleOutputsHidden -> withOutputsHiddenSet(cellIds, hidden = !headCell(head) { outputsHidden })
    // The head and not [cellIds], which is the reference's own narrowing rather than a simplification:
    // `toggleCurrentHeadingCollapse` reads `notebook.activeCell` (`actions.tsx:2440-2455`), and a run
    // of headings collapsed at once would hide the very cells the rest of the run names.
    CellAction.ToggleHeadingCollapse -> withHeadingCollapsed(head, collapsed = !headCell(head) { headingCollapsed })
    // The one fold that reads the *run*, not the head - see [EditorUiState.allMarkedCodeCellsScrolled].
    CellAction.ToggleOutputsScrolled ->
      withOutputsScrolledSet(cellIds, scrolled = !allMarkedCodeCellsScrolled(cellIds))
  }

/** Which way the head cell is folded, which is also what the menu row's label was written from. */
private fun EditorUiState.headCell(head: String, part: Cell.() -> Boolean): Boolean =
  cells.firstOrNull { it.id == head }?.part() ?: false

/** Ids are minted here rather than inside the transforms, which keeps those testable. */
internal fun newCellId(): String = UUID.randomUUID().toString()
