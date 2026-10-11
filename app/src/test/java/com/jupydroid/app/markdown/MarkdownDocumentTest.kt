package com.jupydroid.app.markdown

import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.RichInline
import com.jupydroid.app.richtext.images
import com.jupydroid.app.richtext.plainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parse half of rendering markdown: source in, a model out, with no Compose anywhere near
 * it. Drawing that model is [com.jupydroid.app.ui.editor.RichText]'s job and A1c's.
 */
class MarkdownDocumentTest {
  @Test
  fun readsAHeadingWithItsLevel() {
    val blocks = MarkdownDocument.parse("## Results")

    val heading = blocks.single() as RichBlock.Heading
    assertEquals(2, heading.level)
    assertEquals("Results", heading.inlines.plainText())
  }

  @Test
  fun readsAParagraph() {
    val blocks = MarkdownDocument.parse("Just some prose.")

    assertEquals("Just some prose.", (blocks.single() as RichBlock.Paragraph).inlines.plainText())
  }

  @Test
  fun separatesBlocksInTheOrderTheyAppear() {
    val blocks = MarkdownDocument.parse("# Title\n\nA paragraph.\n\n---")

    assertTrue("got $blocks", blocks[0] is RichBlock.Heading)
    assertTrue("got $blocks", blocks[1] is RichBlock.Paragraph)
    assertEquals(RichBlock.ThematicBreak, blocks[2])
  }

  @Test
  fun readsEmphasisAndStrongAsNestedInlines() {
    val inlines = (MarkdownDocument.parse("a *b* and **c**").single() as RichBlock.Paragraph).inlines

    assertTrue("got $inlines", inlines.any { it is RichInline.Emphasis })
    assertTrue("got $inlines", inlines.any { it is RichInline.StrongEmphasis })
    assertEquals("a b and c", inlines.plainText())
  }

  @Test
  fun readsInlineCode() {
    val inlines = (MarkdownDocument.parse("run `df.head()` first").single() as RichBlock.Paragraph).inlines

    assertEquals("df.head()", inlines.filterIsInstance<RichInline.Code>().single().code)
  }

  @Test
  fun readsStrikethroughAsANestedInline() {
    val inlines = (MarkdownDocument.parse("a ~~b *c*~~ d").single() as RichBlock.Paragraph).inlines

    val struck = inlines.filterIsInstance<RichInline.Strikethrough>().single()
    assertEquals("b c", struck.children.plainText())
    assertTrue("got $struck", struck.children.any { it is RichInline.Emphasis })
    assertEquals("a b c d", inlines.plainText())
  }

  /** marked@17.0.6's `gfm.del` is `^(~~?)(?=[^\s~])…\1`: one tilde strikes as two do. */
  @Test
  fun aSingleTildeStrikesAsMarkedDoes() {
    val inlines = (MarkdownDocument.parse("~b~").single() as RichBlock.Paragraph).inlines

    assertEquals("b", inlines.filterIsInstance<RichInline.Strikethrough>().single().children.plainText())
  }

  @Test
  fun readsAFencedCodeBlockWithItsLanguage() {
    val block = MarkdownDocument.parse("```python\nx = 1\n```").single() as RichBlock.CodeBlock

    assertEquals("x = 1\n", block.code)
    assertEquals("python", block.language)
  }

  @Test
  fun readsAnIndentedCodeBlockWithNoLanguage() {
    val block = MarkdownDocument.parse("    x = 1").single() as RichBlock.CodeBlock

    assertEquals("x = 1\n", block.code)
    assertEquals(null, block.language)
  }

  @Test
  fun readsABulletListAsItemsOfBlocks() {
    val list = MarkdownDocument.parse("- one\n- two").single() as RichBlock.ListBlock

    assertEquals(false, list.ordered)
    assertEquals(2, list.items.size)
    assertEquals("one", (list.items[0].single() as RichBlock.Paragraph).inlines.plainText())
  }

  @Test
  fun readsAnOrderedListAndWhereItStarts() {
    val list = MarkdownDocument.parse("3. three\n4. four").single() as RichBlock.ListBlock

    assertEquals(true, list.ordered)
    assertEquals(3, list.startFrom)
    assertEquals(2, list.items.size)
  }

  /** Nesting is why blocks hold blocks rather than a flat list of styled runs. */
  @Test
  fun readsABlockQuoteAsBlocksInsideABlock() {
    val quote = MarkdownDocument.parse("> quoted **text**").single() as RichBlock.BlockQuote

    val inner = quote.children.single() as RichBlock.Paragraph
    assertEquals("quoted text", inner.inlines.plainText())
  }

