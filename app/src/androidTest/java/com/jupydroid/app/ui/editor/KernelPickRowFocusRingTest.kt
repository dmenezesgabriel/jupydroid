package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.KernelPickOption
import com.jupydroid.app.theme.FocusRingEdgeProbe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A kernel picker row that takes keyboard focus draws the palette's `focusBorder` on its edge.
 *
 * The `RadioButton` carries no callback, so the focusable is the row's `selectable`, which reads
 * `LocalIndication`: the row is what is measured. What is compared, and why, is `FocusRingEdgeProbe`'s.
 */
@RunWith(AndroidJUnit4::class)
class KernelPickRowFocusRingTest {
  @get:Rule val compose = createComposeRule()
  private val probe = FocusRingEdgeProbe(compose)

  @Test
  fun aFocusedKernelRowDrawsTheRingInDark() = assertKeyboardFocusDrawsTheRing(darkTheme = true)

  @Test
  fun aFocusedKernelRowDrawsTheRingInLight() = assertKeyboardFocusDrawsTheRing(darkTheme = false)

  @Test
  fun aFingerTapOnAKernelRowDrawsNoRing() {
    showRow(darkTheme = true)

    probe.assertFingerTapDrawsNoRing(compose.onNodeWithText(LABEL))
  }

  private fun assertKeyboardFocusDrawsTheRing(darkTheme: Boolean) {
    showRow(darkTheme)

    probe.assertKeyboardFocusDrawsTheRing(compose.onNodeWithText(LABEL))
  }

  private fun showRow(darkTheme: Boolean) = probe.show(darkTheme) {
    KernelPickRow(KernelPickOption(text = LABEL, choice = null), picked = false, onClick = {})
  }

  private companion object {
    const val LABEL = "Python 3 (ipykernel)"
  }
}
