package com.jupydroid.app.markdown

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
 * Markdown source into [MarkdownBlock]s, using commonmark-java's AST.
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

  fun parse(source: String): List<MarkdownBlock> = parser.parse(source).blocks()

  private fun Node.blocks(): List<MarkdownBlock> = children().mapNotNull { it.asBlock() }

  private fun Node.asBlock(): MarkdownBlock? = when (this) {
    is Heading -> MarkdownBlock.Heading(level, inlines())
    is Paragraph -> MarkdownBlock.Paragraph(inlines())
    is FencedCodeBlock -> MarkdownBlock.CodeBlock(literal, info?.takeIf { it.isNotBlank() })
    is IndentedCodeBlock -> MarkdownBlock.CodeBlock(literal, null)
    is BlockQuote -> MarkdownBlock.BlockQuote(blocks())
    is BulletList -> MarkdownBlock.ListBlock(ordered = false, startFrom = 1, items = items())
    is OrderedList -> MarkdownBlock.ListBlock(ordered = true, startFrom = startNumber, items = items())
    is ThematicBreak -> MarkdownBlock.ThematicBreak
    // Anything else - HTML blocks, link reference definitions - has no place in a notebook cell
    // and is dropped rather than shown as its own markup.
    else -> null
  }

  /** Each item is its own block list: an item may hold a paragraph and then a nested list. */
  private fun Node.items(): List<List<MarkdownBlock>> =
    children().filterIsInstance<ListItem>().map { it.blocks() }

  private fun Node.inlines(): List<MarkdownInline> = children().mapNotNull { it.asInline() }

  private fun Node.asInline(): MarkdownInline? = when (this) {
    is Text -> MarkdownInline.Text(literal)
    is Code -> MarkdownInline.Code(literal)
    is Emphasis -> MarkdownInline.Emphasis(inlines())
    is StrongEmphasis -> MarkdownInline.StrongEmphasis(inlines())
    is Link -> MarkdownInline.Link(destination, inlines())
    is SoftLineBreak -> MarkdownInline.SoftLineBreak
    is HardLineBreak -> MarkdownInline.HardLineBreak
    else -> null
  }

  /** commonmark exposes children as a sibling chain, not a collection. */
  private fun Node.children(): List<Node> = generateSequence(firstChild) { it.next }.toList()
}
