package com.jupydroid.app.syntax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown highlighting comes from commonmark's own source spans rather than a second lexer -
 * `includeSourceSpans(BLOCKS_AND_INLINES)` plus `SourceSpan.getInputIndex()`/`getLength()`.
 */
class MarkdownSyntaxTest {
  private val syntax: SyntaxHighlighter = MarkdownSyntax()

  @Test
  fun marksAHeadingIncludingItsHashes() {
    val source = "# Analysis"

    assertEquals(SyntaxKind.HEADING, kindAt(syntax.spansOf(source), 0))
    assertEquals(SyntaxKind.HEADING, kindAt(syntax.spansOf(source), source.indexOf("Analysis")))
  }

  @Test
  fun marksEmphasisAndStrongSeparately() {
    val source = "a *slanted* and **heavy** word"

    assertEquals(SyntaxKind.EMPHASIS, kindAt(syntax.spansOf(source), source.indexOf("slanted")))
    assertEquals(SyntaxKind.STRONG, kindAt(syntax.spansOf(source), source.indexOf("heavy")))
  }

  @Test
  fun marksInlineCodeAndFencedBlocks() {
    val inline = "run `df.head()` first"
    assertEquals(SyntaxKind.CODE, kindAt(syntax.spansOf(inline), inline.indexOf("df.head")))

    val fenced = "```python\nx = 1\n```"
    assertEquals(SyntaxKind.CODE, kindAt(syntax.spansOf(fenced), fenced.indexOf("x = 1")))
  }

  @Test
  fun marksALinkAndLeavesTheProseAroundItAlone() {
    val source = "See [the docs](https://x.dev) now"

    assertEquals(SyntaxKind.LINK, kindAt(syntax.spansOf(source), source.indexOf("the docs")))
    assertEquals(SyntaxKind.PLAIN, kindAt(syntax.spansOf(source), source.indexOf("now")))
  }

  @Test
  fun marksAQuoteAndAListMarker() {
    assertEquals(SyntaxKind.QUOTE, kindAt(syntax.spansOf("> quoted"), 0))
    assertEquals(SyntaxKind.MARKER, kindAt(syntax.spansOf("- an item"), 0))
  }

  /**
   * **A quote is quoted for its whole length**, which is what the reference's grammar says: the
   * blockquote rule is `begin`/`end` over the entire block and scopes it `markup.quote.markdown`,
   * with the `>` a separate capture (`extensions/markdown-basics/syntaxes/markdown.tmLanguage.json`,
   * `/repository/blockquote`).
   *
   * Ours once marked the `>` alone. It was invisible while [SyntaxKind.QUOTE] drew in the
   * string colour - a quote body that looked plain looked like prose, which it is - and the palette re-base
   * gave it a colour of its own, at which point one green chevron above plain text was obviously
   * not what VS Code draws.
   */
  @Test
  fun marksTheWholeOfAQuoteRatherThanItsChevron() {
    val source = "> quoted words"

    assertEquals(SyntaxKind.QUOTE, kindAt(syntax.spansOf(source), source.indexOf("words")))
  }

  /** The rule is `begin`/`end` over the block, so a quote's later lines are inside it too. */
  @Test
  fun marksEveryLineOfAMultiLineQuote() {
    val source = "> first line\n> second line\n"

    val spans = syntax.spansOf(source)

    assertEquals(SyntaxKind.QUOTE, kindAt(spans, source.indexOf("first")))
    assertEquals(SyntaxKind.QUOTE, kindAt(spans, source.indexOf("second")))
  }

  /**
   * **A list marker is the bullet and nothing else**, and that half must not move with the quote.
   * The reference scopes only the bullet - `punctuation.definition.list.begin.markdown` - and
   * leaves the item's text to whatever is inside it, which is what this app already did.
   */
  @Test
  fun leavesAListItemsTextOutsideItsMarker() {
    val source = "- an item"

    assertEquals(SyntaxKind.PLAIN, kindAt(syntax.spansOf(source), source.indexOf("an item")))
  }

  @Test
  fun leavesPlainProseUnmarked() {
    assertTrue("prose needs no spans", syntax.spansOf("just some words").isEmpty())
  }

  /** The renderer crashes on a backwards span, exactly as it would for Python. */
  @Test
  fun neverProducesAnEmptyOrBackwardsSpan() {
    val source = "# H\n\n- a\n\n> q\n\n`c`\n\n**b**\n\n```\nx\n```\n"

    val spans = syntax.spansOf(source)

    assertTrue("every span must advance, got $spans", spans.all { it.end > it.start })
    assertTrue("spans must not overrun, got $spans", spans.all { it.end <= source.length })
  }

  /** A cell is edited constantly, so half-written markdown is the normal case. */
  @Test
  fun toleratesHalfWrittenMarkdown() {
    listOf("", "#", "**unclosed", "- ", "```", "[link](", ">", "   ").forEach { syntax.spansOf(it) }
  }

  /** The fixture the device flow uses, so what the screenshot shows can be checked here. */
  @Test
  fun marksEveryKindInsideAWholeDocument() {
    val source = buildString {
      appendLine("# Analysis").appendLine()
      appendLine("Some **bold** and *italic* prose with `df.head()` inline.").appendLine()
      appendLine("- first point").appendLine()
      appendLine("> A quoted remark.").appendLine()
      appendLine("```python").appendLine("x = 1").appendLine("```").appendLine()
      append("See [the docs](https://x.dev).")
    }
    val spans = syntax.spansOf(source)

    assertEquals(SyntaxKind.HEADING, kindAt(spans, source.indexOf("Analysis")))
    assertEquals(SyntaxKind.STRONG, kindAt(spans, source.indexOf("bold")))
    assertEquals(SyntaxKind.EMPHASIS, kindAt(spans, source.indexOf("italic")))
    assertEquals(SyntaxKind.CODE, kindAt(spans, source.indexOf("df.head")))
    assertEquals(SyntaxKind.MARKER, kindAt(spans, source.indexOf("- first")))
    assertEquals(SyntaxKind.QUOTE, kindAt(spans, source.indexOf("> A quoted")))
    assertEquals(SyntaxKind.CODE, kindAt(spans, source.indexOf("x = 1")))
    assertEquals(SyntaxKind.LINK, kindAt(spans, source.indexOf("the docs")))
  }

  private fun kindAt(spans: List<SyntaxSpan>, offset: Int): SyntaxKind =
    spans.firstOrNull { offset >= it.start && offset < it.end }?.kind ?: SyntaxKind.PLAIN
}
