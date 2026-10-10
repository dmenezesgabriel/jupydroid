package com.jupydroid.app.markdown

import com.jupydroid.app.richtext.CellAlignment
import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.RichInline
import com.jupydroid.app.richtext.plainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A GitHub-flavoured pipe table, which marked parses under `gfm: true`
 * (jupyterlab@7f0e690 packages/markedparser-extension/src/index.ts), into the same
 * [RichBlock.Table] an HTML output fills.
 */
class MarkdownTableTest {
  @Test
  fun readsAPipeTableAsOneTableOfRows() {
    val table = tableOf("| a | b |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |")

    assertEquals(
      listOf(listOf("a", "b"), listOf("1", "2"), listOf("3", "4")),
      table.rows.map { row -> row.map { it.inlines.plainText() } },
    )
  }

  @Test
  fun marksTheHeaderRowAndOnlyTheHeaderRow() {
    val table = tableOf("| a | b |\n|---|---|\n| 1 | 2 |")

    assertEquals(listOf(true, true), table.rows[0].map { it.isHeader })
    assertEquals(listOf(false, false), table.rows[1].map { it.isHeader })
  }

  /** `:--`, `:-:` and `--:` are the column's, so every cell under the delimiter carries them. */
  @Test
  fun eachCellTakesItsColumnsAlignment() {
    val table = tableOf("| l | c | r |\n|:--|:-:|--:|\n| 1 | 2 | 3 |")

    val aligned = listOf(CellAlignment.Start, CellAlignment.Center, CellAlignment.End)
    assertEquals(aligned, table.rows[0].map { it.alignment })
    assertEquals(aligned, table.rows[1].map { it.alignment })
  }

  /** With no colon, a header cell is centred and a body cell starts at the left, as `<th>` and `<td>` do. */
  @Test
  fun anUnalignedColumnCentresItsHeaderAndStartsItsBody() {
    val table = tableOf("| h |\n|---|\n| v |")

    assertEquals(CellAlignment.Center, table.rows[0].single().alignment)
    assertEquals(CellAlignment.Start, table.rows[1].single().alignment)
  }

  @Test
  fun keepsTheInlineMarksInsideACell() {
    val cell = tableOf("| h |\n|---|\n| **b** and `c` |").rows[1].single()

    assertTrue("got $cell", cell.inlines.any { it is RichInline.StrongEmphasis })
    assertEquals("c", cell.inlines.filterIsInstance<RichInline.Code>().single().code)
  }

  /** A grid is only a grid if the rows are the same length, so a short row is padded. */
  @Test
  fun padsAShortBodyRowToTheHeadersWidth() {
    val table = tableOf("| a | b |\n|---|---|\n| 1 |")

    assertEquals(listOf(2, 2), table.rows.map { it.size })
    assertEquals("", table.rows[1][1].inlines.plainText())
  }

  @Test
  fun readsAHeaderWithNoBodyAsATableOfOneRow() {
    assertEquals(1, tableOf("| a | b |\n|---|---|").rows.size)
  }

  /** Without a delimiter row there is no table, only a line with pipes in it. */
  @Test
  fun pipesWithoutADelimiterRowStayAParagraph() {
    val block = MarkdownDocument.parse("| a | b |\n| 1 | 2 |").single()

    assertTrue("got $block", block is RichBlock.Paragraph)
  }

  @Test
  fun aTableSitsBetweenTheBlocksAroundIt() {
    val blocks = MarkdownDocument.parse("# Title\n\n| a |\n|---|\n| 1 |\n\nAfter.")

    assertEquals(listOf("Heading", "Table", "Paragraph"), blocks.map { it::class.simpleName })
  }

  private fun tableOf(source: String): RichBlock.Table = MarkdownDocument.parse(source).single() as RichBlock.Table
}
