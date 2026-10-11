package com.jupydroid.app.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import com.jupydroid.app.richtext.PlainTextLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OutputTextLinksTest {
  private val linkStyle = SpanStyle(color = Color.Blue)

  @Test
  fun aFoundLinkCanBeTappedToItsDestination() {
    val text = AnnotatedString("see www.x.org now")

    val linked = text.withWebLinks(listOf(PlainTextLink(4, 13, "https://www.x.org")), linkStyle)

    val link = linked.getLinkAnnotations(0, linked.length).single()
    assertEquals("https://www.x.org", (link.item as LinkAnnotation.Url).url)
    assertEquals("www.x.org", linked.text.substring(link.start, link.end))
    assertEquals(linkStyle, (link.item as LinkAnnotation.Url).styles?.style)
  }

  /** The link is laid over an output's ANSI colours, never in place of them. */
  @Test
  fun anOutputsColoursSurviveItsLinks() {
    val red = AnnotatedString.Range(SpanStyle(color = Color.Red), 0, 3)
    val text = AnnotatedString("see https://a.io", listOf(red))

    val linked = text.withWebLinks(listOf(PlainTextLink(4, 16, "https://a.io")), linkStyle)

    assertEquals(listOf(red), linked.spanStyles)
  }

  @Test
  fun textWithNoLinkIsReturnedAsItIs() {
    val text = AnnotatedString("plain")

    assertSame(text, text.withWebLinks(emptyList(), linkStyle))
  }
}
