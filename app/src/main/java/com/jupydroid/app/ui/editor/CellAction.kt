package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellKind

/** Which way a cell is being moved past its neighbour. */
enum class CellMove { UP, DOWN }

/**
 * What the long-press menu can do to one cell.
 *
 * The menu's vocabulary, kept apart from both the menu that shows it and the [CellEdits] that
 * carry it out - so neither has to know about the other.
 */
sealed interface CellAction {
  data class Insert(val kind: CellKind) : CellAction

  data class Move(val move: CellMove) : CellAction

  data object Duplicate : CellAction

  data object Delete : CellAction

  data object ClearOutputs : CellAction

  /** Collapse or expand the cell's source. Code cells only, as in JupyterLab. */
  data object ToggleSourceHidden : CellAction
}
