package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.FocusRingEdgeProbe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A cell's selection tick that takes keyboard focus draws the palette's `focusBorder` on its edge.
 *
 * material3 1.4.0's `Checkbox` calls `ripple()` itself (`Checkbox.kt`), so `FocusRingTest`'s ring
 * does not reach it: a Tab changed one circular plate and nothing else. What is compared, and why,
 * is `FocusRingEdgeProbe`'s.
 */
@RunWith(AndroidJUnit4::class)
class CellSelectionCheckboxFocusRingTest {
  @get:Rule val compose = createComposeRule()
  private val probe = FocusRingEdgeProbe(compose)

  @Test
  fun aFocusedSelectionTickDrawsTheRingInDark() = assertKeyboardFocusDrawsTheRing(darkTheme = true)

  @Test
  fun aFocusedSelectionTickDrawsTheRingInLight() = assertKeyboardFocusDrawsTheRing(darkTheme = false)

  @Test
  fun aFingerTapOnASelectionTickDrawsNoRing() {
    showTick(darkTheme = true)

    probe.assertFingerTapDrawsNoRing(compose.onNodeWithContentDescription(MARK.label))
  }

  private fun assertKeyboardFocusDrawsTheRing(darkTheme: Boolean) {
    showTick(darkTheme)

    probe.assertKeyboardFocusDrawsTheRing(compose.onNodeWithContentDescription(MARK.label))
  }

  private fun showTick(darkTheme: Boolean) = probe.show(darkTheme) {
    CellSelectionCheckbox(MARK, onToggleSelected = {})
  }

  private companion object {
    val MARK = CellMark(checked = false, position = 2)
  }
}
