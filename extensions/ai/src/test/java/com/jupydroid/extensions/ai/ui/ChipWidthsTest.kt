package com.jupydroid.extensions.ai.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** How the Chat's toolbar shares its row between chips of the widths their names need. */
class ChipWidthsTest {
  @Test
  fun chipsThatFitKeepTheWidthTheirNamesNeed() {
    assertEquals(listOf(100, 200, 50), chipWidths(listOf(100, 200, 50), room = 400))
  }

  @Test
  fun onlyTheLongChipGivesWayWhenTheyDoNotFit() {
    assertEquals(listOf(100, 220, 80), chipWidths(listOf(100, 500, 80), room = 400))
  }

  @Test
  fun twoLongChipsShareWhatTheShortOnesLeave() {
    assertEquals(listOf(100, 150, 150), chipWidths(listOf(100, 500, 300), room = 400))
  }

  @Test
  fun chipsAllLongerThanTheirShareSplitTheRowEvenly() {
    assertEquals(listOf(50, 50), chipWidths(listOf(100, 100), room = 100))
  }

  @Test
  fun noChipsTakeNoWidth() {
    assertEquals(emptyList<Int>(), chipWidths(emptyList(), room = 400))
  }
}
