package com.jupydroid.app.html

import com.jupydroid.app.richtext.CellAlignment
import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.RichInline
import com.jupydroid.app.richtext.plainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
  fun readsAPreformattedBlockWithItsLineBreaks() {
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

  /**
   * The whole point of the slice, against the captured fixture rather than markup written
   * from memory: a DataFrame is one table and nothing else - no paragraph of stylesheet.
   */
  @Test
  fun readsARealPandasDataFrameAsOneTable() {
    val table = HtmlDocument.parse(PANDAS_DATAFRAME_HTML).single() as RichBlock.Table

    assertEquals(3, table.rows.size)
    assertEquals(listOf("", "a", "b"), table.rows[0].map { it.inlines.plainText() })
    assertEquals(listOf("0", "1", "x"), table.rows[1].map { it.inlines.plainText() })
    assertEquals(listOf("1", "2", "y"), table.rows[2].map { it.inlines.plainText() })
  }

  /** `thead`'s cells are headers, and so is the index `<th>` pandas puts inside `tbody`. */
  @Test
  fun marksHeaderCellsWhereverTheySit() {
    val table = HtmlDocument.parse(PANDAS_DATAFRAME_HTML).single() as RichBlock.Table

    assertEquals(listOf(true, true, true), table.rows[0].map { it.isHeader })
    assertEquals(listOf(true, false, false), table.rows[1].map { it.isHeader })
  }

  /** A `<tr>` need not sit in a `thead` or a `tbody`; hand-written HTML often skips both. */
  @Test
  fun readsRowsWithNoRowGroupAroundThem() {
    val table = HtmlDocument.parse("<table><tr><th>h</th></tr><tr><td>v</td></tr></table>").single()
      as RichBlock.Table

    assertEquals(listOf("h", "v"), table.rows.map { it.single().inlines.plainText() })
  }

  /** A grid is only a grid if the rows are the same length, so short ones are padded. */
  @Test
  fun padsARaggedRowSoEveryRowIsTheSameLength() {
    val table = HtmlDocument.parse("<table><tr><td>a</td><td>b</td></tr><tr><td>c</td></tr></table>")
      .single() as RichBlock.Table

    assertEquals(listOf(2, 2), table.rows.map { it.size })
    assertEquals("", table.rows[1][1].inlines.plainText())
  }

  /**
   * Right, header and padding alike: `base.css` right-aligns `td`, `th` and `tr` outside a
   * markdown render (jupyterlab@7f0e690 packages/rendermime/style/base.css, `/* Tables */`).
   */
  @Test
  fun alignsEveryCellOfAnHtmlTableToTheEnd() {
    val table = HtmlDocument.parse("<table><tr><th>h</th><th>i</th></tr><tr><td>v</td></tr></table>")
      .single() as RichBlock.Table

    assertEquals(setOf(CellAlignment.End), table.rows.flatten().map { it.alignment }.toSet())
  }

  /** Nothing to draw is better than an empty frame, and lets the output fall back to its text. */
  @Test
  fun dropsATableThatHasNoRows() {
    assertEquals(emptyList<RichBlock>(), HtmlDocument.parse("<table><caption>Empty</caption></table>"))
  }

  /** A cell keeps its marks - `<td><b>x</b></td>` is bold, not the literal tags. */
  @Test
  fun keepsTheInlineMarksInsideACell() {
    val table = HtmlDocument.parse("<table><tr><td>a <b>bold</b></td></tr></table>").single()
      as RichBlock.Table

    assertTrue("got ${table.rows[0][0]}", table.rows[0][0].inlines.any { it is RichInline.StrongEmphasis })
  }

  /** An `<img>` is the image a markdown `![]()` is, so one resolver and one renderer draw both. */
  @Test
  fun readsAnImgWithItsSourceAltAndWidth() {
    val image = paragraphOf("<img src=\"img/plot.png\" alt=\"a plot\" width=\"120\">").single() as RichInline.Image

    assertEquals("img/plot.png", image.source)
    assertEquals("a plot", image.alt.plainText())
    assertEquals(120, image.width)
  }

  /** Jewel's `parseDimensionSize`: a width in px or no unit is pixels, any other unit is no width. */
  @Test
  fun anImgWidthIsPixelsOnly() {
    assertEquals(120, imageOf("<img src=\"a.png\" width=\"120px\">").width)
    assertNull(imageOf("<img src=\"a.png\" width=\"50%\">").width)
    assertNull(imageOf("<img src=\"a.png\">").width)
  }

  @Test
  fun anImgWithNoAltHasAnEmptyAlt() {
    assertEquals(emptyList<RichInline>(), imageOf("<img src=\"a.png\">").alt)
  }

  /** An image inside a centred paragraph is the paragraph's, between its text. */
  @Test
  fun anImgSitsInTheLineItIsWrittenIn() {
    val inlines = paragraphOf("<p align=\"center\">before <img src=\"a.png\" alt=\"a\"> after</p>")

    assertEquals("before a after", inlines.plainText())
    assertEquals("a.png", inlines.filterIsInstance<RichInline.Image>().single().source)
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

  private fun imageOf(html: String): RichInline.Image = paragraphOf(html).single() as RichInline.Image
}
