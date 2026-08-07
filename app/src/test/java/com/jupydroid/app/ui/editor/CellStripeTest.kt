package com.jupydroid.app.ui.editor

import androidx.compose.ui.unit.dp
import com.jupydroid.app.theme.DarkEditorPalette
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The vertical line down the left of every cell: what it is wide and what colour, given the
 * cell's state.
 *
 * A pure function so the rules can be argued on the JVM. Only the *drawing* needs a device, and
 * drawing is the part a screenshot can check.
 *
 * Widths come straight from the reference's `DefaultNotebookEditorAppearanceSizes` - 4px in
 * command mode, 2px in edit - and are copied rather than scaled, because a stripe is a marker
 * and not something anyone taps. That is the opposite call from the buttons beside it, which
 * take Material's 48dp minimum.
 */
class CellStripeTest {
  private val palette = DarkEditorPalette

  @Test
  fun anUnselectedCellStillHasAStripe() {
    val stripe = cellStripe(

      selected = false,

      mode = CellMode.COMMAND,

      status = ExecutionStatus.STOPPED_OK,

      palette = palette,

    )

    assertEquals(palette.stripeResting, stripe.color)
    assertEquals(4.dp, stripe.width)
  }

  /**
   * Every cell, not only code cells. The reference draws one for markdown too - *"vertical gray
   * line for markdown cells in the gutter"* (`markerRenderers/README.txt`) - even though markdown
   * gets no cell background. On touch that line is doing more work than it does on a desktop:
   * there is no hover, so it is the only always-visible thing saying where one cell ends.
   */
  @Test
  fun theSelectedCellIsMarkedInTheAccentColour() {
    val stripe = cellStripe(

      selected = true,

      mode = CellMode.COMMAND,

      status = ExecutionStatus.STOPPED_OK,

      palette = palette,

    )

    assertEquals(palette.stripeSelected, stripe.color)
  }

  /** `commandModeCellLeftLineWidth = 4`, `editModeCellLeftLineWidth = 2`. */
  @Test
  fun theStripeNarrowsWhileTheCellIsBeingTypedInto() {
    val editing = cellStripe(

      selected = true,

      mode = CellMode.EDIT,

      status = ExecutionStatus.STOPPED_OK,

      palette = palette,

    )

    assertEquals(2.dp, editing.width)
  }

  /** Only the selected cell narrows: an unselected cell is not in edit mode whatever the notebook is. */
  @Test
  fun anUnselectedCellKeepsTheCommandWidthWhileAnotherIsEdited() {
    val other = cellStripe(

      selected = false,

      mode = CellMode.EDIT,

      status = ExecutionStatus.STOPPED_OK,

      palette = palette,

    )

    assertEquals(4.dp, other.width)
  }

  /**
   * A cell whose last run raised is findable while scrolling, which is the one thing an
   * always-visible per-cell marker is good for. The reference tints its run gutter icon instead -
   * an icon that only appears on hover, so it is no use here.
   */
  @Test
  fun aCellThatRaisedIsMarkedInTheErrorColour() {
    val stripe = cellStripe(

      selected = false,

      mode = CellMode.COMMAND,

      status = ExecutionStatus.STOPPED_ERROR,

      palette = palette,

    )

    assertEquals(palette.stripeError, stripe.color)
  }

  /** Selection wins: you always know which cell you are on, and the failure is still in its output. */
  @Test
  fun selectionOutranksFailure() {
    val stripe = cellStripe(

      selected = true,

      mode = CellMode.COMMAND,

      status = ExecutionStatus.STOPPED_ERROR,

      palette = palette,

    )

    assertEquals(palette.stripeSelected, stripe.color)
  }
}
