package com.jupydroid.app.ui.editor

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.toSize
import org.junit.Assert.assertTrue
import kotlin.math.abs

/**
 * The node's bounds on the *screen*, not in its window. A `Popup` is a window of its own
 * (`TYPE_APPLICATION_SUB_PANEL`), so `boundsInWindow` on a node inside it says where the node
 * sits in the popup, never where the popup sits on the screen - [SemanticsNode.positionOnScreen]
 * is the one read that puts the popup and the field it anchors to in a single space.
 */
internal fun SemanticsNode.boundsOnScreen(): Rect = Rect(positionOnScreen, size.toSize())

/**
 * A popup's placement against the rect it anchors to, asserted in the one space the two share -
 * the screen. [popup] is the popup node's [boundsOnScreen]; [inner] is the text node's (the
 * region inside `cellEditorBox`'s padding and gutter) and [box] is the padded box the `Popup`
 * anchors on.
 *
 * The failure message doubles as the read of *which* node `anchorBounds` is: `anchorTop` is the
 * top the provider placed the popup from (`popup.top` less the anchor rect's height, [inner]'s
 * whole height in these fixtures, where the anchor's bottom is the inner's bottom), printed
 * beside the two candidates.
 */
internal fun assertPopupAlignedToAnchor(
  popup: Rect,
  inner: Rect,
  box: Rect,
  /** The x the anchor rect's left edge should land at - where the test's own rect starts.
   * Null skips the check: the caret draws no node, so a caret-anchored popup has no second
   * edge a semantics read can name. */
  expectedLeft: Float? = null,
) {
  val anchorTop = popup.top - inner.height
  assertTrue(
    "popup.top=${popup.top} must clear the anchor's bottom ${inner.bottom}: the gap is " +
      "${popup.top - inner.bottom}px against a padding of ${inner.top - box.top}px, and " +
      "anchorTop=$anchorTop sits beside the two candidate origins box.top=${box.top} " +
      "and inner.top=${inner.top}",
    popup.top >= inner.bottom,
  )
  if (expectedLeft == null) return
  assertTrue(
    "popup.left=${popup.left} must sit at the anchor's left $expectedLeft",
    abs(popup.left - expectedLeft) <= 1f,
  )
}
