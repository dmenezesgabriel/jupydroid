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
 * `toc:show-panel`, on both presentations [CellActionSurface]'s own rule offers.
 *
 * The presentation is passed in rather than read from the window, for [CellActionsTest]'s own
 * reason: a test states the branch it means, and a `ForcedSize` would compact the whole scene
 * where the question is only which surface draws. The rule itself is [CellActionPresentation]'s,
 * tested once on the JVM; what needs a device is that each branch renders and responds here too.
 */
@RunWith(AndroidJUnit4::class)
class TableOfContentsPanelTest {
  @get:Rule val compose = createComposeRule()

  private val headings = listOf(
    TocHeading("h1", 1, "Chapter one"),
    TocHeading("h2", 2, "Part A"),
    TocHeading("h1b", 1, "Chapter two"),
  )

  @Test
  fun theMenuOffersEveryHeadingAndReportsThePick() {
    assertOffersAndReports(CellActionPresentation.MENU)
  }

  @Test
  fun theSheetOffersEveryHeadingAndReportsThePick() {
    assertOffersAndReports(CellActionPresentation.SHEET)
  }

  /**
   * Picking a row both reports its cell and closes the surface, in the one step
   * [CellActionSurface]'s own `choose` already establishes for a picker here.
   */
  private fun assertOffersAndReports(presentation: CellActionPresentation) {
    show(headings, presentation = presentation)

    compose.onNodeWithContentDescription("Table of contents Chapter one").assertIsDisplayed()
    compose.onNodeWithContentDescription("Table of contents Chapter two").assertIsDisplayed()
    compose.onNodeWithContentDescription("Table of contents Part A").performClick()

    compose.runOnIdle {
      assertEquals(listOf("h2"), selected)
      assertEquals(1, dismissals)
    }
  }

  @Test
  fun theMenuShowsAPlaceholderWithNoHeadings() {
    assertShowsAPlaceholderWithNoHeadings(CellActionPresentation.MENU)
  }

  @Test
  fun theSheetShowsAPlaceholderWithNoHeadings() {
    assertShowsAPlaceholderWithNoHeadings(CellActionPresentation.SHEET)
  }

  private fun assertShowsAPlaceholderWithNoHeadings(presentation: CellActionPresentation) {
    show(emptyList(), presentation = presentation)

    compose.onNodeWithText("No headings in this notebook").assertIsDisplayed()
  }

  /** `expanded` is what a caller drives, and false renders nothing - [CellActionSurface]'s shape. */
  @Test
  fun collapsedRendersNothing() {
    show(headings, expanded = false)

    compose.onNodeWithContentDescription("Table of contents Chapter one").assertDoesNotExist()
  }

  private val selected = mutableListOf<String>()
  private var dismissals = 0

  private fun show(
    headings: List<TocHeading>,
    expanded: Boolean = true,
    presentation: CellActionPresentation = CellActionPresentation.SHEET,
  ) {
    compose.setContent {
      JupyterDroidTheme {
        TableOfContentsPanel(
          expanded = expanded,
          headings = headings,
          onHeadingSelected = { selected += it },
          onDismiss = { dismissals++ },
          presentation = presentation,
        )
      }
    }
  }
}
