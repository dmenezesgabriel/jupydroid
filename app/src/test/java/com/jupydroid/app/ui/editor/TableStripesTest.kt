package com.jupydroid.app.ui.editor

import com.jupydroid.app.richtext.RichInline
import com.jupydroid.app.richtext.TableCell
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which rows are tinted, kept out of the composable so the off-by-one is arguable on the JVM
 * rather than only in a screenshot - which is where it was first spotted, and only because
 * someone looked at one.
 *
 * JupyterLab's rule is `tbody tr:nth-child(even)`
 * (`packages/rendermime/style/base.css:470-476`): the count starts inside the body, so the
 * *second* data row is the first tinted one, and the header takes no part in the count.
 */
class TableStripesTest {
  @Test
  fun tintsEveryOtherBodyRowStartingWithTheSecond() {
    val rows = listOf(headerRow("", "a", "b"), bodyRow("0", "1"), bodyRow("1", "2"), bodyRow("2", "3"))

    assertEquals(setOf(2), rows.stripedRows())
  }

  /** The header takes no part in the count, so a table without one starts the same way. */
  @Test
  fun countsFromTheFirstBodyRowWhenThereIsNoHeader() {
    val rows = listOf(bodyRow("a", "1"), bodyRow("b", "2"), bodyRow("c", "3"), bodyRow("d", "4"))

    assertEquals(setOf(1, 3), rows.stripedRows())
  }

  /** A pandas row leads with a `<th>` index cell, which must not make the row a header row. */
  @Test
  fun aRowWithOneHeaderCellIsStillABodyRow() {
    val indexed = listOf(TableCell(text("0"), isHeader = true), TableCell(text("1"), isHeader = false))
    val rows = listOf(headerRow("", "a"), indexed, indexed)

    assertEquals(setOf(2), rows.stripedRows())
  }

  @Test
  fun tintsNothingWhenThereIsOnlyAHeader() {
    assertEquals(emptySet<Int>(), listOf(headerRow("a", "b")).stripedRows())
  }

  private fun headerRow(vararg cells: String) = cells.map { TableCell(text(it), isHeader = true) }

  private fun bodyRow(vararg cells: String) = cells.map { TableCell(text(it), isHeader = false) }

  private fun text(value: String) = listOf(RichInline.Text(value))
}
