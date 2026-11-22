package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
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
    compose.setContent { MarkdownCellField(state = rememberTextFieldState("# heading"), onSourceEdited = {}) }

    compose.onNodeWithText("# heading").assertIsDisplayed()
  }

  /** The bug in one assertion: an empty markdown cell must never be an invisible row. */
  @Test
  fun showsAPlaceholderWhileEmptySoTheCellIsNeverInvisible() {
    compose.setContent { MarkdownCellField(state = rememberTextFieldState(""), onSourceEdited = {}) }

    compose.onNodeWithText(MARKDOWN_PLACEHOLDER).assertIsDisplayed()
  }

  @Test
  fun acceptsTypingAndReportsWhatWasTyped() {
    var reported: String? = null
    compose.setContent { MarkdownCellField(state = rememberTextFieldState(""), onSourceEdited = { reported = it }) }

    compose.onNodeWithText(MARKDOWN_PLACEHOLDER).performClick()
    compose.onNodeWithText(MARKDOWN_PLACEHOLDER).performTextInput("## notes")

    compose.waitUntil(TIMEOUT_MILLIS) { reported == "## notes" }
  }

  @Test
  fun dropsThePlaceholderOnceThereIsText() {
    compose.setContent { MarkdownCellField(state = rememberTextFieldState("written"), onSourceEdited = {}) }

    compose.onNodeWithText(MARKDOWN_PLACEHOLDER).assertDoesNotExist()
  }

  /**
   * Every cell type's editor is one font, so a markdown cell's **source** is code-shaped.
   *
   * **Both references agree, and they were checked separately.** VS Code builds a markup cell's
   * editor as a plain `CodeEditorWidget` over `getBaseCellEditorOptions(language)` - the very
   * options a code cell gets - with no font override anywhere in the file
   * (`view/cellParts/markupCell.ts:66,331`). Its one markdown-specific font setting,
   * `markupFontFamily`, is returned from `computeWebviewOptions()`
   * (`notebookOptions.ts:748-772`), which styles the **rendered** preview and never the editor.
   * JupyterLab is the same shape: `.cm-editor` takes `--jp-code-font-family`
   * (`packages/codemirror/style/base.css:5-9`) with no per-cell-type override.
   *
   * **Measured against a raw cell rather than against `CodeText`**, for the reason
   * `CellOutputsViewTest.oneOutputLineIsAsTallAsALineOfCode` records: a conversion and a layout
   * agree at `fontScale = 1.0` and part company on a device where the reader has changed the font
   * size. The claim is that the two fields lay out identically, and comparing them is the only
   * form of it that survives that setting.
   *
   * Three lines, not one: line height is half of what changes here (`bodyMedium`'s 20sp against
   * [CodeText]'s 18sp), and a single line hides a per-line difference in its padding.
   */
  @Test
  fun aMarkdownSourceLaysOutLikeARawCellsSource() {
    compose.setContent {
      JupydroidTheme {
        Column(modifier = Modifier.width(FIELD_WIDTH)) {
          MarkdownCellField(
            state = rememberTextFieldState(THREE_LINES),
            onSourceEdited = {},
            modifier = Modifier.testTag(MARKDOWN_FIELD),
          )
          RawCellField(
            state = rememberTextFieldState(THREE_LINES),
            onSourceEdited = {},
            modifier = Modifier.testTag(RAW_FIELD),
          )
        }
      }
    }

    assertEquals(
      compose.onNodeWithTag(RAW_FIELD).getUnclippedBoundsInRoot().height.value,
      compose.onNodeWithTag(MARKDOWN_FIELD).getUnclippedBoundsInRoot().height.value,
      HALF_A_PIXEL,
    )
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L

    /**
     * The same source in both fields. Deliberately prose-shaped: a markdown cell holds prose, and
     * the claim is that prose in an *editor* is still drawn in the editor's font.
     */
    const val THREE_LINES = "the first line\nthe second line\nthe third line"

    const val MARKDOWN_FIELD = "markdown-source-field"
    const val RAW_FIELD = "raw-source-field"

    /** Wide enough that neither field wraps, so height counts lines and not wrapping. */
    val FIELD_WIDTH = 400.dp

    const val HALF_A_PIXEL = 0.5f
  }
}
