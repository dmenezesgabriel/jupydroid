package com.jupydroid.app.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How far a group of equal items may sit off centre because their widths are whole pixels, which is a
 * fact about layout rounding and needs no composition: the cases are the two densities the bar's own
 * test meets, and the offset `theItemsGroupInTheMiddleOnAMediumScreen` measured.
 */
class PixelRoundingSlackTest {
  /** `dev36` forced to 800dp: 360px across, so one pixel is 1 / 0.45 dp. */
  private val forcedEmulatorDpPerPixel = 1f / 0.45f

  /** The tablet at 800dp: 1200px across, 1.5 px per dp. */
  private val tabletDpPerPixel = 1f / 1.5f

  /** The group sat 3.33dp left of centre at 800dp on `dev36`: five items floor 57.6px to 57px. */
  @Test
  fun fiveItemsMayBeOffCentreByTwoPixelsAndTheFloorOfTheEmulatorWasThatMuch() {
    val measuredOffsetDp = 3.3333f

    assertTrue(centreSlackDp(5, forcedEmulatorDpPerPixel) >= measuredOffsetDp)
  }

  @Test
  fun theSlackIsHalfTheLeftoverPixelsOfFlooringEveryItem() {
    assertEquals(2f * forcedEmulatorDpPerPixel, centreSlackDp(5, forcedEmulatorDpPerPixel), 0.001f)
    assertEquals(2f * tabletDpPerPixel, centreSlackDp(5, tabletDpPerPixel), 0.001f)
  }

  /** A group moved by a whole item, 126.667dp at either density, is a real offset and must still fail. */
  @Test
  fun aGroupMovedByAWholeItemIsOutsideTheSlackAtEitherDensity() {
    val itemWidthDp = 126.667f

    assertTrue(centreSlackDp(5, forcedEmulatorDpPerPixel) < itemWidthDp / 2)
    assertTrue(centreSlackDp(5, tabletDpPerPixel) < itemWidthDp / 2)
  }

  @Test
  fun aSingleItemHasNothingToRound() {
    assertEquals(0f, centreSlackDp(1, forcedEmulatorDpPerPixel), 0f)
  }
}
