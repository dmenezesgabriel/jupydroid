package com.jupydroid.app.syntax

import org.commonmark.node.BlockQuote
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.StrongEmphasis
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/**
 * Markdown highlighting from commonmark's own source spans.
 *
 * No second lexer, which is what a language usually costs here: the parser already knows where
 * every node came from once `includeSourceSpans` is on, and `SourceSpan` carries an input index
 * and a length. So this is a walk over the tree that already renders the cell, read back as
 * offsets.
 *
 * A list marker and a quote marker have no node of their own - they are syntax the parser
 * consumes - so they are taken from the first line of the item or quote instead.
 */
class MarkdownSyntax : SyntaxHighlighter {
  override fun spansOf(source: String): List<SyntaxSpan> {
    if (source.isBlank()) return emptyList()
    val spans = mutableListOf<SyntaxSpan>()
    collect(parser.parse(source), source, spans)
    return spans.filter { it.end > it.start && it.end <= source.length && it.start >= 0 }
  }

  private fun collect(node: Node, source: String, into: MutableList<SyntaxSpan>) {
    node.kind()?.let { kind -> node.range()?.let { into += SyntaxSpan(it.first, it.second, kind) } }
    markerOf(node, source)?.let { into += it }
    var child = node.firstChild
    while (child != null) {
      collect(child, source, into)
      child = child.next
    }
  }

  private fun Node.kind(): SyntaxKind? = when (this) {
    is Heading -> SyntaxKind.HEADING
    is StrongEmphasis -> SyntaxKind.STRONG
    is Emphasis -> SyntaxKind.EMPHASIS
    is Code, is FencedCodeBlock, is IndentedCodeBlock -> SyntaxKind.CODE
    is Link -> SyntaxKind.LINK
    else -> null
  }

  /** Start and end of everything this node covers, across however many lines it spans. */
  private fun Node.range(): Pair<Int, Int>? {
    val spans = sourceSpans.ifEmpty { return null }
    val start = spans.first().inputIndex
    val last = spans.last()
    return start to (last.inputIndex + last.length)
  }

  /**
   * The `-` or `>` the parser swallowed. Only the leading run of markup is marked, so the text
   * after it keeps whatever the nodes inside it say.
   */
  private fun markerOf(node: Node, source: String): SyntaxSpan? {
    val kind = when (node) {
      is ListItem -> SyntaxKind.MARKER
      is BlockQuote -> SyntaxKind.QUOTE
      else -> null
    }
    val start = node.sourceSpans.firstOrNull()?.inputIndex
    if (kind == null || start == null) return null
    return source.indexOfFirst(start) { it.isWhitespace() }
      .takeIf { it > start }
      ?.let { SyntaxSpan(start, it, kind) }
  }

  private inline fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
    var i = from
    while (i < length && !predicate(this[i])) i++
    return i
  }

  private companion object {
    val parser: Parser = Parser.builder()
      .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
      .build()
  }
}
