package com.jupydroid.app.ui.editor

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind

/**
 * The menu the `⋮` on a cell header opens.
 *
 * ```
 * CellMenu(expanded = open, cell = cell, canPaste = false, onDismiss = { open = false }) { … }
 * ```
 */
@Composable
fun CellMenu(
  expanded: Boolean,
  cell: Cell,
  canPaste: Boolean,
  onDismiss: () -> Unit,
  onAction: (CellAction) -> Unit,
) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    itemsFor(cell, canPaste).forEach { (label, action) ->
      DropdownMenuItem(
        // Named for the accessibility tree as well as the eye: "Delete" alone would also match
        // any other Delete on screen, and these are what a Maestro flow taps.
        modifier = Modifier.semantics { contentDescription = "Cell menu $label" },
        text = { Text(label) },
        onClick = {
          onAction(action)
          onDismiss()
        },
      )
    }
  }
}

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
 */
private fun itemsFor(cell: Cell, canPaste: Boolean): List<Pair<String, CellAction>> =
  addingItems() + shapingItems(cell) + clipboardItems(canPaste) + foldingItems(cell) + removingItems()

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

internal const val CHANGE_TO_MARKDOWN = "Change to markdown"
internal const val CHANGE_TO_CODE = "Change to code"

internal const val COLLAPSE_CODE = "Collapse code"
internal const val EXPAND_CODE = "Expand code"
internal const val COLLAPSE_OUTPUTS = "Collapse outputs"
internal const val EXPAND_OUTPUTS = "Expand outputs"
