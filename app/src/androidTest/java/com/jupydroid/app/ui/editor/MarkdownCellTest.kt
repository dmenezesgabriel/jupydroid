package com.jupydroid.app.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Audit A1c: markdown renders, and there is a way back to the source and forward again. */
@RunWith(AndroidJUnit4::class)
class MarkdownCellTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun rendersTheMarkdownRatherThanItsSource() {
    compose.setContent { MarkdownCell("## Results", rendered = true, {}, {}) }

    // The heading's text without its hashes is the whole point of rendering.
    compose.onNodeWithText("Results").assertIsDisplayed()
    compose.onNodeWithText("## Results").assertDoesNotExist()
  }

  @Test
  fun tappingTheRenderedCellAsksForTheEditor() {
    var asked = false
    compose.setContent { MarkdownCell("# Title", rendered = true, { asked = true }, {}) }

    compose.onNodeWithContentDescription(RENDERED_MARKDOWN).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { asked }
  }

  /**
   * Asking for the editor is not the same as being able to type in it. The tap used to leave an
   * unfocused field, so the next keystroke went nowhere - invisible to every test here, because
   * `performTextInput` focuses the node itself, and caught only by `a1-markdown` on the tablet.
   */
  @Test
  fun openingTheEditorPutsTheCursorInIt() {
    var rendered by mutableStateOf(true)
    compose.setContent {
      MarkdownCell("# Title", rendered, onEditRequested = { rendered = false }, onSourceEdited = {})
    }

    compose.onNodeWithContentDescription(RENDERED_MARKDOWN).performClick()

    compose.onNodeWithText("# Title").assertIsFocused()
  }

  /** Rendered empty markdown is nothing at all, which would be a row with no way into it. */
  @Test
  fun anEmptyCellStillOffersSomethingToTap() {
    compose.setContent { MarkdownCell("", rendered = true, {}, {}) }

    compose.onNodeWithText(MARKDOWN_PLACEHOLDER).assertIsDisplayed()
  }

  @Test
  fun editingShowsTheSourceAgain() {
    compose.setContent { MarkdownCell("## Results", rendered = false, {}, {}) }

    compose.onNodeWithText("## Results").assertIsDisplayed()
  }

  /**
   * The reason `ReportEdits` flushes on dispose: the edit is debounced, and leaving the editor
   * for the rendered view within that window used to drop the last keystrokes.
   */
  @Test
  fun switchingBackToRenderedKeepsWhatWasJustTyped() {
    var source by mutableStateOf("start")
    var rendered by mutableStateOf(false)
    compose.setContent {
      MarkdownCell(source, rendered, onEditRequested = { rendered = false }, onSourceEdited = { source = it })
    }

    compose.onNodeWithText("start").performTextInput(" and more")
    rendered = true

    compose.waitUntil(TIMEOUT_MILLIS) { source == "start and more" }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
