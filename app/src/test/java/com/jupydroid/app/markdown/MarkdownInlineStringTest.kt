package com.jupydroid.app.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Building the `AnnotatedString` is separated from drawing it precisely so it can be tested
 * here, on the JVM, rather than only by looking at a screenshot.
 */
class MarkdownInlineStringTest {
  private val styles = MarkdownInlineStyles(
    emphasis = SpanStyle(fontStyle = FontStyle.Italic),
    strong = SpanStyle(fontWeight = FontWeight.Bold),
    code = SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Red),
    link = SpanStyle(color = Color.Blue),
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
  fun aLinkShowsItsTextAndCarriesItsDestination() {
    val built = MarkdownDocument.parse("see [docs](https://x.dev)").paragraphInlines().toAnnotatedString(styles)

    assertEquals("see docs", built.text)
    assertEquals(Color.Blue, built.spanStyles.single().item.color)
    val link = built.getStringAnnotations(MARKDOWN_LINK_TAG, 0, built.length).single()
    assertEquals("https://x.dev", link.item)
    assertEquals("docs", built.text.substring(link.start, link.end))
  }

  @Test
  fun aSoftBreakBecomesASpaceAndAHardBreakANewline() {
    assertEquals("one two", MarkdownDocument.parse("one\ntwo").paragraphInlines().toAnnotatedString(styles).text)
    assertEquals("one\ntwo", MarkdownDocument.parse("one  \ntwo").paragraphInlines().toAnnotatedString(styles).text)
  }

  private fun List<MarkdownBlock>.paragraphInlines(): List<MarkdownInline> =
    (single() as MarkdownBlock.Paragraph).inlines
}
