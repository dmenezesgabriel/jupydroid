package com.jupydroid.app.markdown

import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.RichInline
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * Markdown source into [RichBlock]s, using commonmark-java's AST.
 *
 * The AST is walked directly by sibling chain rather than with a `Visitor`: the model is a tree
 * of immutable lists, so building it bottom-up reads better than accumulating into a visitor's
 * mutable state. `org.commonmark.renderer` is never touched - nothing here produces HTML, which
 * is the whole point of drawing markdown in Compose instead of a WebView.
 *
 * ```
 * MarkdownDocument.parse("## Results")  // [Heading(level = 2, …)]
 * ```
 */
object MarkdownDocument {
  private val parser: Parser = Parser.builder().build()

  fun parse(source: String): List<RichBlock> = parser.parse(source).blocks()

  /**
   * The heading level this source is, or null when it holds no heading of its own.
   *
   * **The lowest number wins**, which is `MarkdownCell.headingInfo` read literally -
   * `headings.reduce((prev, curr) => prev.level <= curr.level ? prev : curr)`
   * (`packages/cells/src/widget.ts:2213-2226`) - so a cell opening on `###` and going on to `#`
   * heads an H1 section rather than an H3 one.
   *
   * **Parsed, never pattern-matched.** JupyterLab renders the source and reads back `<h1>`-`<h6>`
   * (`packages/toc/src/utils/markdown.ts:78-114`), so a `#` inside a fence is not a heading and a
   * setext underline is one. **Top level only**, because `getHeadingTokens` looks at the type of
   * each token `_marked.lexer` returns and never descends
   * (`packages/markedparser-extension/src/index.ts:268-298`): a heading inside a blockquote or a
   * list item is not the cell's heading.
   *
   * ```
   * MarkdownDocument.headingLevel("## Results")  // 2
   * ```
   */
  fun headingLevel(source: String): Int? =
    parse(source).filterIsInstance<RichBlock.Heading>().minOfOrNull { it.level }

  private fun Node.blocks(): List<RichBlock> = children().mapNotNull { it.asBlock() }

  private fun Node.asBlock(): RichBlock? = when (this) {
    is Heading -> RichBlock.Heading(level, inlines())
    is Paragraph -> RichBlock.Paragraph(inlines())
    is FencedCodeBlock -> RichBlock.CodeBlock(literal, info?.takeIf { it.isNotBlank() })
    is IndentedCodeBlock -> RichBlock.CodeBlock(literal, null)
    is BlockQuote -> RichBlock.BlockQuote(blocks())
    is BulletList -> RichBlock.ListBlock(ordered = false, startFrom = 1, items = items())
    is OrderedList -> RichBlock.ListBlock(ordered = true, startFrom = startNumber, items = items())
    is ThematicBreak -> RichBlock.ThematicBreak
    // Anything else - HTML blocks, link reference definitions - has no place in a notebook cell
    // and is dropped rather than shown as its own markup.
    else -> null
  }

  /** Each item is its own block list: an item may hold a paragraph and then a nested list. */
  private fun Node.items(): List<List<RichBlock>> =
    children().filterIsInstance<ListItem>().map { it.blocks() }

  private fun Node.inlines(): List<RichInline> = children().mapNotNull { it.asInline() }

  private fun Node.asInline(): RichInline? = when (this) {
    is Text -> RichInline.Text(literal)
    is Code -> RichInline.Code(literal)
    is Emphasis -> RichInline.Emphasis(inlines())
    is StrongEmphasis -> RichInline.StrongEmphasis(inlines())
    is Link -> RichInline.Link(destination, inlines())
    is SoftLineBreak -> RichInline.SoftLineBreak
    is HardLineBreak -> RichInline.HardLineBreak
    else -> null
  }

  /** commonmark exposes children as a sibling chain, not a collection. */
  private fun Node.children(): List<Node> = generateSequence(firstChild) { it.next }.toList()
}
