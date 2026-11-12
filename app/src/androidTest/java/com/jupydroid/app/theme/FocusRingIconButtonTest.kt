package com.jupydroid.app.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An icon button that takes keyboard focus draws the palette's `focusBorder` on its edge.
 *
 * material3 1.4.0's `IconButton` calls `ripple()` itself, so `FocusRingTest`'s ring does not reach
 * it: a Tab on the tablet changed one circular plate at 1.14:1 and nothing else. What is compared,
 * and why, is `FocusRingEdgeProbe`'s.
 */
@RunWith(AndroidJUnit4::class)
class FocusRingIconButtonTest {
  @get:Rule val compose = createComposeRule()
  private val probe = FocusRingEdgeProbe(compose)

  @Test
  fun aFocusedIconButtonDrawsTheRingInDark() = assertKeyboardFocusDrawsTheRing(darkTheme = true)

  @Test
  fun aFocusedIconButtonDrawsTheRingInLight() = assertKeyboardFocusDrawsTheRing(darkTheme = false)

  @Test
  fun aFingerTapOnAnIconButtonDrawsNoRing() {
    showButton(darkTheme = true)

    probe.assertFingerTapDrawsNoRing(compose.onNodeWithContentDescription(LABEL))
  }

  private fun assertKeyboardFocusDrawsTheRing(darkTheme: Boolean) {
    showButton(darkTheme)

    probe.assertKeyboardFocusDrawsTheRing(compose.onNodeWithContentDescription(LABEL))
  }

  private fun showButton(darkTheme: Boolean) = probe.show(darkTheme) {
    FocusRingIconButton(onClick = {}) { Icon(Icons.Filled.Add, contentDescription = LABEL) }
  }

  private companion object {
    const val LABEL = "Add"
  }
}
