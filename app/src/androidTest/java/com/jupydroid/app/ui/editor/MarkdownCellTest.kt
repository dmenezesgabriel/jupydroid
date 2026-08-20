package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
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

  /**
   * `toc:display-numbering` puts the number **in the document**, which is what that command's own
   * label promises - the reference writes it into the rendered heading itself
   * (`packages/notebook/src/toc.ts:733-748`), not only into the panel.
   */
  @Test
  fun aNumberedHeadingCarriesItsNumberInTheDocument() {
    compose.setContent {
      MarkdownCell(
        rememberTextFieldState("## Results"),
        "## Results",
        rendered = true,
        {},
        {},
        headingPrefix = "1.2. ",
      )
    }

    compose.onNodeWithText("1.2. Results").assertIsDisplayed()
  }

  @Test
  fun rendersTheMarkdownRatherThanItsSource() {
    compose.setContent { MarkdownCell(rememberTextFieldState("## Results"), "## Results", rendered = true, {}, {}) }

    // The heading's text without its hashes is the whole point of rendering.
    compose.onNodeWithText("Results").assertIsDisplayed()
    compose.onNodeWithText("## Results").assertDoesNotExist()
  }

  @Test
  fun tappingTheRenderedCellAsksForTheEditor() {
    var asked = false
    compose.setContent {
      MarkdownCell(rememberTextFieldState("# Title"), "# Title", rendered = true, { asked = true }, {})
    }

    compose.onNodeWithContentDescription(RENDERED_MARKDOWN).performClick()

    compose.waitUntil(TIMEOUT_MILLIS) { asked }
  }

  /**
   * Asking for the editor is not the same as being able to type in it. The tap used to leave an
   * unfocused field, so the next keystroke went nowhere - invisible to every test here, because
   * `performTextInput` focuses the node itself, and caught only by `a1-markdown` on the tablet.
   *
   * The tap flips both flags, which is what `withMarkdownEditorOpened` does: it opens the editor
   * **and** asks for the caret, and only a tap does the second.
   */
  @Test
  fun openingTheEditorPutsTheCursorInIt() {
    var rendered by mutableStateOf(true)
    var takesCaret by mutableStateOf(false)
    compose.setContent {
      MarkdownCell(
        rememberTextFieldState("# Title"),
        "# Title",
        rendered,
        onEditRequested = {
          rendered = false
          takesCaret = true
        },
        onSourceEdited = {},
        takesCaret = takesCaret,
      )
    }

    compose.onNodeWithContentDescription(RENDERED_MARKDOWN).performClick()

    compose.onNodeWithText("# Title").assertIsFocused()
  }

  /**
   * **And a cell unrendered by a command does not.** `changeCellType` unrenders every cell it
   * visits and ends in command mode (`packages/notebook/src/actions.tsx:3308-3314`), so a run
   * changed to markdown composes several of these at once - each one asking for the caret was the
   * soft keyboard coming up over a notebook nobody was typing in
   * (`evidence/p2e-3-change-to-markdown/`).
   */
  @Test
  fun anEditorNobodyAskedForLeavesTheCaretAlone() {
    compose.setContent {
      MarkdownCell(rememberTextFieldState("# Title"), "# Title", rendered = false, {}, {})
    }

    compose.onNodeWithText("# Title").assertIsNotFocused()
  }

  /** Rendered empty markdown is nothing at all, which would be a row with no way into it. */
  @Test
  fun anEmptyCellStillOffersSomethingToTap() {
    compose.setContent { MarkdownCell(rememberTextFieldState(""), "", rendered = true, {}, {}) }

    compose.onNodeWithText(MARKDOWN_PLACEHOLDER).assertIsDisplayed()
  }

  @Test
  fun editingShowsTheSourceAgain() {
    compose.setContent { MarkdownCell(rememberTextFieldState("## Results"), "## Results", rendered = false, {}, {}) }

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
      MarkdownCell(
        state = rememberTextFieldState("start"),
        source = source,
        rendered = rendered,
        onEditRequested = { rendered = false },
        onSourceEdited = { source = it },
      )
    }

    compose.onNodeWithText("start").performTextInput(" and more")
    rendered = true

    compose.waitUntil(TIMEOUT_MILLIS) { source == "start and more" }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
