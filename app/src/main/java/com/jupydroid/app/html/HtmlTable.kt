package com.jupydroid.app.html

import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.TableCell
import org.jsoup.nodes.Element

/**
 * A `<table>` into a [RichBlock.Table], or nothing when it holds no rows - in which case the
 * output falls back to whatever else the mime bundle offered, rather than drawing an empty frame.
 *
 * Rows are read from direct children only, so a table nested inside a cell keeps its own rows
 * instead of having them hoisted into the outer grid.
 */
internal fun Element.asTable(): List<RichBlock> {
  val rows = tableRows().map { it.cells() }.filter { it.isNotEmpty() }
  if (rows.isEmpty()) return emptyList()
  return listOf(RichBlock.Table(rows.padded()))
}

/** `<tr>` may sit directly under the table or inside a `thead`/`tbody`/`tfoot`; both are usual. */
private fun Element.tableRows(): List<Element> =
  children()
    .flatMap { if (it.tagName() in ROW_GROUP_TAGS) it.children() else listOf(it) }
    .filter { it.tagName() == "tr" }

private fun Element.cells(): List<TableCell> =
  children()
    .filter { it.tagName() in CELL_TAGS }
    .map { TableCell(it.childInlines(), isHeader = it.tagName() == "th") }

/**
 * A grid is only a grid if every row is the same length. Ragged rows come from hand-written
 * markup rather than from pandas, but a renderer that indexes by column must not meet one.
 */
private fun List<List<TableCell>>.padded(): List<List<TableCell>> {
  val columns = maxOf { it.size }
  return map { row -> row + List(columns - row.size) { EMPTY_CELL } }
}

private val EMPTY_CELL = TableCell(inlines = emptyList(), isHeader = false)

private val ROW_GROUP_TAGS = setOf("thead", "tbody", "tfoot")

private val CELL_TAGS = setOf("td", "th")
