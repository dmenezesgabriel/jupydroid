package com.jupydroid.app.ui.editor

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the completion popup goes, given the caret and how much of the window the keyboard has
 * taken.
 *
 * It used to be `Popup(offset = caretBottom)` - a raw offset with no knowledge of either. On a
 * cell near the bottom of the notebook that put a 220dp list **behind the keyboard**: with `co`
 * typed on the tablet, `compile`, `complex`, `continue` and `copyright` were readable and
 * `%code_wrap` and everything under it were drawn over the keys.
 *
 * A `PopupPositionProvider` is the seam Compose offers for this, and it is a pure function of
 * four inputs - which is why the rules can be argued here rather than only looked at.
 */
class CompletionPopupPositionTest {
  private val window = IntSize(width = 1000, height = 800)

  /** The cell's input area, somewhere down the notebook. */
  private val anchor = IntRect(left = 100, top = 300, right = 900, bottom = 360)

  /** A caret on the field's first line: 40px tall, 20px in from the field's left edge. */
  private val caret = IntRect(left = 20, top = 0, right = 22, bottom = 40)

  @Test
  fun sitsUnderTheCaretWhenThereIsRoom() {
    val at = position(popup = IntSize(280, 120), keyboard = 0)

    assertEquals(IntOffset(x = 120, y = 340), at)
  }

  /**
   * The case the keyboard creates. Below the caret there are 800 - 340 - 380 = 80px, and the
   * popup wants 220 - so it goes above instead, sitting on the caret's top edge.
   */
  @Test
  fun flipsAboveTheCaretWhenTheKeyboardTakesTheRoomBelow() {
    val at = position(popup = IntSize(280, 220), keyboard = 380)

    assertEquals("above the caret top, not below its bottom", IntOffset(x = 120, y = 80), at)
  }

  /** Without the keyboard the same popup fits below, so the flip is about space and nothing else. */
  @Test
  fun theSamePopupStaysBelowWithNoKeyboard() {
    val at = position(popup = IntSize(280, 220), keyboard = 0)

    assertEquals(IntOffset(x = 120, y = 340), at)
  }

  /**
   * Flipped up but still too tall: pinned to the top of the window rather than allowed off it.
   * Some of the list is unreachable either way, and the top is where the first - and likeliest -
   * match is.
   */
  @Test
  fun clampsToTheTopWhenItFitsNeitherWay() {
    val at = position(popup = IntSize(280, 700), keyboard = 380)

    assertEquals(0, at.y)
  }

  @Test
  fun followsTheCaretAcrossTheLine() {
    val at = position(popup = IntSize(280, 120), keyboard = 0, caret = caret.copy(left = 500))

    assertEquals(600, at.x)
  }

  /** A caret near the right edge must not push the list off the window. */
  @Test
  fun staysInsideTheRightEdge() {
    val at = position(popup = IntSize(280, 120), keyboard = 0, caret = caret.copy(left = 780))

    assertEquals(window.width - 280, at.x)
  }

  /**
   * Signature help prefers **above** - VS Code's `[ABOVE, BELOW]` preference
   * (`parameterHintsWidget.ts:177`), which also keeps it clear of the completion list that
   * takes the space below the caret for itself.
   */
  @Test
  fun preferAbove_sitsAboveTheCaretWhenThereIsRoom() {
    val at = position(popup = IntSize(280, 120), keyboard = 0, preferAbove = true)

    // Caret top at 300, popup 120 tall: its bottom rests on the caret's top edge.
    assertEquals(IntOffset(x = 120, y = 180), at)
  }

  /** No room above flips it below, just as `BELOW` is the second preference. */
  @Test
  fun preferAbove_flipsBelowTheCaretWhenTheSpaceAboveIsTaken() {
    val at = position(
      popup = IntSize(280, 340),
      keyboard = 0,
      preferAbove = true,
      anchor = IntRect(left = 100, top = 100, right = 900, bottom = 160),
    )

    // Above the caret top (100) there is only 100px and the popup wants 340 - below it goes.
    assertEquals(IntOffset(x = 120, y = 140), at)
  }

  private fun position(
    popup: IntSize,
    keyboard: Int,
    caret: IntRect = this.caret,
    preferAbove: Boolean = false,
    anchor: IntRect = this.anchor,
  ): IntOffset = CompletionPopupPosition(caret = caret, bottomInset = keyboard, preferAbove = preferAbove)
    .calculatePosition(anchor, window, LayoutDirection.Ltr, popup)
}
