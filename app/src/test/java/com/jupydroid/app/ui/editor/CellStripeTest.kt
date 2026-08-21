package com.jupydroid.app.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jupydroid.app.theme.DarkEditorPalette
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The vertical line down the left of a cell: what it is wide and what colour, given the cell's
 * state.
 *
 * A pure function so the rules can be argued on the JVM. Only the *drawing* needs a device, and
 * drawing is the part a screenshot can check.
 *
 * Widths come straight from IntelliJ's `DefaultNotebookEditorAppearanceSizes` - 4px in command
 * mode, 2px in edit - and are copied rather than scaled, because a stripe is a marker and not
 * something anyone taps. That is the opposite call from the buttons beside it, which take
 * Material's 48dp minimum. **The colours are JupyterLab's and the widths are IntelliJ's**, which
 * is a hybrid and is named as one in `docs/DECISIONS.md`.
 */
class CellStripeTest {
  private val palette = DarkEditorPalette

  /**
   * **Only the active cell is marked.** `.jp-Collapser` is `background: transparent`
   * (`packages/cells/style/collapser.css:6-15`) and takes a colour only under
   * `.jp-Cell.jp-mod-active` (`packages/notebook/style/base.css:108-110`); the cell node itself is
   * `background: transparent; border: none` (`packages/cells/style/widget.css:18-24`). The
   * reference answers "where does this cell end" with the editor's own border, not with a gutter
   * mark - see `docs/DECISIONS.md`.
   */
  @Test
  fun anUnselectedCellIsNotMarkedAtAll() {
    val stripe = cellStripe(
      selected = false,
      mode = CellMode.COMMAND,
      status = ExecutionStatus.STOPPED_OK,
      palette = palette,
    )

    assertEquals(Color.Transparent, stripe.color)
  }

  /** The width is reserved whether or not anything is drawn in it, so nothing shifts on selection. */
  @Test
  fun anUnmarkedCellStillReservesTheGutter() {
    val stripe = cellStripe(
      selected = false,
      mode = CellMode.COMMAND,
      status = ExecutionStatus.STOPPED_OK,
      palette = palette,
    )

    assertEquals(4.dp, stripe.width)
  }

  /** `.jp-Cell.jp-mod-active .jp-Collapser { background: var(--jp-brand-color1) }`. */
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
   *
   * **This is the app's own adaptation and it is now the only non-active mark on screen**, which
   * is what the paragraph above always claimed it was for and could not be while every resting
   * cell carried a grey line beside it.
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
