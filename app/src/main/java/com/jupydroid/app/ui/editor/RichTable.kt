package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.TableCell
import com.jupydroid.app.richtext.toAnnotatedString
import com.jupydroid.app.theme.Spacing

/**
 * Draws a table the way Jupyter draws one, read off its stylesheet rather than invented:
 * no gridlines at all (`.jp-RenderedHTMLCommon table { border: none }`), a rule under the
 * header cells (`thead { border-bottom }`), striped body rows (`tbody tr:nth-child(odd|even)`),
 * bold headers, `0.5em` of padding, and cells aligned right - which is what JupyterLab does
 * for an output table, since its columns are usually numbers
 * (`packages/rendermime/style/base.css:426-476`).
 *
 * It scrolls sideways rather than shrinking to fit, matching `.jp-OutputArea-output`'s
 * `overflow-x: auto` and the same choice already made for a code block: a squeezed column of
 * numbers is harder to read than one you drag.
 */
@Composable
fun RichTable(table: RichBlock.Table, modifier: Modifier = Modifier) {
  val columnCount = table.rows.firstOrNull()?.size ?: return
  val stripe = MaterialTheme.colorScheme.surfaceVariant
  val rule = MaterialTheme.colorScheme.outlineVariant
  val striped = remember(table) { table.rows.stripedRows() }
  Layout(
    modifier = modifier.horizontalScroll(rememberScrollState()),
    // Backgrounds and the header rule ride on the cells rather than being drawn behind the
    // table, because every cell is measured to fill its slot exactly - so they tile into
    // full-width stripes with no second pass over the geometry.
    content = {
      table.rows.forEachIndexed { row, cells ->
        // Under `thead`, not under every `th`: a pandas index cell is a header cell in a body
        // row, and ruling each one draws a line across the middle of the table.
        val underlined = cells.isHeaderRow()
        cells.forEach {
          CellText(it, if (row in striped) stripe else Color.Transparent, rule, underlined)
        }
      }
    },
    measurePolicy = { measurables, _ -> measureGrid(measurables, columnCount) },
  )
}

/**
 * The rows JupyterLab tints, by its own rule: `tbody tr:nth-child(even)`
 * (`packages/rendermime/style/base.css:470-476`). The count starts inside the body, so the
 * *second* data row is the first tinted one and the header takes no part in it.
 */
internal fun List<List<TableCell>>.stripedRows(): Set<Int> {
  var body = 0
  return indices.filterTo(mutableSetOf()) { row ->
    !this[row].isHeaderRow() && body++ % 2 == 1
  }
}

/** All-header, so `thead`. One header cell among data cells is a pandas row index. */
private fun List<TableCell>.isHeaderRow(): Boolean = isNotEmpty() && all { it.isHeader }

/**
 * Columns as wide as their widest cell, rows as tall as their tallest. Incoming constraints
 * are ignored on purpose: the caller wraps this in a horizontal scroll, so the width to fit
 * is the content's own.
 */
private fun MeasureScope.measureGrid(measurables: List<Measurable>, columnCount: Int): MeasureResult {
  val grid = measurables.chunked(columnCount)
  val columnWidths = grid.columnWidths(columnCount)
  val rowHeights = grid.rowHeights(columnWidths)
  val placeables = grid.mapIndexed { row, cells ->
    cells.mapIndexed { column, cell ->
      cell.measure(Constraints.fixed(columnWidths[column], rowHeights[row]))
    }
  }
  return layout(columnWidths.sum(), rowHeights.sum()) { placeGrid(placeables, rowHeights) }
}

private fun List<List<Measurable>>.columnWidths(columnCount: Int): IntArray =
  IntArray(columnCount) { column -> maxOf { it[column].maxIntrinsicWidth(Int.MAX_VALUE) } }

private fun List<List<Measurable>>.rowHeights(columnWidths: IntArray): List<Int> =
  map { row -> row.mapIndexed { column, cell -> cell.maxIntrinsicHeight(columnWidths[column]) }.max() }

private fun Placeable.PlacementScope.placeGrid(grid: List<List<Placeable>>, rowHeights: List<Int>) {
  var y = 0
  grid.forEachIndexed { row, cells ->
    var x = 0
    cells.forEach { cell ->
      cell.placeRelative(x, y)
      x += cell.width
    }
    y += rowHeights[row]
  }
}

/**
 * The rule under a header cell is drawn rather than composed, for the reason a block quote's
 * is: a divider would need a row of its own and a width nobody knows until the cell is laid out.
 */
@Composable
private fun CellText(cell: TableCell, background: Color, rule: Color, underlined: Boolean) {
  val underline = if (underlined) Modifier.drawBehind { drawHeaderRule(rule) } else Modifier
  Text(
    text = cell.inlines.toAnnotatedString(richInlineStyles()),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurface,
    fontWeight = if (cell.isHeader) FontWeight.Bold else null,
    textAlign = TextAlign.End,
    modifier = Modifier.background(background).then(underline).padding(CELL_PADDING),
  )
}

private fun DrawScope.drawHeaderRule(color: Color) {
  val width = HEADER_RULE_WIDTH.toPx()
  val y = size.height - width / 2
  drawLine(color, Offset(0f, y), Offset(size.width, y), width)
}

private val CELL_PADDING = Spacing.sm
/** A hairline under a table's header row - `--jp-border-width`, which the reference sets to 1px. */
private val HEADER_RULE_WIDTH = 1.dp
