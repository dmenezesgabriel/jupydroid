package com.jupydroid.app.markdown

import com.jupydroid.app.richtext.CellAlignment
import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.RichInline
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.Node
import com.jupydroid.app.richtext.TableCell as RichCell
import org.commonmark.ext.gfm.tables.TableCell as PipeCell

/**
 * A GitHub-flavoured pipe table into the same [RichBlock.Table] an HTML output fills, so one
 * renderer draws both.
 *
 * [inlinesOf] is [MarkdownDocument]'s own inline walk, passed in so a cell reads its marks exactly
 * as a paragraph does.
 *
 * ```
 * MarkdownDocument.parse("| a |\n|---|\n| 1 |")  // [Table(rows = [[a], [1]])]
 * ```
 */
internal fun TableBlock.asTable(inlinesOf: (Node) -> List<RichInline>): RichBlock.Table =
  RichBlock.Table(rows().map { row -> row.children().filterIsInstance<PipeCell>().map { it.asCell(inlinesOf) } })

/** The rows sit one level down, under a `TableHead` and a `TableBody`. */
private fun TableBlock.rows(): List<TableRow> = children().flatMap { it.children() }.filterIsInstance<TableRow>()

private fun PipeCell.asCell(inlinesOf: (Node) -> List<RichInline>): RichCell =
  RichCell(inlinesOf(this), isHeader, alignment?.asCellAlignment() ?: defaultAlignment())

private fun PipeCell.Alignment.asCellAlignment(): CellAlignment = when (this) {
  PipeCell.Alignment.LEFT -> CellAlignment.Start
  PipeCell.Alignment.CENTER -> CellAlignment.Center
  PipeCell.Alignment.RIGHT -> CellAlignment.End
}

/**
 * A column with no colon in its delimiter gets no `align` from marked, and `base.css` sets no
 * `text-align` on a markdown table (jupyterlab@7f0e690 packages/rendermime/style/base.css), so what
 * draws is the HTML rendering default: a `<th>` centred, a `<td>` at the start
 * (https://html.spec.whatwg.org/multipage/rendering.html#tables-2).
 */
private fun PipeCell.defaultAlignment(): CellAlignment = if (isHeader) CellAlignment.Center else CellAlignment.Start
