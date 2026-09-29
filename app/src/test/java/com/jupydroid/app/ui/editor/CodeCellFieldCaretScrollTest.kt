package com.jupydroid.app.ui.editor

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * once a code cell can scroll horizontally, the completion popup's own
 * caret rect has to move with it - [CompletionPopupPositionTest] already proves the popup itself
 * is a pure function of its anchor, so the anchor it is handed must already carry the live scroll
 * offset rather than the field's unscrolled layout position.
 */
class CodeCellFieldCaretScrollTest {
  private val rect = IntRect(left = 120, top = 10, right = 122, bottom = 50)

  @Test
  fun noScrollLeavesTheRectWhereItWas() {
    assertEquals(rect, rect.shiftedBy(0))
  }

  @Test
  fun aPositiveScrollOffsetShiftsTheRectLeft() {
    assertEquals(IntRect(left = 20, top = 10, right = 22, bottom = 50), rect.shiftedBy(100))
  }
}
