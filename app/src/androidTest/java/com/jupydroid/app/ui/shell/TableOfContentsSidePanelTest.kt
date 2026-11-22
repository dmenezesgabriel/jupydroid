package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.DocumentOutline
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.editor.TocHeading
import com.jupydroid.app.ui.editor.TocNumbering
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reference's sidebar panel, `shell.add(toc, 'left', { rank: 400 })` - a column that draws the
 * open document's structure and stays there while the document scrolls.
 *
 * It reads a [DocumentOutline] and derives nothing, because it cannot: it is in the list pane and
 * the headings are worked out in the notebook's own `NavEntry`.
 */
@RunWith(AndroidJUnit4::class)
class TableOfContentsSidePanelTest {
  @get:Rule val compose = createComposeRule()

  private val jumped = mutableListOf<String>()

  private val headings = listOf(
    TocHeading("h1", 1, "Chapter one"),
    TocHeading("h2", 2, "Part A"),
    TocHeading("h1b", 1, "Chapter two"),
  )

  @Test
  fun everyHeadingOfTheOpenDocumentIsOnScreen() {
    show(outlineOf(headings))

    compose.onNodeWithContentDescription("Table of contents Chapter one").assertIsDisplayed()
    compose.onNodeWithContentDescription("Table of contents Part A").assertIsDisplayed()
    compose.onNodeWithContentDescription("Table of contents Chapter two").assertIsDisplayed()
  }

  /** **A pick does not close it**, which is the whole of what a panel buys over the overlay. */
  @Test
  fun aPickJumpsAndLeavesThePanelUp() {
    show(outlineOf(headings))

    compose.onNodeWithContentDescription("Table of contents Part A").performClick()

    compose.runOnIdle { assertEquals(listOf("h2"), jumped) }
    compose.onNodeWithContentDescription("Table of contents Part A").assertIsDisplayed()
  }

  /**
   * `if (!this.model || this.model.headings.length === 0)` - **one** placeholder for both
   * "nothing is open" and "nothing has headings" (`packages/toc/src/treeview.tsx:31-40`), with the
   * reference's own two strings (`packages/toc/src/panel.ts:32-36`).
   */
  @Test
  fun nothingOpenDrawsThePlaceholder() {
    show(outline = null)

    compose.onNodeWithText(NO_HEADINGS).assertIsDisplayed()
    compose.onNodeWithText(PLACEHOLDER_TEXT).assertIsDisplayed()
  }

  @Test
  fun aDocumentWithNoHeadingsDrawsTheSamePlaceholder() {
    show(outlineOf(emptyList()))

    compose.onNodeWithText(NO_HEADINGS).assertIsDisplayed()
    compose.onNodeWithText(PLACEHOLDER_TEXT).assertIsDisplayed()
  }

  /** The header takes the document's name - `this._model?.title ?? …` (`panel.ts:77-80`). */
  @Test
  fun theHeaderNamesTheOpenDocument() {
    show(outlineOf(headings))

    compose.onNodeWithText("Analysis.ipynb").assertIsDisplayed()
  }

  @Test
  fun theHeaderFallsBackToThePanelsOwnNameWithNothingOpen() {
    show(outline = null)

    compose.onNodeWithText(TABLE_OF_CONTENTS).assertIsDisplayed()
  }

  /** `toc:toggle-collapse`, which the panel keeps and the overlay does not - see [TocFolds]. */
  @Test
  fun oneControlFoldsEveryEntryAndThenUnfoldsThem() {
    show(outlineOf(headings))

    compose.onNodeWithContentDescription("Collapse all entries").performClick()

    compose.onNodeWithContentDescription("Table of contents Part A").assertDoesNotExist()
    compose.onNodeWithContentDescription("Expand all entries").performClick()
    compose.onNodeWithContentDescription("Table of contents Part A").assertIsDisplayed()
  }

  private fun outlineOf(headings: List<TocHeading>) =
    DocumentOutline("Analysis.ipynb", headings, TocNumbering()) { jumped += it }

  private fun show(outline: DocumentOutline?) {
    compose.setContent {
      JupydroidTheme { TableOfContentsSidePanel(outline = outline) }
    }
  }

  private companion object {
    const val TABLE_OF_CONTENTS = "Table of contents"
    const val NO_HEADINGS = "No headings"
    const val PLACEHOLDER_TEXT = "The table of contents shows headings in notebooks and supported files."
  }
}
