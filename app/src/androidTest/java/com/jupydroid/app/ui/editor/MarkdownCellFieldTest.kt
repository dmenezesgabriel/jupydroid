package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for audit finding A1: markdown cells were rendered as read-only `Text`, so an
 * existing one could not be edited and a freshly inserted one was a bare header with nothing
 * under it - invisible and unfillable.
 */
@RunWith(AndroidJUnit4::class)
class MarkdownCellFieldTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun showsTheSourceItWasGiven() {
    compose.setContent { MarkdownCellField(source = "# heading", onSourceEdited = {}) }

    compose.onNodeWithText("# heading").assertIsDisplayed()
  }

  /** The bug in one assertion: an empty markdown cell must never be an invisible row. */
  @Test
  fun showsAPlaceholderWhileEmptySoTheCellIsNeverInvisible() {
    compose.setContent { MarkdownCellField(source = "", onSourceEdited = {}) }

    compose.onNodeWithText(MARKDOWN_PLACEHOLDER).assertIsDisplayed()
  }

  @Test
  fun acceptsTypingAndReportsWhatWasTyped() {
    var reported: String? = null
    compose.setContent { MarkdownCellField(source = "", onSourceEdited = { reported = it }) }

    compose.onNodeWithText(MARKDOWN_PLACEHOLDER).performClick()
    compose.onNodeWithText(MARKDOWN_PLACEHOLDER).performTextInput("## notes")

    compose.waitUntil(TIMEOUT_MILLIS) { reported == "## notes" }
  }

  @Test
  fun dropsThePlaceholderOnceThereIsText() {
    compose.setContent { MarkdownCellField(source = "written", onSourceEdited = {}) }

    compose.onNodeWithText(MARKDOWN_PLACEHOLDER).assertDoesNotExist()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
