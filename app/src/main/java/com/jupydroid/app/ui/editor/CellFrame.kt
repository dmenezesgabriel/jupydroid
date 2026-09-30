package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.jupydroid.app.theme.Spacing

/**
 * One cell's chrome: the stripe down its left edge, and the space around it.
 *
 * Drawn with [drawBehind] rather than laid out as a `Box`, which keeps a stripe change in the
 * draw phase - a cell going from resting to selected re-draws and does not re-measure. Same
 * reason `LineNumbers` draws its gutter instead of composing one.
 *
 * The spacing is the reference's, as ratios rather than pixels: cells are separated by
 * [BETWEEN_CELLS] (`distanceBetweenCells = 16`) and the stripe stands off the content by
 * [STRIPE_GAP] (`codeCellLeftLinePadding = 5`).
 *
 * ```
 * CellFrame(stripe = cellStripe(selected, mode, failed, palette), tint = Color.Unspecified) { … }
 * ```
 */
@Composable
internal fun CellFrame(
  stripe: CellStripe,
  /**
   * The wash over a cell that is one of a marked run, or `Color.Unspecified` for every other cell.
   *
   * Behind the whole frame rather than behind the source, which is where the reference puts it -
   * `.jp-Cell.jp-mod-selected { background: … }` tints the cell node while the editor inside keeps
   * its own background (`packages/notebook/style/base.css:141-143`). So the mark reads as a band
   * around the field, the header and the outputs, which is the part of a cell that is always on
   * screen whatever it holds.
   */
  tint: Color,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .drawBehind {
        if (tint.isSpecified) drawRect(color = tint)
        // Inset, not flush with the window: the stripe is a mark in a gutter, and a rect at x=0
        // would sit under a rounded corner or a gesture inset on the devices that have them.
        // Anchored on its left edge, so narrowing it in edit mode widens the gap instead of
        // moving the line - the content never shifts either way.
        //
        // Skipped when there is nothing to draw, which is every cell that is neither
        // active nor failed - the same guard [tint] takes above, rather than a transparent rect
        // blended over the page once per cell per frame. The gutter's *width* is reserved by
        // [STRIPE_LEFT] whatever is drawn in it.
        if (stripe.color != Color.Transparent) {
          drawRect(
            color = stripe.color,
            topLeft = Offset(STRIPE_LEFT.toPx(), 0f),
            size = Size(stripe.width.toPx(), size.height),
          )
        }
      }
      // Equal side padding, with the stripe living inside the left margin rather than pushing
      // the content in. A 9dp left inset against a 16dp right one read as a mistake on device.
      .padding(horizontal = SIDE_PADDING)
      .padding(top = TOP_PADDING, bottom = BETWEEN_CELLS),
    content = content,
  )
}

/** `distanceBetweenCells`. */
private val BETWEEN_CELLS = 16.dp

/** `codeCellLeftLinePadding` - the clearance the reference leaves between stripe and content. */
private val STRIPE_GAP = 5.dp

/** The command-mode stripe, which is the wider of the two. */
private val MAX_STRIPE_WIDTH = 4.dp

private val SIDE_PADDING = Spacing.lg
private val TOP_PADDING = Spacing.sm

/** Positions the stripe so the widest it gets still clears [STRIPE_GAP] before the content. */
private val STRIPE_LEFT = SIDE_PADDING - STRIPE_GAP - MAX_STRIPE_WIDTH