  @Test
  fun readsALinkWithItsDestination() {
    val inlines = (MarkdownDocument.parse("see [docs](https://x.dev)").single() as RichBlock.Paragraph).inlines

    val link = inlines.filterIsInstance<RichInline.Link>().single()
    assertEquals("https://x.dev", link.destination)
    assertEquals("docs", link.children.plainText())
  }

  /** The source is kept as written: what it names is resolved where it draws, as a link's is. */
  @Test
  fun readsAnImageWithItsSourceAndAlt() {
    val inlines = (MarkdownDocument.parse("![a *chart*](attachment:x.png)").single() as RichBlock.Paragraph).inlines

    val image = inlines.single() as RichInline.Image
    assertEquals("attachment:x.png", image.source)
    assertEquals("a chart", image.alt.plainText())
  }

  /** What the reader is told instead of the picture is what search and the outline read. */
  @Test
  fun anImageReadsAsItsAltText() {
    val inlines = (MarkdownDocument.parse("see ![the chart](c.png) here").single() as RichBlock.Paragraph).inlines

    assertEquals("see the chart here", inlines.plainText())
  }

  /**
   * An HTML block reads as an HTML output does, as JupyterLab's marked and VS Code's
   * `MarkdownIt({ html: true })` both draw it (ADR-0088): an `<img>` on its own line is an image.
   */
  @Test
  fun anHtmlBlockReadsAsAnHtmlOutputDoes() {
    val blocks = MarkdownDocument.parse("<img src=\"img/plot.png\" alt=\"plot\" width=\"120\">")

    val image = (blocks.single() as RichBlock.Paragraph).inlines.single() as RichInline.Image
    assertEquals("img/plot.png", image.source)
    assertEquals(120, image.width)
  }

  @Test
  fun aCentredImageBlockKeepsItsImage() {
    val blocks = MarkdownDocument.parse("<p align=\"center\">\n  <img src=\"logo.png\" alt=\"logo\">\n</p>")

    assertEquals("logo.png", (blocks.single() as RichBlock.Paragraph).inlines.images().single().source)
  }

  /** An `<img>` written inside a line of prose is an image in that line, Jewel's void-tag branch. */
  @Test
  fun anInlineImgIsAnImageInItsLine() {
    val inlines = (MarkdownDocument.parse("see <img src=\"a.png\" alt=\"a\"> here").single() as RichBlock.Paragraph)
      .inlines

    assertEquals("see a here", inlines.plainText())
    assertEquals("a.png", inlines.images().single().source)
  }

  /** A paired inline tag is two `HtmlInline` nodes around markdown text: the tags drop, the text stays. */
  @Test
  fun aPairedInlineTagDropsAndKeepsItsText() {
    val inlines = (MarkdownDocument.parse("a <b>bold</b> word").single() as RichBlock.Paragraph).inlines

    assertEquals("a bold word", inlines.plainText())
  }

  @Test
  fun readsABareUrlAsALinkToItself() {
    val inlines = (MarkdownDocument.parse("see https://jupyter.org").single() as RichBlock.Paragraph).inlines

    val link = inlines.filterIsInstance<RichInline.Link>().single()
    assertEquals("https://jupyter.org", link.destination)
    assertEquals("https://jupyter.org", link.children.plainText())
    assertEquals("see https://jupyter.org", inlines.plainText())
  }

  /** marked@17.0.6's gfm `url` rule links a bare `www.` host over `http://`, as the GFM spec says. */
  @Test
  fun aWwwHostLinksOverHttpAsMarkedDoes() {
    val inlines = (MarkdownDocument.parse("www.jupyter.org").single() as RichBlock.Paragraph).inlines

    assertEquals("http://www.jupyter.org", inlines.filterIsInstance<RichInline.Link>().single().destination)
  }

  @Test
  fun anEmailLinksAsMailto() {
    val inlines = (MarkdownDocument.parse("a@b.org").single() as RichBlock.Paragraph).inlines

    assertEquals("mailto:a@b.org", inlines.filterIsInstance<RichInline.Link>().single().destination)
  }

  /** marked@17.0.6's gfm `_backpedal` trims trailing punctuation off a bare URL. */
  @Test
  fun aSentencesFullStopIsNotPartOfTheUrl() {
    val inlines = (MarkdownDocument.parse("go to https://jupyter.org.").single() as RichBlock.Paragraph).inlines

    assertEquals("https://jupyter.org", inlines.filterIsInstance<RichInline.Link>().single().destination)
  }

  @Test
  fun aUrlInCodeStaysCode() {
    val inlines = (MarkdownDocument.parse("`https://x.dev`").single() as RichBlock.Paragraph).inlines

    assertEquals(listOf(RichInline.Code("https://x.dev")), inlines)
  }

