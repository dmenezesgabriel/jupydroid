package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which surface a cell's actions are offered on, given how wide the window is.
 *
 * A dropdown anchored to a cell near the top of a 6" screen is a stretch for a thumb, and its
 * rows are sized for a mouse. On a tablet the same menu is fine and a sheet sliding over the
 * notebook would be heavier than the job needs. So the *model* is one list of actions and only
 * the presentation branches - which is the third of this plan's mobile principles.
 *
 * The rule is pure so it can be argued here rather than only seen; the composable that reads the
 * real window delegates to it.
 *
 * Breakpoint is the documented compact/medium boundary, 600dp. **Never a device check** - a
 * tablet in a 400dp split-screen window is a compact window, and the class changes at runtime.
 */
class CellActionPresentationTest {
  @Test
  fun aPhoneWidthWindowGetsTheSheet() {
    assertEquals(CellActionPresentation.SHEET, cellActionPresentationFor(widthDp = 411))
  }

  @Test
  fun aTabletWidthWindowGetsTheMenu() {
    assertEquals(CellActionPresentation.MENU, cellActionPresentationFor(widthDp = 1280))
  }

  /** 600dp is the medium lower bound, and "at least" includes it. */
  @Test
  fun theBoundaryItselfCountsAsRoomForAMenu() {
    assertEquals(CellActionPresentation.MENU, cellActionPresentationFor(widthDp = 600))
    assertEquals(CellActionPresentation.SHEET, cellActionPresentationFor(widthDp = 599))
  }

  /**
   * A tablet in split screen is a compact window. This is the case that makes the rule a *width*
   * rule rather than a device rule, and the one a device check would get wrong.
   */
  @Test
  fun aTabletSharingItsScreenIsTreatedAsNarrow() {
    assertEquals(CellActionPresentation.SHEET, cellActionPresentationFor(widthDp = 480))
  }
}
