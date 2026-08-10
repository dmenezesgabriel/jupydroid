package com.jupydroid.app.ui.editor

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
