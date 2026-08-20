package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A cell's hits are found in the **model's** source, and drawn over the **field's** text - two
 * copies that are the same for all but 150 ms at a time ([reportCellEdits]'s debounce). So a range
 * can name characters the field has not got, and `TextFieldBuffer.addStyle` past the end throws.
 */
class CellSearchHighlightTest {
  @Test
  fun aRangeInsideTheTextIsKept() {
    val kept = CellSearchHighlight(matches = listOf(0..2), current = 0..2).within(length = 8)
    assertEquals(listOf(0..2), kept.matches)
    assertEquals(0..2, kept.current)
  }

  /** The very last character is inside it: a range is inclusive and `addStyle`'s end is not. */
  @Test
  fun aRangeEndingOnTheLastCharacterIsKept() {
    assertEquals(listOf(5..7), CellSearchHighlight(matches = listOf(5..7)).within(length = 8).matches)
  }

  /** What a cell that just had text deleted from it looks like for one debounce. */
  @Test
  fun aRangePastTheEndIsDroppedRatherThanClamped() {
    assertEquals(emptyList<IntRange>(), CellSearchHighlight(matches = listOf(5..9)).within(length = 8).matches)
  }

  @Test
  fun theCurrentMatchIsDroppedOnTheSameRule() {
    assertNull(CellSearchHighlight(matches = listOf(0..2), current = 5..9).within(length = 8).current)
  }

  @Test
  fun nothingToDrawSurvivesAnyLength() {
    assertEquals(CellSearchHighlight.NONE, CellSearchHighlight.NONE.within(length = 0))
  }
}
