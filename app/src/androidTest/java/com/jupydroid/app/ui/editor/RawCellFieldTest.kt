package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A raw cell's editor, which is the whole of what a raw cell is on screen: `RawCell` has no
 * renderer and no output area (`packages/cells/src/widget.ts:2671-2694`).
 *
 * Two of these are about what the field must *not* do, and both are things it would have inherited
 * had it been written as a copy of [MarkdownCellField].
 */
@RunWith(AndroidJUnit4::class)
class RawCellFieldTest {
  @get:Rule val compose = createComposeRule()

  /** Verbatim: nothing parses it, so what is stored is what is drawn. */
  @Test
  fun showsItsSourceExactlyAsWritten() {
    compose.setContent { RawCellField(state = rememberTextFieldState(LATEX), onSourceEdited = {}) }

    compose.onNodeWithText(LATEX).assertIsDisplayed()
  }

  /**
   * Replaced rather than appended, so the assertion does not depend on where a freshly composed
   * field happens to leave its caret - which is not what this is about.
   */
  @Test
  fun acceptsTypingAndReportsWhatWasTyped() {
    var reported: String? = null
    compose.setContent { RawCellField(state = rememberTextFieldState(LATEX), onSourceEdited = { reported = it }) }

    compose.onNodeWithText(LATEX).performTextReplacement(".. note::")

    compose.waitUntil(TIMEOUT_MILLIS) { reported == ".. note::" }
  }

  /**
   * **It must not take the caret on arrival.** [MarkdownCellField] does, because it only ever
   * appears in answer to a tap. A raw cell is in this state always, so the same line here would
   * pull the keyboard away from whatever was being typed each time one scrolled into view.
   */
  @Test
  fun doesNotTakeTheCaretJustForBeingComposed() {
    compose.setContent { RawCellField(state = rememberTextFieldState(LATEX), onSourceEdited = {}) }

    compose.onNodeWithText(LATEX).assertIsNotFocused()
  }

  /**
   * **And it has no placeholder**, which is the reference's:
   * `jp-mod-emptyMarkdownPlaceholder` is markdown's alone (`packages/cells/style/widget.css:145`).
   */
  @Test
  fun offersNoMarkdownPlaceholderWhenEmpty() {
    compose.setContent { RawCellField(state = rememberTextFieldState(""), onSourceEdited = {}) }

    compose.onNodeWithText(MARKDOWN_PLACEHOLDER).assertDoesNotExist()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
    const val LATEX = "\\begin{equation}"
  }
}
