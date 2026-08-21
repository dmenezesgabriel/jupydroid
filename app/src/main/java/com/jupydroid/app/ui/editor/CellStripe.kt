package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupydroid.app.theme.EditorPalette

/** The vertical line down a cell's left edge: how wide, and in what colour. */
@Immutable
data class CellStripe(val width: Dp, val color: Color)

/**
 * What the stripe looks like for a cell in this state.
 *
 * **Only the active cell is marked**, which is the reference's rule rather than a saving.
 * `.jp-Collapser` is `background: transparent` (`packages/cells/style/collapser.css:6-15`) and
 * takes a colour only under `.jp-Cell.jp-mod-active`
 * (`packages/notebook/style/base.css:108-110`), while the cell node itself is
 * `background: transparent; border: none` (`packages/cells/style/widget.css:18-24`). The reference
 * answers *"where does this cell end"* with the input area's own border - `1px solid` at rest,
 * brand colour plus a box-shadow in edit mode (`packages/cells/style/inputarea.css:18-26`,
 * `base.css:160-164`) - and not with a mark in the gutter. This app drew the opposite in both
 * halves until P2m; what the reversal cost is `docs/DECISIONS.md`.
 *
 * The colour rule is therefore JupyterLab's while the widths stay IntelliJ's - 4dp command, 2dp
 * edit, from `DefaultNotebookEditorAppearanceSizes`, copied rather than scaled. **That hybrid is
 * deliberate and is named as one** in `docs/DECISIONS.md`: a stripe is a marker and nobody taps
 * it, so the 48dp minimum that governs every button beside it does not apply, and the reference's
 * own 8px collapser is sized to be clicked.
 *
 * ```
 * val stripe = cellStripe(selected, mode, status, palette)
 * ```
 */
internal fun cellStripe(
  selected: Boolean,
  mode: CellMode,
  status: ExecutionStatus,
  palette: EditorPalette,
): CellStripe = CellStripe(
  // Reserved whether or not anything is drawn in it, so selecting a cell never shifts its content.
  // Only the selected cell can be the one being edited, so a notebook in edit mode leaves every
  // other stripe at its resting width.
  width = if (selected && mode == CellMode.EDIT) EDIT_WIDTH else COMMAND_WIDTH,
  color = when {
    // Selection outranks failure: you always know which cell you are on, and the failure is
    // still sitting in the cell's output either way.
    selected -> palette.stripeSelected
    // The one mark the reference does not have, kept as this app's own adaptation - and now the
    // only non-active mark on screen, which is the job it was always described as doing.
    status == ExecutionStatus.STOPPED_ERROR -> palette.stripeError
    else -> Color.Transparent
  },
)

/** `commandModeCellLeftLineWidth`. */
private val COMMAND_WIDTH = 4.dp

/** `editModeCellLeftLineWidth`. */
private val EDIT_WIDTH = 2.dp
