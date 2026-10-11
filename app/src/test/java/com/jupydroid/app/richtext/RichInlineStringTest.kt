package com.jupydroid.app.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.jupydroid.app.markdown.MarkdownDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Building the `AnnotatedString` is separated from drawing it precisely so it can be tested
 * here, on the JVM, rather than only by looking at a screenshot.
 */
class RichInlineStringTest {
  private val styles = RichInlineStyles(
    emphasis = SpanStyle(fontStyle = FontStyle.Italic),
    strong = SpanStyle(fontWeight = FontWeight.Bold),
    code = SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Red),
    link = SpanStyle(color = Color.Blue),
    struck = SpanStyle(textDecoration = TextDecoration.LineThrough),
  )

  @Test
  fun plainTextCarriesNoStyle() {
    val built = MarkdownDocument.parse("just words").paragraphInlines().toAnnotatedString(styles)

    assertEquals("just words", built.text)
    assertTrue("expected no spans, got ${built.spanStyles}", built.spanStyles.isEmpty())
  }

  @Test
  fun strongCoversOnlyTheEmphasisedRun() {
    val built = MarkdownDocument.parse("a **b** c").paragraphInlines().toAnnotatedString(styles)

    assertEquals("a b c", built.text)
    val span = built.spanStyles.single()
    assertEquals(FontWeight.Bold, span.item.fontWeight)
    assertEquals("b", built.text.substring(span.start, span.end))
  }

  @Test
  fun strikethroughCoversOnlyTheStruckRun() {
    val built = MarkdownDocument.parse("a ~~b~~ c").paragraphInlines().toAnnotatedString(styles)

    assertEquals("a b c", built.text)
    val span = built.spanStyles.single()
    assertEquals(TextDecoration.LineThrough, span.item.textDecoration)
    assertEquals("b", built.text.substring(span.start, span.end))
  }

  @Test
  fun emphasisIsItalic() {
    val built = MarkdownDocument.parse("*slanted*").paragraphInlines().toAnnotatedString(styles)

    assertEquals(FontStyle.Italic, built.spanStyles.single().item.fontStyle)
  }

  /** Nesting is the reason inlines are a tree rather than a flat run list. */
  @Test
  fun nestedMarksBothApplyToTheInnerRun() {
    val built = MarkdownDocument.parse("**bold *both***").paragraphInlines().toAnnotatedString(styles)

    val inner = built.text.indexOf("both")
    val covering = built.spanStyles.filter { it.start <= inner && it.end >= inner + "both".length }
    assertTrue("expected bold and italic over 'both', got $covering", covering.size >= 2)
  }

  @Test
  fun inlineCodeIsMonospacedAndBacked() {
    val built = MarkdownDocument.parse("run `df.head()`").paragraphInlines().toAnnotatedString(styles)

    val span = built.spanStyles.single()
    assertEquals("df.head()", built.text.substring(span.start, span.end))
    assertEquals(FontFamily.Monospace, span.item.fontFamily)
    assertEquals(Color.Red, span.item.background)
  }

  @Test
  fun aLinkShowsItsTextAndCanBeTappedToItsDestination() {
    val built = MarkdownDocument.parse("see [docs](https://x.dev)").paragraphInlines().toAnnotatedString(styles)

    assertEquals("see docs", built.text)
    val link = built.getLinkAnnotations(0, built.length).single()
    val url = link.item as LinkAnnotation.Url
    assertEquals("https://x.dev", url.url)
    assertEquals(Color.Blue, url.styles?.style?.color)
    assertEquals("docs", built.text.substring(link.start, link.end))
  }

  /**
   * An image is a slot in the string, numbered in document order, so the renderer can put the
   * picture in it; its alt is the text the slot holds, which is what draws when nothing fills it.
   */
  @Test
  fun anImageIsASlotHoldingItsAltText() {
    val built = MarkdownDocument.parse("a ![one](x.png) b ![two](y.png)").paragraphInlines().toAnnotatedString(styles)

    assertEquals("a one b two", built.text)
    val slots = built.getStringAnnotations(0, built.length)
    assertEquals(listOf(richImageId(0), richImageId(1)), slots.map { it.item })
    assertEquals(listOf("one", "two"), slots.map { built.text.substring(it.start, it.end) })
  }

  /** A slot cannot hold nothing, so an image with no alt holds Lab's default, `handleDefaults`'s "Image". */
  @Test
  fun anImageWithNoAltHoldsTheWordImage() {
    val built = MarkdownDocument.parse("![](x.png)").paragraphInlines().toAnnotatedString(styles)

    assertEquals("Image", built.text)
  }

  /** The renderer pairs slot `n` with the `n`th of these, so both walk the tree in the same order. */
  @Test
  fun imagesAreListedInTheOrderTheirSlotsAreNumbered() {
    val inlines = MarkdownDocument.parse("![a](1.png) **![b](2.png)** [![c](3.png)](x)").paragraphInlines()

    assertEquals(listOf("1.png", "2.png", "3.png"), inlines.images().map { it.source })
  }

  @Test
  fun aSoftBreakBecomesASpaceAndAHardBreakANewline() {
    assertEquals("one two", MarkdownDocument.parse("one\ntwo").paragraphInlines().toAnnotatedString(styles).text)
    assertEquals("one\ntwo", MarkdownDocument.parse("one  \ntwo").paragraphInlines().toAnnotatedString(styles).text)
  }

  private fun List<RichBlock>.paragraphInlines(): List<RichInline> = (single() as RichBlock.Paragraph).inlines
}
