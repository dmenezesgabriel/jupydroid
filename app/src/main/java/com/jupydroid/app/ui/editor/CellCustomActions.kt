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
  /** Passed straight through: while a run is marked, offering to start one again is a lie. */
  selecting: Boolean,
  /**
   * How many cells a row will act on, so this menu reads the same as the bar's does.
   *
   * A cell's context menu means the marked run when the cell is in it - the same rule
   * [EditorUiState.applyingFrom] applies - so it has to say so, or TalkBack offers "Delete" on a
   * cell and deletes three.
   */
  count: Int,
  onAction: (CellAction) -> Unit,
): Modifier {
  // Remembered against what the list is made of: rebuilding fifteen CustomAccessibilityAction
  // objects on every recomposition of every cell would allocate on each keystroke, and a changed
  // identity makes TalkBack re-read the menu it is sitting in.
  val actions = remember(cell.kind, cell.sourceHidden, cell.outputsHidden, canPaste, selecting, count, onAction) {
    cellActionItems(cell, canPaste, selecting, count)
      // The one row that stands for six is expanded here rather than opening a picker: this menu
      // has no width to run out of and no fold, and a submenu is the worst shape to operate with
      // TalkBack. See [headingLevelItems].
      .flatMap { if (it.second == CellAction.ChooseHeadingLevel) headingLevelItems() else listOf(it) }
      .map { (label, action) ->
        CustomAccessibilityAction(label) {
          onAction(action)
          true
        }
      }
  }
  return this.semantics { customActions = actions }
}
