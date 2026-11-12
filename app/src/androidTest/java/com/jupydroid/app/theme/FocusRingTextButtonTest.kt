package com.jupydroid.app.theme

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A text button that takes keyboard focus draws the palette's `focusBorder` on its edge.
 *
 * material3 1.4.0's `TextButton` is a `Surface(onClick)`, which calls `ripple()` itself, so
 * `FocusRingIndication` does not reach it: a Tab on `Run` changed one plate at 1.14:1. What is
 * compared, and why, is `FocusRingEdgeProbe`'s.
 */
@RunWith(AndroidJUnit4::class)
class FocusRingTextButtonTest {
  @get:Rule val compose = createComposeRule()
  private val probe = FocusRingEdgeProbe(compose)

  @Test
  fun aFocusedTextButtonDrawsTheRingInDark() = assertKeyboardFocusDrawsTheRing(darkTheme = true)

  @Test
  fun aFocusedTextButtonDrawsTheRingInLight() = assertKeyboardFocusDrawsTheRing(darkTheme = false)

  @Test
  fun aFingerTapOnATextButtonDrawsNoRing() {
    showButton(darkTheme = true)

    probe.assertFingerTapDrawsNoRing(compose.onNodeWithText(LABEL))
  }

  private fun assertKeyboardFocusDrawsTheRing(darkTheme: Boolean) {
    showButton(darkTheme)

    probe.assertKeyboardFocusDrawsTheRing(compose.onNodeWithText(LABEL))
  }

  private fun showButton(darkTheme: Boolean) = probe.show(darkTheme) {
    FocusRingTextButton(onClick = {}) { Text(LABEL) }
  }

  private companion object {
    const val LABEL = "Run"
  }
}
