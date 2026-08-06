package com.jupydroid.app.html

import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.RichInline
import com.jupydroid.app.richtext.plainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parse half of rendering a `text/html` output: markup in, the same [RichBlock] model a
 * markdown cell produces out, with no Compose and no WebView anywhere near it.
 */
class HtmlDocumentTest {
  @Test
  fun readsAHeadingWithItsLevel() {
    val heading = HtmlDocument.parse("<h3>Results</h3>").single() as RichBlock.Heading

    assertEquals(3, heading.level)
    assertEquals("Results", heading.inlines.plainText())
  }

  @Test
  fun readsAParagraph() {
    val blocks = HtmlDocument.parse("<p>Just some prose.</p>")

    assertEquals("Just some prose.", (blocks.single() as RichBlock.Paragraph).inlines.plainText())
  }

  /** `<b>`/`<strong>` and `<i>`/`<em>` are the same mark written two ways. */
  @Test
  fun readsBothSpellingsOfBoldAndItalic() {
    val inlines = paragraphOf("<p><b>one</b><strong>two</strong><i>three</i><em>four</em></p>")

    assertEquals(2, inlines.filterIsInstance<RichInline.StrongEmphasis>().size)
    assertEquals(2, inlines.filterIsInstance<RichInline.Emphasis>().size)
    assertEquals("onetwothreefour", inlines.plainText())
  }

  @Test
  fun readsNestedMarksAsNestedInlines() {
    val inlines = paragraphOf("<p><strong>bold <em>both</em></strong></p>")

    val strong = inlines.single() as RichInline.StrongEmphasis
    assertTrue("got ${strong.children}", strong.children.any { it is RichInline.Emphasis })
  }

  @Test
  fun readsInlineCode() {
    val inlines = paragraphOf("<p>run <code>df.head()</code> first</p>")

    assertEquals("df.head()", inlines.filterIsInstance<RichInline.Code>().single().code)
  }

  /** A `<pre>` keeps its newlines, which is the only reason it is not a paragraph. */
  @Test
  fun readsAPreformattedBlockWithItsLineBreaks()  {
    val block = HtmlDocument.parse("<pre>x = 1\ny = 2</pre>").single() as RichBlock.CodeBlock

    assertEquals("x = 1\ny = 2", block.code)
  }

  @Test
  fun readsAnUnorderedList() {
    val list = HtmlDocument.parse("<ul><li>one</li><li>two</li></ul>").single() as RichBlock.ListBlock

    assertEquals(false, list.ordered)
    assertEquals(2, list.items.size)
    assertEquals("one", (list.items[0].single() as RichBlock.Paragraph).inlines.plainText())
  }

  @Test
  fun readsAnOrderedListAndWhereItStarts() {
    val list = HtmlDocument.parse("<ol start=\"3\"><li>three</li><li>four</li></ol>").single() as RichBlock.ListBlock

    assertEquals(true, list.ordered)
    assertEquals(3, list.startFrom)
  }

  @Test
  fun readsABlockQuoteAsBlocksInsideABlock() {
    val quote = HtmlDocument.parse("<blockquote><p>quoted <b>text</b></p></blockquote>").single()
      as RichBlock.BlockQuote

    assertEquals("quoted text", (quote.children.single() as RichBlock.Paragraph).inlines.plainText())
  }

  @Test
  fun readsAThematicBreak() {
    assertEquals(RichBlock.ThematicBreak, HtmlDocument.parse("<hr>").single())
  }

  @Test
  fun readsALinkWithItsDestination() {
    val link = paragraphOf("<p>see <a href=\"https://x.dev\">docs</a></p>")
      .filterIsInstance<RichInline.Link>().single()

    assertEquals("https://x.dev", link.destination)
    assertEquals("docs", link.children.plainText())
  }

  @Test
  fun readsABrAsAHardLineBreak() {
    assertTrue(paragraphOf("<p>one<br>two</p>").any { it is RichInline.HardLineBreak })
  }

  /**
   * The trap this whole slice exists to avoid: pandas wraps every table in a `<style scoped>`
   * block, and a walk that treats unknown elements as text renders that CSS as prose.
   */
  @Test
  fun dropsTheContentOfStyleAndScriptEntirely() {
    val blocks = HtmlDocument.parse(
      "<div><style scoped>.dataframe th { vertical-align: top; }</style>" +
        "<script>alert(1)</script><p>after</p></div>",
    )

    assertEquals("after", (blocks.single() as RichBlock.Paragraph).inlines.plainText())
  }

  /** A `<div>` is a container, not a block of its own: its children flatten into the document. */
  @Test
  fun flattensAContainerIntoItsChildren() {
    val blocks = HtmlDocument.parse("<div><h4>Title</h4><p>body</p></div>")

    assertTrue("got $blocks", blocks[0] is RichBlock.Heading)
    assertTrue("got $blocks", blocks[1] is RichBlock.Paragraph)
    assertEquals(2, blocks.size)
  }

  /** `display(HTML("hello"))` is legal and must not render as nothing. */
  @Test
  fun wrapsLooseTextInAParagraphOfItsOwn() {
    val blocks = HtmlDocument.parse("hello <b>there</b>")

    assertEquals("hello there", (blocks.single() as RichBlock.Paragraph).inlines.plainText())
  }

  /** An unsupported wrapper contributes its content and no mark of its own. */
  @Test
  fun keepsTheContentOfAnUnknownElement() {
    assertEquals("tinted", paragraphOf("<p><span style=\"color:red\">tinted</span></p>").plainText())
  }

  /** HTML collapses runs of whitespace and drops it at a block's edges. */
  @Test
  fun collapsesWhitespaceAndTrimsTheEdgesOfABlock() {
    assertEquals("one two", paragraphOf("<p>\n  one   two\n</p>").plainText())
  }

  /** Indentation between block tags is layout, not a paragraph of spaces. */
  @Test
  fun doesNotTurnWhitespaceBetweenBlocksIntoAParagraph() {
    val blocks = HtmlDocument.parse("<div>\n  <p>one</p>\n  <p>two</p>\n</div>")

    assertEquals(2, blocks.size)
  }

  /** An output is whatever the kernel sent; malformed markup must degrade, never throw. */
  @Test
  fun toleratesEmptyAndMalformedMarkup() {
    assertEquals(emptyList<RichBlock>(), HtmlDocument.parse(""))
    listOf("<p>unclosed", "<b><i>crossed</b></i>", "<table><tr>", "&nbsp;", "<<>>")
      .forEach { HtmlDocument.parse(it) }
  }

  private fun paragraphOf(html: String): List<RichInline> =
    (HtmlDocument.parse(html).single() as RichBlock.Paragraph).inlines
}
