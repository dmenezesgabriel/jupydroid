package com.jupydroid.app.ui.editor

import androidx.compose.ui.text.TextRange
import com.jupydroid.app.domain.model.CellKind

/** Which way a cell is being moved past its neighbour. */
enum class CellMove { UP, DOWN }

/** Which neighbour a merge folds into. */
enum class MergeInto { ABOVE, BELOW }

/**
 * What the long-press menu can do to one cell.
 *
 * The menu's vocabulary, kept apart from both the menu that shows it and the [CellEdits] that
 * carry it out - so neither has to know about the other.
 */
sealed interface CellAction {
  /** Below the target, which is what `notebook:insert-cell-below` (`B`) does. */
  data class Insert(val kind: CellKind) : CellAction

  /** Above it - `notebook:insert-cell-above` (`A`). */
  data class InsertAbove(val kind: CellKind) : CellAction

  /** Code to markdown or back. Keeps the cell's id, drops its outputs. */
  data class ChangeKind(val kind: CellKind) : CellAction

  /** Fold this cell together with a neighbour. */
  data class Merge(val into: MergeInto) : CellAction

  /**
   * Cut this cell in two where the caret is - `notebook:split-cell-at-cursor`, and merge's
   * inverse.
   *
   * Carries nothing, unlike [Merge]: where to cut is [CellEditorText], which arrives beside the
   * action rather than inside it. The menu names an action and cannot see a caret - and an item
   * carrying one would be rebuilt on every keystroke, which is what the accessibility surface
   * remembers these against to avoid.
   */
  data object Split : CellAction

  data object Copy : CellAction

  data object Cut : CellAction

  /** Drops the clipboard below this cell - `notebook:paste-cell-below` (`V`). */
  data object Paste : CellAction

  data class Move(val move: CellMove) : CellAction

  data object Duplicate : CellAction

  data object Delete : CellAction

  data object ClearOutputs : CellAction

  /** Collapse or expand the cell's source. Code cells only, as in JupyterLab. */
  data object ToggleSourceHidden : CellAction

  /** Collapse or expand the cell's outputs. Hides them; [ClearOutputs] throws them away. */
  data object ToggleOutputsHidden : CellAction
}

/**
 * What a cell's text field is holding right now: its text and where the caret is in it.
 *
 * Travels beside a [CellAction] because [CellAction.Split] is the one action that is not a pure
 * function of the document. Two things make it necessary rather than convenient. The caret is not
 * in the document at all - it belongs to the field. And the text can be *ahead* of the document,
 * because an edit is reported on a 150ms debounce (`CodeCellField.EDIT_REPORT_MILLIS`) and the tap
 * that opens the cell menu can beat it; splitting the model's stale copy at the field's fresh
 * offset would cut in the wrong place. JupyterLab reads its editor for the same reason -
 * `editor.getSelections()` beside `sharedModel.getSource()` (`actions.tsx:216-222`).
 *
 * [NONE] is what an action that has no field behind it carries: splitting on it gives two empty
 * cells, which is the reference's answer for an empty source rather than a special case.
 */
data class CellEditorText(val source: String, val caret: TextRange) {
  companion object {
    val NONE = CellEditorText("", TextRange.Zero)
  }
}
