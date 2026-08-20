package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.html.HtmlDocument
import com.jupydroid.app.richtext.RichBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 7.2 `text/html`: whether a table is laid out as a grid is not a question any JVM test
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

  private fun tableOf(html: String): RichBlock.Table = HtmlDocument.parse(html).single() as RichBlock.Table
}
