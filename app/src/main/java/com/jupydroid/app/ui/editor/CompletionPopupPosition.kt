package com.jupydroid.app.ui.editor

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Puts the completion list under the caret, or over it when the keyboard has taken the room
 * below.
 *
 * The popup used to be placed with a raw `Popup(offset = …)`, which knows nothing about the
 * window or the keyboard. On a cell near the bottom of a notebook that drew a 220dp list
 * **behind the keyboard**: half the matches were readable and the rest were painted over the
 * keys. A `PopupPositionProvider` is handed exactly what is
 * needed to decide - where the anchor is in the window, how big the window is, and how big the
 * popup turned out to be.
 *
 * @param caret where the text cursor is *within* the anchor, gutter included.
 * @param bottomInset how much of the window's bottom is spoken for - the keyboard, plus whatever
 *   the navigation bar takes. Passed in because `windowSize` does not shrink for the IME under
 *   edge-to-edge; the window stays full height and the keyboard sits on top of it.
 * @param preferAbove try the space over the caret first - VS Code's `[ABOVE, BELOW]`
 *   preference for parameter hints (`parameterHintsWidget.ts:177`), which also keeps that
 *   card clear of the completion list that owns the space below.
 */
internal class CompletionPopupPosition(
  private val caret: IntRect,
  private val bottomInset: Int,
  private val preferAbove: Boolean = false,
) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset {
    val below = anchorBounds.top + caret.bottom
    val roomBelow = windowSize.height - bottomInset - below
    // Above the caret's *top*, not its bottom, so the list never covers the character being
    // completed - which is the one piece of context that makes the matches make sense.
    val above = anchorBounds.top + caret.top - popupContentSize.height
    val roomAbove = anchorBounds.top + caret.top
    val y = when {
      preferAbove && popupContentSize.height <= roomAbove -> above
      !preferAbove && popupContentSize.height <= roomBelow -> below
      preferAbove -> below
      else -> above
    }
    return IntOffset(
      x = (anchorBounds.left + caret.left).coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width)),
      // Pinned to the top when it fits neither way. Some of the list is out of reach either
      // way, and the top is where the first - and likeliest - match is.
      y = y.coerceAtLeast(0),
    )
  }
}
