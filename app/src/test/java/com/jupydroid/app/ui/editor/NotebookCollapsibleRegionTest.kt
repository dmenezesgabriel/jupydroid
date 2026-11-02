package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How far the notebook's title strip has slid away as the document is read.
 *
 * The strip is the reference's own arrangement - `#jp-title-panel` holds the document's name above
 * its toolbar in single-document mode (`packages/application/style/titlepanel.css:10-37`,
 * `packages/application/src/shell.ts:690-705`) - and on a phone it costs what the reference's 28px
 * strip does not. Measured: 48dp of notebook at 320dp, which put a fourth cell's source under the
 * bottom bar and was caught by four flows at once (`split-code-cell`, `batch-delete`,
 * `insert-heading`, `next-previous`).
 *
 * Material's answer to a two-row bar is that the second row is not free and does not pretend to
 * be: it collapses as the content scrolls. The name is there when you are at the top of the
 * document, which is where you look for it, and the 48dp is the notebook's the rest of the time.
 */
class NotebookCollapsibleRegionTest {
  @Test
  fun atTheTopOfTheDocumentTheTitleIsWhole() {
    assertEquals(0f, collapsibleRegionFraction(firstVisibleItem = 0, scrolledPx = 0, stripPx = 96f), 0f)
  }

  @Test
  fun itSlidesAwayOverTheStripsOwnHeight() {
    assertEquals(0.25f, collapsibleRegionFraction(0, scrolledPx = 24, stripPx = 96f), 0f)
    assertEquals(0.5f, collapsibleRegionFraction(0, scrolledPx = 48, stripPx = 96f), 0f)
  }

  @Test
  fun pastTheStripsHeightItIsGoneRatherThanNegative() {
    assertEquals(1f, collapsibleRegionFraction(0, scrolledPx = 500, stripPx = 96f), 0f)
  }

  /**
   * Past the first cell the offset restarts from zero - it is *within* the first visible item - so
   * reading it alone would bring the title back in the middle of a long notebook.
   */
  @Test
  fun beyondTheFirstCellItStaysGoneWhateverTheOffsetSays() {
    assertEquals(1f, collapsibleRegionFraction(firstVisibleItem = 3, scrolledPx = 0, stripPx = 96f), 0f)
  }

  /**
   * A notebook whose first cell is shorter than the strip can still only collapse it once, and a
   * zero-height strip must not divide by zero - which is what an unmeasured first frame reports.
   */
  @Test
  fun aStripWithNoHeightYetIsWhole() {
    assertEquals(0f, collapsibleRegionFraction(0, scrolledPx = 40, stripPx = 0f), 0f)
  }
}
