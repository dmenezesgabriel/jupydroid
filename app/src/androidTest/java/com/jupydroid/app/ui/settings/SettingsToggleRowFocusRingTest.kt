package com.jupydroid.app.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.FocusRingEdgeProbe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A settings toggle row that takes keyboard focus draws the palette's `focusBorder` on its edge.
 *
 * The `Switch` carries no callback, so the focusable is the row's `toggleable`, which reads
 * `LocalIndication`: the row is what is measured. What is compared, and why, is `FocusRingEdgeProbe`'s.
 */
@RunWith(AndroidJUnit4::class)
class SettingsToggleRowFocusRingTest {
  @get:Rule val compose = createComposeRule()
  private val probe = FocusRingEdgeProbe(compose)

  @Test
  fun aFocusedToggleRowDrawsTheRingInDark() = assertKeyboardFocusDrawsTheRing(darkTheme = true)

  @Test
  fun aFocusedToggleRowDrawsTheRingInLight() = assertKeyboardFocusDrawsTheRing(darkTheme = false)

  @Test
  fun aFingerTapOnAToggleRowDrawsNoRing() {
    showRow(darkTheme = true)

    probe.assertFingerTapDrawsNoRing(compose.onNodeWithText(OUTPUT_HEADINGS))
  }

  private fun assertKeyboardFocusDrawsTheRing(darkTheme: Boolean) {
    showRow(darkTheme)

    probe.assertKeyboardFocusDrawsTheRing(compose.onNodeWithText(OUTPUT_HEADINGS))
  }

  private fun showRow(darkTheme: Boolean) = probe.show(darkTheme) {
    SettingsToggleRow(OUTPUT_HEADINGS, checked = false, onCheckedChange = {})
  }
}
