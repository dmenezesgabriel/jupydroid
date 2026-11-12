package com.jupydroid.app.theme

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.extension.api.FocusRingDropdownMenuItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A menu item that takes keyboard focus draws the palette's `focusBorder` on its edge.
 *
 * material3 1.4.0's `DropdownMenuItem` names `ripple()` itself (`Menu.kt`), so `FocusRingIndication`
 * does not reach it: a Tab in the notebook's menu changed one plate at 1.14:1. The item is shown in
 * the test's own window, because a popup's window takes no input focus under `am instrument`; what a
 * menu draws in its popup is read on the tablet. What is compared, and why, is `FocusRingEdgeProbe`'s.
 */
@RunWith(AndroidJUnit4::class)
class FocusRingDropdownMenuItemTest {
  @get:Rule val compose = createComposeRule()
  private val probe = FocusRingEdgeProbe(compose)

  @Test
  fun aFocusedMenuItemDrawsTheRingInDark() = assertKeyboardFocusDrawsTheRing(darkTheme = true)

  @Test
  fun aFocusedMenuItemDrawsTheRingInLight() = assertKeyboardFocusDrawsTheRing(darkTheme = false)

  @Test
  fun aFingerTapOnAMenuItemDrawsNoRing() {
    showItem(darkTheme = true)

    probe.assertFingerTapDrawsNoRing(compose.onNodeWithText(LABEL))
  }

  private fun assertKeyboardFocusDrawsTheRing(darkTheme: Boolean) {
    showItem(darkTheme)

    probe.assertKeyboardFocusDrawsTheRing(compose.onNodeWithText(LABEL))
  }

  private fun showItem(darkTheme: Boolean) = probe.show(darkTheme) {
    FocusRingDropdownMenuItem(text = { Text(LABEL) }, onClick = {})
  }

  private companion object {
    const val LABEL = "Save"
  }
}
