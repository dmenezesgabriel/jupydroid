package com.jupydroid.app.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import com.jupydroid.app.richtext.PlainTextLink

/**
 * This output's text with each of [links] made tappable, painted with [style].
 *
 * A [LinkAnnotation.Url], as a markdown link is (`com.jupydroid.app.richtext.RichInlineStyles`), so
 * the tap reaches the same `LocalUriHandler` and goes where a rendered link goes - a path into the
 * workspace included, which `WorkspaceLinkUriHandler` takes where one is provided. Laid over the
 * output's colours rather than in place of them, and returned as it is when there is no link.
 *
 * ```
 * output.coloured(palette).withPlainTextLinks(output.text.plainTextLinks(output.isError), richInlineStyles().link)
 * ```
 */
fun AnnotatedString.withPlainTextLinks(links: List<PlainTextLink>, style: SpanStyle): AnnotatedString {
  if (links.isEmpty()) return this
  return buildAnnotatedString {
    append(this@withPlainTextLinks)
    links.forEach { addLink(LinkAnnotation.Url(it.destination, TextLinkStyles(style)), it.start, it.end) }
  }
}
