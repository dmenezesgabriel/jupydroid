package com.jupydroid.app.ui.editor

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.domain.model.CellKind

/**
 * The menu a long press on a cell opens.
 *
 * ```
 * CellMenu(expanded = open, onDismiss = { open = false }, onAction = { viewModel.applyToCell(cell.id, it) })
 * ```
 */
@Composable
fun CellMenu(expanded: Boolean, onDismiss: () -> Unit, onAction: (CellAction) -> Unit) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    ITEMS.forEach { (label, action) ->
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
 */
private val ITEMS = listOf(
  "Insert code below" to CellAction.Insert(CellKind.CODE),
  "Insert markdown below" to CellAction.Insert(CellKind.MARKDOWN),
  "Move up" to CellAction.Move(CellMove.UP),
  "Move down" to CellAction.Move(CellMove.DOWN),
  "Duplicate" to CellAction.Duplicate,
  "Clear outputs" to CellAction.ClearOutputs,
  "Delete" to CellAction.Delete,
)
