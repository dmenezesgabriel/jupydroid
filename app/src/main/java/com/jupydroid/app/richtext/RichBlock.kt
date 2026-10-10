package com.jupydroid.app.richtext

/**
 * What a rendered document is made of, as far as drawing it is concerned.
 *
 * Shared rather than markdown's own, because HTML output arrives with the same vocabulary -
 * `MarkdownDocument` and `HtmlDocument` are two front ends onto one model and one renderer.
 *
 * Blocks hold blocks - a quote contains paragraphs, a list item contains anything - so the
 * renderer is recursive rather than a loop over styled runs.
 *
 * Deliberately free of Compose: leaf blocks carry an inline *model* and the `AnnotatedString` is
 * built when it is drawn. That is what keeps this whole layer testable on the JVM, and it is the
 * shape JetBrains Jewel arrived at for the same problem
 * (`platform/jewel/markdown/core/.../MarkdownBlock.kt`).
 */
sealed interface RichBlock {
  data class Heading(val level: Int, val inlines: List<RichInline>) : RichBlock

  data class Paragraph(val inlines: List<RichInline>) : RichBlock

  /** [language] is null for an indented block, which has no way to say one. */
  data class CodeBlock(val code: String, val language: String?) : RichBlock

  data class BlockQuote(val children: List<RichBlock>) : RichBlock

  /** Each item is its own list of blocks, because an item may hold a paragraph and a nested list. */
  data class ListBlock(val ordered: Boolean, val startFrom: Int, val items: List<List<RichBlock>>) : RichBlock

  data object ThematicBreak : RichBlock

  /**
   * Rows of cells, every row the same length so a renderer can treat it as a grid.
   *
   * Header-ness is a property of the cell rather than of the first row, because a pandas
   * DataFrame puts its row index in a `<th>` inside `<tbody>` - so "header" and "first row"
   * are two different things and the model has to hold both.
   */
  data class Table(val rows: List<List<TableCell>>) : RichBlock
}

/**
 * A cell holds inlines and never blocks, which is Jewel's rule for the same model.
 *
 * [alignment] is the cell's own and never left to the renderer, because the right answer depends on
 * where the table came from, which a renderer cannot see: `base.css` right-aligns every cell of an
 * HTML output and leaves a markdown table to its delimiter row
 * (jupyterlab@7f0e690 packages/rendermime/style/base.css, `:not(.jp-RenderedMarkdown)`).
 */
data class TableCell(val inlines: List<RichInline>, val isHeader: Boolean, val alignment: CellAlignment)

/** Where a cell's text sits across its column. */
enum class CellAlignment { Start, Center, End }

/** The styled runs inside a block. Nested, so `**bold with *italic* inside**` survives. */
sealed interface RichInline {
  data class Text(val text: String) : RichInline

  data class Emphasis(val children: List<RichInline>) : RichInline

  data class StrongEmphasis(val children: List<RichInline>) : RichInline

  /** GitHub-flavoured `~~text~~`, which JupyterLab parses under marked's `gfm: true`. */
  data class Strikethrough(val children: List<RichInline>) : RichInline

  data class Code(val code: String) : RichInline

  data class Link(val destination: String, val children: List<RichInline>) : RichInline

  /** A wrapped line inside one paragraph; renders as a space. */
  data object SoftLineBreak : RichInline

  /** Two trailing spaces or a backslash; renders as a newline. */
  data object HardLineBreak : RichInline
}

/** The text with every mark dropped - what a heading would read as, and what tests assert on. */
fun List<RichInline>.plainText(): String = joinToString("") { inline ->
  when (inline) {
    is RichInline.Text -> inline.text
    is RichInline.Code -> inline.code
    is RichInline.Emphasis -> inline.children.plainText()
    is RichInline.StrongEmphasis -> inline.children.plainText()
    is RichInline.Strikethrough -> inline.children.plainText()
    is RichInline.Link -> inline.children.plainText()
    RichInline.SoftLineBreak -> " "
    RichInline.HardLineBreak -> "\n"
  }
}