  /** A cell is edited constantly, so half-written markdown is the normal case. */
  @Test
  fun toleratesEmptyAndHalfWrittenSource() {
    assertEquals(emptyList<RichBlock>(), MarkdownDocument.parse(""))
    listOf("#", "**unclosed", "- ", "```", "[link](", ">").forEach { MarkdownDocument.parse(it) }
  }

  /** Soft breaks are what a wrapped paragraph is made of; they must not be lost. */
  @Test
  fun keepsLineBreaksInsideAParagraph() {
    val inlines = (MarkdownDocument.parse("one\ntwo").single() as RichBlock.Paragraph).inlines

    assertTrue("got $inlines", inlines.any { it is RichInline.SoftLineBreak })
  }

  // --- heading level, which is what a collapsible section is keyed on -------------

  /**
   * `MarkdownCell.headingInfo` returns the **lowest-numbered** heading found in the cell -
   * `headings.reduce((prev, curr) => prev.level <= curr.level ? prev : curr)`
   * (`packages/cells/src/widget.ts:2213-2226`) - so a cell opening on `###` and continuing with
   * `#` is an H1 section, not an H3 one.
   */
  @Test
  fun headingLevelIsTheLowestNumberedHeadingInTheCell() {
    assertEquals(2, MarkdownDocument.headingLevel("## Results"))
    assertEquals(1, MarkdownDocument.headingLevel("### Detail\n\n# Chapter\n\n## Part"))
    assertEquals(6, MarkdownDocument.headingLevel("###### Deepest"))
  }

  /** `{text: '', level: -1}` when the cell holds no heading at all - prose is not a section. */
  @Test
  fun proseHasNoHeadingLevel() {
    assertNull(MarkdownDocument.headingLevel("just some prose\n\n- and a list"))
    assertNull(MarkdownDocument.headingLevel(""))
  }

  /**
   * [MarkdownDocument.heading] is the same reduce as [MarkdownDocument.headingLevel], kept as one
   * function rather than two so the tie rule is not implemented twice - reused by the table of
   * contents, which needs the winning heading's text and not only its level.
   */
  @Test
  fun headingIsTheLowestNumberedHeadingWithItsText() {
    val heading = MarkdownDocument.heading("### Detail\n\n# Chapter\n\n## Part")

    assertEquals(1, heading?.level)
    assertEquals("Chapter", heading?.inlines?.plainText())
  }

  /** `prev.level <= curr.level ? prev : curr` keeps `prev` on a tie - the first occurrence wins. */
  @Test
  fun onATiedLevelTheFirstOccurrenceWins() {
    val heading = MarkdownDocument.heading("## A\n\n## B")

    assertEquals("A", heading?.inlines?.plainText())
  }

  @Test
  fun proseHasNoHeading() {
    assertNull(MarkdownDocument.heading("just some prose"))
  }

  /**
   * Setext headings count, because the level is *parsed* rather than pattern-matched: JupyterLab
   * renders the source and reads back `<h1>`-`<h6>` (`packages/toc/src/utils/markdown.ts:78-114`).
   * A `#` a regex would find is not always a heading, and a heading does not always start with one.
   */
  @Test
  fun readsSetextHeadingsAndIgnoresHashesThatAreNotOne() {
    assertEquals(1, MarkdownDocument.headingLevel("Chapter\n======="))
    assertEquals(2, MarkdownDocument.headingLevel("Section\n-------"))
    assertNull(MarkdownDocument.headingLevel("```\n# not a heading, it is a comment\n```"))
    assertNull(MarkdownDocument.headingLevel("a # in the middle of a line"))
  }

  /**
   * Top-level only. `getHeadingTokens` iterates `_marked.lexer(content)` and looks at each token's
   * own type (`packages/markedparser-extension/src/index.ts:268-298`), so a heading nested inside a
   * blockquote or a list item is never a token in that list and never a section boundary.
   */
  @Test
  fun aNestedHeadingIsNotTheCellsHeading() {
    assertNull(MarkdownDocument.headingLevel("> # quoted"))
    assertNull(MarkdownDocument.headingLevel("- # inside an item"))
    assertEquals(2, MarkdownDocument.headingLevel("## Real\n\n> # quoted"))
  }

  /**
   * `getHeadingTokens` keeps an `html` token holding a heading tag and reads its level off the
   * rendered `<h1>`-`<h6>` (jupyterlab@7f0e690 packages/markedparser-extension/src/index.ts:281-309).
   */
  @Test
  fun anHtmlHeadingBlockIsTheCellsHeading() {
    assertEquals(1, MarkdownDocument.headingLevel("<h1>Title</h1>"))
  }
}
