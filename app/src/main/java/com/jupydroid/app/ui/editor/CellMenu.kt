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
 * CellMenu(expanded = open, cell = cell, onDismiss = { open = false }, onAction = { … })
 * ```
 */
@Composable
fun CellMenu(expanded: Boolean, cell: Cell, onDismiss: () -> Unit, onAction: (CellAction) -> Unit) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    itemsFor(cell).forEach { (label, action) ->
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
private fun itemsFor(cell: Cell): List<Pair<String, CellAction>> = buildList {
  add("Insert code below" to CellAction.Insert(CellKind.CODE))
  add("Insert markdown below" to CellAction.Insert(CellKind.MARKDOWN))
  add("Move up" to CellAction.Move(CellMove.UP))
  add("Move down" to CellAction.Move(CellMove.DOWN))
  add("Duplicate" to CellAction.Duplicate)
  if (cell.kind == CellKind.CODE) {
    add((if (cell.sourceHidden) EXPAND_CODE else COLLAPSE_CODE) to CellAction.ToggleSourceHidden)
  }
  add("Clear outputs" to CellAction.ClearOutputs)
  add("Delete" to CellAction.Delete)
}

internal const val COLLAPSE_CODE = "Collapse code"
internal const val EXPAND_CODE = "Expand code"
