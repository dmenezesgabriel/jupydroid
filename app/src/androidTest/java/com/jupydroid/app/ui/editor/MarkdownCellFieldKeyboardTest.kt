package com.jupydroid.app.ui.editor

import android.text.InputType
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the markdown field asks of the keyboard. Markdown is prose, but it is typed as source: on
 * SM-X216B the keyboard rewrote a typed `https://jupyter.org` as `https://Jupiter.org`. JupyterLab's
 * editor turns autocorrect, spellcheck and writing suggestions off for every cell type
 * (`@codemirror/view@6.39.14`, `contentAttrs` in `dist/index.js`).
 */
@RunWith(AndroidJUnit4::class)
class MarkdownCellFieldKeyboardTest {
  @get:Rule val compose = createComposeRule()

  /** The field never set the autocorrect flag, and the keyboard corrected anyway: this is what stops it. */
  @Test
  fun theMarkdownFieldAsksForTheVisiblePasswordVariation() {
    val variation = askedInputType() and InputType.TYPE_MASK_VARIATION
    assertEquals(
      "the field kept a variation the keyboard corrects in",
      InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
      variation,
    )
  }

  @Test
  fun theMarkdownFieldAsksTheKeyboardForNoSuggestions() {
    val noSuggestions = askedInputType() and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    assertEquals("the field left suggestions on", InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, noSuggestions)
  }

  @Test
  fun theMarkdownFieldStaysMultiLineText() {
    val shape = askedInputType() and (InputType.TYPE_MASK_CLASS or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
    assertEquals(
      "Enter must still break a line",
      InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
      shape,
    )
  }

  private fun askedInputType(): Int = compose.inputTypeAskedBy {
    MarkdownCellField(state = rememberTextFieldState("see https://jupyter.org"), onSourceEdited = {})
  }
}
