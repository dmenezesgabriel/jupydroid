package com.jupydroid.app.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.extension.api.RENAME_FIELD_TAG
import com.jupydroid.extension.api.RenameFileDialog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Whether the soft keyboard the dialog asks for leaves its two buttons on screen, on a phone with a
 * large font scale. Run on a phone-sized device (`dev36`): on a taller screen there is room either way.
 *
 * A refused name adds its message under the field, which at twice the font size takes five lines and
 * makes the dialog taller than the room above the keyboard. The dialog window was panned to keep the
 * field in view and nothing kept the buttons there, so Cancel and Rename sat under the keyboard.
 */
@RunWith(AndroidJUnit4::class)
class RenameFileDialogKeyboardTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun aRefusedNameKeepsBothButtonsAboveTheKeyboardAtTwiceTheFontScale() {
    showFolderRenameAt2x()

    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextReplacement("a/b")

    compose.onNodeWithText("Names must have", substring = true).assertExists()
    assertButtonsAreAboveTheKeyboard()
  }

  /** Bounding the dialog by the keyboard clips what no longer fits, so the clipped part must scroll in. */
  @Test
  fun aRefusalCutByTheKeyboardScrollsIntoView() {
    showFolderRenameAt2x()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextReplacement("a/b")
    val refusal = compose.onNodeWithText("Names must have", substring = true)

    refusal.performScrollTo()

    val container = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
    val message = refusal.fetchSemanticsNode().boundsInRoot
    assertTrue("the refusal at $message ends below its scroller at $container", message.bottom <= container.bottom)
  }

  @Test
  fun aValidNameKeepsBothButtonsAboveTheKeyboardAtTwiceTheFontScale() {
    showFolderRenameAt2x()

    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextReplacement("archive")

    assertButtonsAreAboveTheKeyboard()
  }

  private fun showFolderRenameAt2x() {
    useFontScale(2f)
    compose.setContent {
      JupyterDroidTheme {
        RenameFileDialog(currentTitle = "reports", heading = "Rename folder", onConfirm = {}, onDismiss = {})
      }
    }
  }

  /**
   * A dialog is a window composed from the activity's own resources, so `AtFontScale`, which provides
   * a density to the composition it wraps, never reaches it: the dialog stayed at 1.0 and its refusal
   * took three lines, not the five the system's 2.0 gives it.
   */
  @Suppress("DEPRECATION")
  private fun useFontScale(scale: Float) {
    val resources = compose.activity.resources
    val configuration = Configuration(resources.configuration).apply { fontScale = scale }
    resources.updateConfiguration(configuration, resources.displayMetrics)
  }

  /**
   * Waits, because the keyboard and the dialog's layout settle over a few frames, and fails with what
   * it last saw: a keyboard that never came up reads differently from buttons under one.
   */
  private fun assertButtonsAreAboveTheKeyboard() {
    var lastSeen = "nothing read"
    val settled = runCatching {
      compose.waitUntil(TIMEOUT_MILLIS) {
        val keyboardTop = softKeyboardTop()
        val buttons = BUTTONS.associateWith(::clickableBoundsInScreen)
        lastSeen = "keyboard top $keyboardTop px, buttons $buttons, texts ${textsOnScreen()}"
        keyboardTop != null && buttons.values.all { it != null && it.bottom <= keyboardTop }
      }
    }.isSuccess
    assertTrue("Cancel and Rename should end above the keyboard: $lastSeen", settled)
  }

  private companion object {
    const val TIMEOUT_MILLIS = 10_000L
    val BUTTONS = listOf("Cancel", "Rename")
  }
}
