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

  // --- toc:toggle-collapse (P2h-4) ---------------------------------------------------------

  /**
   * The disclosure is drawn only where there is something under it, which is the reference's own
   * condition (`packages/toc/src/tocitem.tsx`) and this app's rule for the cell header's chevron
   * too: a control for a state the row is not in is one it already refuses to draw.
   */
  @Test
  fun onlyAnEntryThatOwnsSomethingCarriesADisclosure() {
    show(headings)

    compose.onNodeWithContentDescription("Collapse Chapter one").assertIsDisplayed()
    compose.onNodeWithContentDescription("Collapse Part A").assertDoesNotExist()
    compose.onNodeWithContentDescription("Collapse Chapter two").assertDoesNotExist()
  }

  @Test
  fun theDisclosureFoldsTheEntryWithoutJumpingToIt() {
    show(headings)

    compose.onNodeWithContentDescription("Collapse Chapter one").performClick()

    compose.runOnIdle {
      assertEquals(listOf("h1"), foldsToggled)
      // The chevron is its own control inside the row, so pressing it is not a pick.
      assertEquals(emptyList<String>(), selected)
    }
  }

  @Test
  fun aFoldedEntryHidesWhatItOwnsAndOffersToUnfold() {
    show(headings, collapsed = setOf("h1"))

    compose.onNodeWithContentDescription("Table of contents Part A").assertDoesNotExist()
    compose.onNodeWithContentDescription("Expand Chapter one").assertIsDisplayed()
    compose.onNodeWithContentDescription("Table of contents Chapter two").assertIsDisplayed()
  }

  /** One control, named for what the next tap does - `someExpanded`'s own label flip. */
  @Test
  fun theOneControlNamesWhichHalfOfTheCommandIsNext() {
    show(headings)

    compose.onNodeWithContentDescription(COLLAPSE_ALL_TOC_ENTRIES).performClick()

    compose.runOnIdle { assertEquals(1, allFoldsToggled) }
  }

  @Test
  fun withEverythingFoldedTheControlOffersToUnfoldItAll() {
    show(headings, collapsed = setOf("h1"))

    compose.onNodeWithContentDescription(EXPAND_ALL_TOC_ENTRIES).assertIsDisplayed()
  }

  /**
   * Absent rather than dead where nothing nests, which is this app's rule for a control that
   * would do nothing - the reference always draws it, and always drawing it here would put a
   * button on screen that cannot change anything.
   */
  @Test
  fun aFlatTableOfContentsCarriesNoFoldAllControl() {
    show(listOf(TocHeading("a", 1, "One"), TocHeading("b", 1, "Two")))

    compose.onNodeWithContentDescription(COLLAPSE_ALL_TOC_ENTRIES).assertDoesNotExist()
    compose.onNodeWithContentDescription(EXPAND_ALL_TOC_ENTRIES).assertDoesNotExist()
  }

  private val selected = mutableListOf<String>()
  private var closes = 0
  private val foldsToggled = mutableListOf<String>()
  private var allFoldsToggled = 0

  private fun show(headings: List<TocHeading>, collapsed: Set<String> = emptySet()) {
    compose.setContent {
      JupyterDroidTheme {
        TableOfContentsPane(
          headings = headings,
          collapsed = collapsed,
          onHeadingSelected = { selected += it },
          onToggleCollapsed = { foldsToggled += it },
          onToggleAllCollapsed = { allFoldsToggled++ },
          onClose = { closes++ },
        )
      }
    }
  }
}
