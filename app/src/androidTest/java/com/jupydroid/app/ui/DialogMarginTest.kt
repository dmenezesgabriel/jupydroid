package com.jupydroid.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.extension.api.DeleteFileDialog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A dialog whose text wraps keeps a margin to the window's edges on a compact window.
 *
 * Compose's `Dialog` draws no window background to inset, and M3's `BasicAlertDialog` is
 * `sizeIn(280dp, 560dp)` with nothing outside it, so a wrapped dialog filled a 320dp window edge to edge
 * where Material Components' own keeps 24dp (`mtrl_alert_dialog_background_inset_start`, 1.13.0). The
 * surface pads its text by 24dp, so the text starts 24dp + the margin from the dialog window's edge, and
 * a wrapped dialog's window is the screen's whole width.
 */
@RunWith(AndroidJUnit4::class)
class DialogMarginTest {
  @get:Rule val compose = createComposeRule()

  private val longName = "a-notebook-whose-name-is-long-enough-to-make-the-question-wrap-onto-two-lines.ipynb"
  private val question = "Save changes in \"$longName\" before closing?"

  @Test
  fun aWrappedDialogKeepsItsMarginToBothEdges() {
    compose.setContent {
      JupydroidTheme { SaveBeforeCloseDialog(longName, onSave = {}, onDiscard = {}, onCancel = {}) }
    }
    compose.onNodeWithText(question).assertIsDisplayed()

    val window = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
    val text = compose.onNodeWithText(question).fetchSemanticsNode().boundsInRoot
    val inset = with(compose.density) { SURFACE_PADDING_AND_MARGIN.toPx() }
    assertTrue("text left ${text.left}px is at least ${inset}px in", text.left >= inset)
    assertTrue("text right ${text.right}px is at most ${window.right - inset}px", text.right <= window.right - inset)
  }

  /** `DeleteFileDialog` lives in `:extension-api`, which cannot import the app's wrapper, so it owns a check. */
  @Test
  fun aWrappedDeleteDialogKeepsItsMarginToBothEdges() {
    val question = "Are you sure you want to permanently delete: $longName?"
    compose.setContent {
      JupydroidTheme { DeleteFileDialog(longName, onDelete = {}, onDismiss = {}) }
    }
    compose.onNodeWithText(question).assertIsDisplayed()

    val window = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
    val text = compose.onNodeWithText(question).fetchSemanticsNode().boundsInRoot
    val inset = with(compose.density) { SURFACE_PADDING_AND_MARGIN.toPx() }
    assertTrue("text left ${text.left}px is at least ${inset}px in", text.left >= inset)
    assertTrue("text right ${text.right}px is at most ${window.right - inset}px", text.right <= window.right - inset)
  }

  private companion object {
    /** The surface's 24dp text padding plus the 24dp margin this test is about. */
    val SURFACE_PADDING_AND_MARGIN = 48.dp
  }
}
