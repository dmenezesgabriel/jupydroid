package com.jupydroid.app.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Reads the top edge of one control's touch target before and after it takes focus, so a test can
 * say whether the palette's `focusBorder` was drawn there.
 *
 * **The edge is compared with the same pixel before focus.** What focus adds on the top-centre
 * pixel of the target is the ring and nothing else, whatever the control draws inside it (an icon
 * button's 40dp plate leaves the target's edge bare). The capture is of a box around the control,
 * because the control's own node can be smaller than the 48dp target the ring is drawn on.
 *
 * **Keyboard mode is switched on first**, for the reason `FocusRingTest` gives; the touch case taps
 * and asserts the edge is not the ring, because a finger focuses some controls without a Tab.
 *
 * `edgeOf` names a different node to read when the control is not the thing to measure around (a card
 * that draws its own border, say); it defaults to the box.
 *
 * ```
 * probe.show(darkTheme = true) { FocusRingIconButton(onClick = {}) { Icon(…) } }
 * probe.assertKeyboardFocusDrawsTheRing(compose.onNodeWithContentDescription("Add"))
 * ```
 */
@OptIn(ExperimentalComposeUiApi::class) // `InputModeManager.requestInputMode`'s own opt-in requirement
internal class FocusRingEdgeProbe(private val compose: ComposeContentTestRule) {
  private var ring = Color.Unspecified
  private lateinit var inputMode: InputModeManager

  fun show(darkTheme: Boolean, control: @Composable () -> Unit) {
    compose.setContent {
      JupydroidTheme(darkTheme = darkTheme) {
        ring = chromePalette.focusBorder
        inputMode = LocalInputModeManager.current
        Box(Modifier.testTag(TOUCH_TARGET)) { control() }
      }
    }
    compose.waitForIdle()
  }

  fun assertKeyboardFocusDrawsTheRing(control: SemanticsNodeInteraction, edgeOf: SemanticsNodeInteraction = target()) {
    val untouched = capture(edgeOf)

    compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
    control.performSemanticsAction(SemanticsActions.RequestFocus)
    compose.waitForIdle()
    control.assertIsFocused()

    val edge = topEdge(capture(edgeOf))
    assertEquals("edge $edge is not the ring", 0f, gapFromRing(untouched, edge), RING_PRESENT_TOLERANCE)
  }

  fun assertFingerTapDrawsNoRing(control: SemanticsNodeInteraction, edgeOf: SemanticsNodeInteraction = target()) {
    val untouched = capture(edgeOf)

    control.performClick()
    compose.waitForIdle()

    val edge = topEdge(capture(edgeOf))
    assertTrue("a tap drew a ring on the edge: $edge", gapFromRing(untouched, edge) > RING_ABSENT_MARGIN)
  }

  private fun target(): SemanticsNodeInteraction = compose.onNodeWithTag(TOUCH_TARGET)

  private fun capture(edgeOf: SemanticsNodeInteraction): PixelMap = edgeOf.captureToImage().toPixelMap()

  private fun topEdge(pixels: PixelMap): Color = pixels[pixels.width / 2, 1]

  /**
   * How far [edge] is from the ring composited over what the edge was before focus: the widest
   * channel difference, 0 when the ring is there.
   */
  private fun gapFromRing(untouched: PixelMap, edge: Color): Float {
    val expected = ring.compositeOver(topEdge(untouched))
    return listOf(expected.red - edge.red, expected.green - edge.green, expected.blue - edge.blue)
      .maxOf { kotlin.math.abs(it) }
  }

  private companion object {
    const val TOUCH_TARGET = "focus-ring-touch-target"

    /**
     * The ring's edge is the ring composited over what was there, to within the capture's 8-bit
     * quantisation and, on a control whose own state layer shows through Dark's 70% ring, a shift
     * of up to 0.032 on a channel (measured at 0.0157 on a card, 0.0314 on a menu item and a More
     * rail row). A missing ring leaves the edge 0.43 or more from the ring, so the margin is wide.
     */
    const val RING_PRESENT_TOLERANCE = 0.04f

    /**
     * A tap may still lighten the edge with Material's own state layer (a card's fills its whole
     * edge, 0.03 on a channel in Dark), so "no ring" means the edge is nowhere near the ring, which
     * is 0.4 or more away on the palettes' surfaces.
     */
    const val RING_ABSENT_MARGIN = 0.1f
  }
}
