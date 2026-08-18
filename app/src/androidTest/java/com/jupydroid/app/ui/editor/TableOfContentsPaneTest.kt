package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The persistent half of `toc:show-panel` - the column an editor wide enough for one draws beside
 * the notebook, where [TableOfContentsPanelTest] covers the overlay a narrower one gets.
 *
 * **A pick does not close it**, which is the difference the whole sub-step is about: the reference's
 * ToC is a sidebar that stays (`packages/toc-extension/src/index.ts:309`), so jumping twice costs
 * two taps rather than four.
 */
@RunWith(AndroidJUnit4::class)
class TableOfContentsPaneTest {
  @get:Rule val compose = createComposeRule()

  private val headings = listOf(
    TocHeading("h1", 1, "Chapter one"),
    TocHeading("h2", 2, "Part A"),
    TocHeading("h1b", 1, "Chapter two"),
  )

  @Test
  fun everyHeadingIsOnScreen() {
    show(headings)

    compose.onNodeWithContentDescription("Table of contents Chapter one").assertIsDisplayed()
    compose.onNodeWithContentDescription("Table of contents Part A").assertIsDisplayed()
    compose.onNodeWithContentDescription("Table of contents Chapter two").assertIsDisplayed()
  }

  @Test
  fun aPickReportsItsCellAndLeavesThePaneOpen() {
    show(headings)

    compose.onNodeWithContentDescription("Table of contents Part A").performClick()

    compose.runOnIdle { assertEquals(listOf("h2"), selected) }
    compose.onNodeWithContentDescription("Table of contents Part A").assertIsDisplayed()
  }

  /** The way out is on the pane itself, not only in the menu that opened it. */
  @Test
  fun theCloseButtonAsksToHideIt() {
    show(headings)

    compose.onNodeWithContentDescription(HIDE_TABLE_OF_CONTENTS).performClick()

    compose.runOnIdle { assertEquals(1, closes) }
  }

  @Test
  fun aNotebookWithNoHeadingsShowsAPlaceholder() {
    show(emptyList())

    compose.onNodeWithText("No headings in this notebook").assertIsDisplayed()
  }

  private val selected = mutableListOf<String>()
  private var closes = 0

  private fun show(headings: List<TocHeading>) {
    compose.setContent {
      JupyterDroidTheme {
        TableOfContentsPane(
          headings = headings,
          onHeadingSelected = { selected += it },
          onClose = { closes++ },
        )
      }
    }
  }
}
