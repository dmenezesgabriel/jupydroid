package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.domain.model.Cell

/**
 * Puts every cell action in the cell's own accessibility context menu.
 *
 * TalkBack reaches the `⋮` menu in three moves: find that button among the cell's other controls,
 * activate it, then walk a list that has just replaced the screen - and on a compact window the
 * list is a modal sheet, so the cell it belongs to is gone while it is open. `customActions` puts
 * the same items in the local context menu of the cell the user is already on, which is what the
 * property exists for.
 *
 * The items come from [cellActionItems], the same function the menu and the sheet are built from,
 * so a fourteenth action cannot appear on two surfaces and not the third.
 *
 * Run and Stop are deliberately absent: they are full-size buttons on the header with their own
 * `contentDescription`, already one move away. This covers what is *behind* something.
 *
 * ```
 * CellFrame(stripe = stripe, modifier = modifier.cellCustomActions(cell, canPaste, onCellAction))
 * ```
 */
@Composable
internal fun Modifier.cellCustomActions(
  cell: Cell,
  canPaste: Boolean,
  onAction: (CellAction) -> Unit,
): Modifier {
  // Remembered against what the list is made of: rebuilding fourteen CustomAccessibilityAction
  // objects on every recomposition of every cell would allocate on each keystroke, and a changed
  // identity makes TalkBack re-read the menu it is sitting in.
  val actions = remember(cell.kind, cell.sourceHidden, cell.outputsHidden, canPaste, onAction) {
    cellActionItems(cell, canPaste).map { (label, action) ->
      CustomAccessibilityAction(label) {
        onAction(action)
        true
      }
    }
  }
  return this.semantics { customActions = actions }
}
