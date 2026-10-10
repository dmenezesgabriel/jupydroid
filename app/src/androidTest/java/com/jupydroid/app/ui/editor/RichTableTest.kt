package com.jupydroid.app.ui.editor

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.html.HtmlDocument
import com.jupydroid.app.markdown.MarkdownDocument
import com.jupydroid.app.richtext.RichBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `text/html` and markdown: whether a table is laid out as a grid is not a question any JVM test
 * can answer - `HtmlDocumentTest` proves the model, and only a measured layout proves the
 * columns. Unlike a popup's, this geometry *is* observable: the table shares the test's window.
 */
@RunWith(AndroidJUnit4::class)
class RichTableTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun drawsEveryCellOfTheTable() {
    compose.setContent {
      RichTable(tableOf("<table><tr><th>a</th><th>b</th></tr><tr><td>1</td><td>2</td></tr></table>"))
    }

    listOf("a", "b", "1", "2").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
  }

  /** Same row, so the same top; different columns, so a different left. That is a grid. */
  @Test
  fun laysCellsOutInRowsAndColumns() {
    compose.setContent { RichTable(tableOf("<table><tr><td>left</td><td>right</td></tr></table>")) }

    val left = compose.onNodeWithText("left").getUnclippedBoundsInRoot()
    val right = compose.onNodeWithText("right").getUnclippedBoundsInRoot()

    assertEquals(left.top, right.top)
    assertTrue("expected 'right' beside 'left', got $left and $right", right.left > left.left)
  }

  /** A second row sits below the first rather than beside it. */
  @Test
  fun stacksRowsOnTopOfEachOther() {
    compose.setContent { RichTable(tableOf("<table><tr><td>first</td></tr><tr><td>second</td></tr></table>")) }

    val first = compose.onNodeWithText("first").getUnclippedBoundsInRoot()
    val second = compose.onNodeWithText("second").getUnclippedBoundsInRoot()

    assertEquals(first.left, second.left)
    assertTrue("expected 'second' below 'first', got $first and $second", second.top >= first.bottom)
  }

  /**
   * A column is as wide as its widest cell, which is the whole reason for a measured layout
   * rather than a Row of equal weights: a narrow column must not steal room from a wide one.
   */
  @Test
  fun sizesAColumnToItsWidestCell() {
    compose.setContent {
      RichTable(tableOf("<table><tr><td>x</td><td>y</td></tr><tr><td>a much longer value</td><td>z</td></tr></table>"))
    }

    val short = compose.onNodeWithText("x").getUnclippedBoundsInRoot()
    val long = compose.onNodeWithText("a much longer value").getUnclippedBoundsInRoot()

    assertEquals(
      "both are column 0, so both are as wide as the wider",
      short.right - short.left,
      long.right - long.left,
    )
  }

  /**
   * A cell's text fills its slot, so its node's bounds are the column's either way; where the line
   * sits *inside* that box is the alignment, and only the text layout knows it. The column is
   * widened by a longer cell below, so each short header has room to be placed in.
   */
  @Test
  fun placesEachMarkdownCellWhereItsColumnSays() {
    compose.setContent {
      RichTable(markdownTableOf("| l | c | r |\n|:--|:-:|--:|\n| wide value | wide value | wide value |"))
    }

    val (left, centre, right) = listOf("l", "c", "r").map { lineOf(it) }
    assertEquals("start: no gap before the line", 0f, left.before, 0.5f)
    assertEquals("centre: as much gap before as after", centre.before, centre.after, 1f)
    assertEquals("end: no gap after the line", 0f, right.after, 0.5f)
  }

  /** `base.css` right-aligns an HTML table's cells, which the pandas path depends on. */
  @Test
  fun placesAnHtmlCellAtTheEnd() {
    compose.setContent {
      RichTable(tableOf("<table><tr><td>x</td></tr><tr><td>a much longer value</td></tr></table>"))
    }

    val line = lineOf("x")
    assertEquals(0f, line.after, 0.5f)
    assertTrue("expected room before 'x', got $line", line.before > 0f)
  }

  /** The gap between the text box's edges and its one line, in px. */
  private data class LineGaps(val before: Float, val after: Float)

  private fun lineOf(text: String): LineGaps {
    val layouts = mutableListOf<TextLayoutResult>()
    compose.onNodeWithText(text).fetchSemanticsNode()
      .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    val layout = layouts.single()
    return LineGaps(layout.getLineLeft(0), layout.size.width - layout.getLineRight(0))
  }

  private fun tableOf(html: String): RichBlock.Table = HtmlDocument.parse(html).single() as RichBlock.Table

  private fun markdownTableOf(source: String): RichBlock.Table =
    MarkdownDocument.parse(source).single() as RichBlock.Table
}
