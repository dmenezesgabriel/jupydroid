package com.jupydroid.app.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A control that takes keyboard focus draws the palette's `focusBorder` on its edge.
 *
 * Written against a measurement: Tab across ten stops changed one rounded plate behind the control
 * and nothing else, at 1.14:1 to 1.15:1 against what was there. This reads the pixels focus leaves,
 * the thing a palette test cannot say - that the colour reaches the screen.
 *
 * **The input mode is switched to keyboard first, then focus is requested.** A `clickable` is
 * focusable only in keyboard input mode: asked for in touch mode the node stayed `Focused = false`.
 * A key sent through `performKeyInput` did not switch it either - a Tab and a focus request after
 * it left the node unfocused - because the test dispatches to the focus owner directly, past the
 * view that notes a key event. What a hardware Tab reaches, control by control, is the tablet
 * flow's question; this pins what a control that *has* focus draws.
 *
 * **The edge is compared with the control's own interior after focus**, so what is asserted is
 * the ring and not whatever else a focused control draws.
 */
@OptIn(ExperimentalComposeUiApi::class) // `InputModeManager.requestInputMode`'s own opt-in requirement
@RunWith(AndroidJUnit4::class)
class FocusRingTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun darkFocusDrawsTheRingOnTheEdge() = assertFocusDrawsTheRing(darkTheme = true)

  @Test
  fun lightFocusDrawsTheRingOnTheEdge() = assertFocusDrawsTheRing(darkTheme = false)

  private fun assertFocusDrawsTheRing(darkTheme: Boolean) {
    var ring = Color.Unspecified
    lateinit var inputMode: InputModeManager
    compose.setContent {
      JupyterDroidTheme(darkTheme = darkTheme) {
        ring = chromePalette.focusBorder
        inputMode = LocalInputModeManager.current
        Box(
          Modifier.size(96.dp).background(chromePalette.sideBarBackground).testTag(TARGET).clickable {},
        )
      }
    }
    compose.waitForIdle()
    val untouched = capture()

    compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
    compose.onNodeWithTag(TARGET).performSemanticsAction(SemanticsActions.RequestFocus)
    compose.waitForIdle()
    compose.onNodeWithTag(TARGET).assertIsFocused()
    val focused = capture()

    val edge = focused[focused.width / 2, 1]
    val interior = focused[focused.width / 2, focused.height / 2]
    assertEquals("before focus the edge is the surface", untouched[untouched.width / 2, 1], untouched[3, 3])
    assertChannelsClose(ring.compositeOver(interior), edge, "interior $interior")
  }

  private fun capture(): PixelMap = compose.onNodeWithTag(TARGET).captureToImage().toPixelMap()

  private fun assertChannelsClose(expected: Color, actual: Color, context: String) {
    val worst = listOf(
      expected.red - actual.red,
      expected.green - actual.green,
      expected.blue - actual.blue,
    ).maxOf { kotlin.math.abs(it) }
    assertEquals("edge $actual is not the ring $expected ($context)", 0f, worst, ROUNDING_TOLERANCE)
  }

  private companion object {
    const val TARGET = "focus-ring-target"

    /** One 8-bit step and a little more: the capture is quantised, the composite is a float. */
    const val ROUNDING_TOLERANCE = 0.012f
  }
}
