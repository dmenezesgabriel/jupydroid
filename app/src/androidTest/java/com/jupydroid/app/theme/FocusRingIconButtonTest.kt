package com.jupydroid.app.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An icon button that takes keyboard focus draws the palette's `focusBorder` on its edge.
 *
 * material3 1.4.0's `IconButton` calls `ripple()` itself, so `FocusRingTest`'s ring does not reach
 * it: a Tab on the tablet changed one circular plate at 1.14:1 and nothing else.
 *
 * **The edge is compared with the same pixel before focus.** The plate is a 40dp circle inside the
 * 48dp touch target, so the top-centre pixel of the target is the surface both before and after
 * focus; what focus adds there is the ring and nothing else. (The rail's item cannot use this
 * baseline: its plate covers the item's whole edge.)
 *
 * **Keyboard mode is switched on first**, for the reason `FocusRingTest` gives; the touch case taps
 * and asserts the edge did not change, because a finger focuses some controls without a Tab.
 */
@OptIn(ExperimentalComposeUiApi::class) // `InputModeManager.requestInputMode`'s own opt-in requirement
@RunWith(AndroidJUnit4::class)
class FocusRingIconButtonTest {
  @get:Rule val compose = createComposeRule()

  private var ring = Color.Unspecified
  private lateinit var inputMode: InputModeManager

  @Test
  fun aFocusedIconButtonDrawsTheRingInDark() = assertKeyboardFocusDrawsTheRing(darkTheme = true)

  @Test
  fun aFocusedIconButtonDrawsTheRingInLight() = assertKeyboardFocusDrawsTheRing(darkTheme = false)

  @Test
  fun aFingerTapOnAnIconButtonDrawsNoRing() {
    showButton(darkTheme = true)
    val untouched = capture()

    compose.onNodeWithContentDescription(LABEL).performClick()
    compose.waitForIdle()

    assertEquals("a tap drew a ring on the edge", topEdge(untouched), topEdge(capture()))
  }

  private fun assertKeyboardFocusDrawsTheRing(darkTheme: Boolean) {
    showButton(darkTheme)
    val untouched = capture()

    compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
    compose.onNodeWithContentDescription(LABEL).performSemanticsAction(SemanticsActions.RequestFocus)
    compose.waitForIdle()
    compose.onNodeWithContentDescription(LABEL).assertIsFocused()

    assertChannelsClose(ring.compositeOver(topEdge(untouched)), topEdge(capture()))
  }

  private fun showButton(darkTheme: Boolean) {
    compose.setContent {
      JupyterDroidTheme(darkTheme = darkTheme) {
        ring = chromePalette.focusBorder
        inputMode = LocalInputModeManager.current
        Box(Modifier.testTag(TOUCH_TARGET)) {
          FocusRingIconButton(onClick = {}) { Icon(Icons.Filled.Add, contentDescription = LABEL) }
        }
      }
    }
    compose.waitForIdle()
  }

  /**
   * The box around the button, because the button's own node is the 40dp circle and the ring is
   * drawn on the 48dp touch target around it.
   */
  private fun capture(): PixelMap = compose.onNodeWithTag(TOUCH_TARGET).captureToImage().toPixelMap()

  private fun topEdge(pixels: PixelMap): Color = pixels[pixels.width / 2, 1]

  private fun assertChannelsClose(expected: Color, actual: Color) {
    val worst = listOf(
      expected.red - actual.red,
      expected.green - actual.green,
      expected.blue - actual.blue,
    ).maxOf { kotlin.math.abs(it) }
    assertEquals("edge $actual is not the ring $expected", 0f, worst, ROUNDING_TOLERANCE)
  }

  private companion object {
    const val LABEL = "Add"
    const val TOUCH_TARGET = "icon-button-touch-target"

    /** One 8-bit step and a little more: the capture is quantised, the composite is a float. */
    const val ROUNDING_TOLERANCE = 0.012f
  }
}
