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
 * **Every cell gets one**, not only the selected one and not only code cells. The reference draws
 * a line for markdown too - *"vertical gray line for markdown cells in the gutter"*
 * (`markerRenderers/README.txt`) - and on touch it carries more weight than it does on a desktop:
 * there is no hover to reveal a cell's bounds, so this is the only thing always on screen saying
 * where one cell ends and the next begins. Markdown still gets no *background*, which the
 * reference is equally clear about (`CodeCellBackgroundControllerFactory` returns null for it).
 *
 * The widths are copied rather than scaled - 4dp command, 2dp edit, from
 * `DefaultNotebookEditorAppearanceSizes`. A stripe is a marker and nobody taps it, so the 48dp
 * minimum that governs every button beside it does not apply here.
 *
 * ```
 * val stripe = cellStripe(selected, mode, failed, palette)
 * ```
 */
internal fun cellStripe(
  selected: Boolean,
  mode: CellMode,
  failed: Boolean,
  palette: EditorPalette,
): CellStripe = CellStripe(
  // Only the selected cell can be the one being edited, so a notebook in edit mode leaves every
  // other stripe at its resting width.
  width = if (selected && mode == CellMode.EDIT) EDIT_WIDTH else COMMAND_WIDTH,
  color = when {
    // Selection outranks failure: you always know which cell you are on, and the failure is
    // still sitting in the cell's output either way.
    selected -> palette.stripeSelected
    failed -> palette.stripeError
    else -> palette.stripeResting
  },
)

/** `commandModeCellLeftLineWidth`. */
private val COMMAND_WIDTH = 4.dp

/** `editModeCellLeftLineWidth`. */
private val EDIT_WIDTH = 2.dp
