package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import java.util.UUID

/**
 * One cell operation: a [CellAction] and the cells it applies to, turned into a new
 * [EditorUiState] and recorded as one entry on the undo stack.
 *
 * Split out of [CellEdits] on detekt's `TooManyFunctions`, which found a seam the file's own
 * doc had already described: the transforms next door each do one thing to a notebook, and this
 * decides *which* of them a menu row means and *what it applies to*. `docs/DECISIONS.md` calls
 * that unit a cell operation, which is where the file's name comes from.
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
internal fun EditorUiState.applying(
  action: CellAction,
  cellId: String,
  /** Only [CellAction.Split] reads it; see [CellEditorText] for why it cannot come from the state. */
  editor: CellEditorText = CellEditorText.NONE,
): EditorUiState {
  // Marking a run is the one menu item that is not an edit: it changes what the *next* action will
  // mean, and touches no cell. Answered here rather than in [transformed] so it never reaches the
  // undo machinery at all - `recording` would have declined it anyway, on the same rule that
  // covers `Copy`, but "not a document change" is a fact about the action rather than something
  // to rediscover from a diff each time.
  if (action == CellAction.StartSelecting) return withSelectionStartedAt(cellId)
  // The document is brought up to the editor's text *before* the entry is recorded, so the entry
  // holds what the user can see rather than a copy up to one debounce old. Not an entry of its
  // own - typing is the other undo, and it has never had one.
  val current = if (action == CellAction.Split) withSource(cellId, editor.source) else this
  return current.transformed(action, listOf(cellId), head = cellId, editor)
    .recording(before = current)
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
  is CellAction.ChangeKind -> withCellsKindChanged(cellIds, action.kind)
  is CellAction.Merge -> withCellsMerged(cellIds, action.into, newId = newCellId())
  // Two ids because a selection cuts into three pieces and a caret into two; the spare is unused,
  // exactly as a cut code cell's is in `withClipboardPasted`. Minting them here keeps
  // `withCellSplit` free of id generation like every other transform in the file.
  CellAction.Split -> withCellSplit(head, editor.caret, newIds = List(2) { newCellId() })
  is CellAction.Move -> withCellsMoved(cellIds, action.move)
  // Answered by `applying` before it gets here, because it is not a document change at all.
  CellAction.StartSelecting -> this
  CellAction.Copy -> withCellsCopied(cellIds)
  CellAction.Cut -> withCellsCut(cellIds, replacementId = newCellId())
  CellAction.Paste -> withClipboardPasted(head, newIds = clipboard.map { newCellId() })
  CellAction.Duplicate -> withCellsDuplicated(cellIds, newIds = cellIds.map { newCellId() })
  CellAction.Delete -> withCellsDeleted(cellIds, replacementId = newCellId())
  CellAction.ClearOutputs -> withOutputsCleared(cellIds)
  CellAction.ToggleSourceHidden -> withSourceHiddenSet(cellIds, hidden = !folded(head) { sourceHidden })
  CellAction.ToggleOutputsHidden -> withOutputsHiddenSet(cellIds, hidden = !folded(head) { outputsHidden })
}

/** Which way the head cell is folded, which is also what the menu row's label was written from. */
private fun EditorUiState.folded(head: String, part: Cell.() -> Boolean): Boolean =
  cells.firstOrNull { it.id == head }?.part() ?: false

/** Ids are minted here rather than inside the transforms, which keeps those testable. */
internal fun newCellId(): String = UUID.randomUUID().toString()
