package com.jupydroid.app.markdown

import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.RichInline
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

  // --- heading level, which is what a collapsible section is keyed on (P2f) -------------

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
}
