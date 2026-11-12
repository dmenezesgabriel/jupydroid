package com.jupydroid.app.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.FocusRingEdgeProbe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A row of the one-pane More rail that takes keyboard focus draws the palette's `focusBorder` on its
 * edge.
 *
 * `WideNavigationRailItem` draws its own ripple and reads no `LocalIndication` (`NavigationItem.kt` in
 * material3 1.4.0), so `FocusRingTest`'s ring does not reach it. What is compared, and why, is
 * `FocusRingEdgeProbe`'s.
 */
@RunWith(AndroidJUnit4::class)
class MoreRailFocusRingTest {
  @get:Rule val compose = createComposeRule()
  private val probe = FocusRingEdgeProbe(compose)

  @Test
  fun aFocusedMoreRailRowDrawsTheRingInDark() = assertKeyboardFocusDrawsTheRing(darkTheme = true)

  @Test
  fun aFocusedMoreRailRowDrawsTheRingInLight() = assertKeyboardFocusDrawsTheRing(darkTheme = false)

  @Test
  fun aFingerTapOnAMoreRailRowDrawsNoRing() {
    showRow(darkTheme = true)

    probe.assertFingerTapDrawsNoRing(compose.onNodeWithText(LABEL))
  }

  private fun assertKeyboardFocusDrawsTheRing(darkTheme: Boolean) {
    showRow(darkTheme)

    probe.assertKeyboardFocusDrawsTheRing(compose.onNodeWithText(LABEL))
  }

  private fun showRow(darkTheme: Boolean) = probe.show(darkTheme) {
    MoreRailRow(LABEL, Icons.Filled.Terminal, onClick = {})
  }

  private companion object {
    const val LABEL = "New terminal"
  }
}
