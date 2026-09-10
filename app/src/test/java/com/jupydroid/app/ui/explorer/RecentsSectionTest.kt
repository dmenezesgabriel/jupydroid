package com.jupydroid.app.ui.explorer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How many items the Recents section puts above the listing, so a scroll to a listing row lands
 * on that row and not on its neighbour.
 *
 * Untested until P7c, which is exactly the shape of defect it exists to prevent: audit A3's own
 * off-by-one, where a created row that lands below the fold makes the screen look unchanged.
 * Collapsing the section changes the count without changing anything visible, so nothing but an
 * assertion could catch it going wrong.
 */
class RecentsSectionTest {
  /** No recents, no section - so nothing sits above the listing at all. */
  @Test
  fun nothingOpenedYetPutsNothingAboveTheListing() {
    assertEquals(0, itemsAboveTheListing(recents = 0, collapsed = false))
  }

  /** Every row, plus the heading above them and the divider below. */
  @Test
  fun anOpenSectionCountsItsRowsTheHeadingAndTheDivider() {
    assertEquals(5, itemsAboveTheListing(recents = 3, collapsed = false))
  }

  /**
   * Collapsed, the rows are not emitted - but the heading and the divider still are, so the count
   * is two whatever the section holds.
   */
  @Test
  fun aCollapsedSectionCountsOnlyItsHeadingAndDivider() {
    assertEquals(2, itemsAboveTheListing(recents = 3, collapsed = true))
  }

  /** A collapsed section that is empty is no section: the heading is not drawn over nothing. */
  @Test
  fun anEmptySectionIsNotCountedEvenWhenCollapsed() {
    assertEquals(0, itemsAboveTheListing(recents = 0, collapsed = true))
  }
}
