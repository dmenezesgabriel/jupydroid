package com.jupydroid.app.ui.shell

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.ShownPanel
import com.jupydroid.app.SidePanel
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.theme.chromePalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A rail item that takes keyboard focus draws the palette's `focusBorder` on its edge.
 *
 * `NavigationRailItem` selects with `indication = null` and draws its own ripple, so it never reads
 * `LocalIndication` and `FocusRingTest`'s ring does not reach it: a Tab on the tablet changed one
 * rounded plate at 1.14:1 and nothing else. This reads the pixels a focused item leaves, on a panel
 * item and on the foot's Settings, because the foot is a separate call site.
 *
 * **The edge is compared with a pixel just inside it, in the same frame.** The focus plate covers the
 * whole item, edge included (`#191A1B` to `#242526` in Dark), so the edge changes on focus with no
 * ring at all; what the ring adds is a difference between the edge and the plate beside it.
 *
 * **Keyboard mode is switched on first**, for the reason `FocusRingTest` gives; the touch case taps
 * and asserts the edge did not change, because a finger focuses some controls without a Tab.
 */
@OptIn(ExperimentalComposeUiApi::class) // `InputModeManager.requestInputMode`'s own opt-in requirement
@RunWith(AndroidJUnit4::class)
class SidePanelRailFocusRingTest {
  @get:Rule val compose = createComposeRule()

  private var ring = Color.Unspecified
  private lateinit var inputMode: InputModeManager

  @Test
  fun aFocusedPanelItemDrawsTheRingInDark() = assertKeyboardFocusDrawsTheRing(FILES, darkTheme = true)

  @Test
  fun aFocusedPanelItemDrawsTheRingInLight() = assertKeyboardFocusDrawsTheRing(FILES, darkTheme = false)

  @Test
  fun aFocusedSettingsItemDrawsTheRingInDark() = assertKeyboardFocusDrawsTheRing(SETTINGS, darkTheme = true)

  @Test
  fun aFocusedSettingsItemDrawsTheRingInLight() = assertKeyboardFocusDrawsTheRing(SETTINGS, darkTheme = false)

  @Test
  fun aFingerTapOnAnItemDrawsNoRing() {
    showRail(darkTheme = true)

    compose.onNodeWithContentDescription(FILES).performClick()
    compose.waitForIdle()

    val tapped = capture(FILES)
    assertEquals("a tap drew a ring on the edge", justInside(tapped), topEdge(tapped))
  }

  private fun assertKeyboardFocusDrawsTheRing(label: String, darkTheme: Boolean) {
    showRail(darkTheme)
    compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
    compose.onNodeWithContentDescription(label).performSemanticsAction(SemanticsActions.RequestFocus)
    compose.waitForIdle()
    compose.onNodeWithContentDescription(label).assertIsFocused()
    val focused = capture(label)

    val inside = justInside(focused)
    assertChannelsClose(ring.compositeOver(inside), topEdge(focused), "plate beside it $inside")
  }

  private fun showRail(darkTheme: Boolean) {
    compose.setContent {
      JupyterDroidTheme(darkTheme = darkTheme) {
        ring = chromePalette.focusBorder
        inputMode = LocalInputModeManager.current
        SidePanelRail(
          selected = ShownPanel.Shell(SidePanel.SEARCH),
          onChoose = {},
          onOpenLauncher = {},
          onOpenSettings = {},
          contributions = emptyList(),
        )
      }
    }
    compose.waitForIdle()
  }

  private fun capture(label: String): PixelMap =
    compose.onNodeWithContentDescription(label).captureToImage().toPixelMap()

  private fun topEdge(pixels: PixelMap): Color = pixels[pixels.width / 2, 1]

  /** Past the ring's width, so it is the plate (or the surface) and never the ring. */
  private fun justInside(pixels: PixelMap): Color =
    pixels[pixels.width / 2, with(compose.density) { JUST_INSIDE.roundToPx() }]

  private fun assertChannelsClose(expected: Color, actual: Color, context: String) {
    val worst = listOf(
      expected.red - actual.red,
      expected.green - actual.green,
      expected.blue - actual.blue,
    ).maxOf { kotlin.math.abs(it) }
    assertEquals("edge $actual is not the ring $expected ($context)", 0f, worst, ROUNDING_TOLERANCE)
  }

  private companion object {
    const val FILES = "Files"
    const val SETTINGS = "Settings"
    val JUST_INSIDE = 6.dp

    /** One 8-bit step and a little more: the capture is quantised, the composite is a float. */
    const val ROUNDING_TOLERANCE = 0.012f
  }
}
