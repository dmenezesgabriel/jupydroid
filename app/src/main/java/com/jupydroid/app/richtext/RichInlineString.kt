package com.jupydroid.app.richtext

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/** Where a link's destination is stashed, so a tap can find it without re-parsing. */
const val RICH_LINK_TAG = "rich-link"

/** What each inline mark is painted with. Passed in so this stays testable off a device. */
data class RichInlineStyles(
  val emphasis: SpanStyle,
  val strong: SpanStyle,
  val code: SpanStyle,
  val link: SpanStyle,
  val struck: SpanStyle,
)

/**
 * Flattens the inline tree into one styled string.
 *
 * Nesting survives because [withStyle] merges rather than replaces: `**bold *both* **` leaves
 * the inner run carrying both spans.
 *
 * Inline code takes [SpanStyle.background], which is what Jewel does too
 * (`DefaultInlineMarkdownRenderer.kt`). That paints a flat rectangle with no padding or
 * rounding; measuring the run with `TextLayoutResult` and drawing it behind the text is the
 * upgrade, and is not paid for until the flat one is the thing that looks wrong.
 *
 * ```
 * MarkdownDocument.parse("a **b**").inlines.toAnnotatedString(styles)
 * ```
 */
fun List<RichInline>.toAnnotatedString(styles: RichInlineStyles): AnnotatedString =
  buildAnnotatedString { appendInlines(this@toAnnotatedString, styles) }

private fun AnnotatedString.Builder.appendInlines(inlines: List<RichInline>, styles: RichInlineStyles) {
  inlines.forEach { inline ->
    when (inline) {
      is RichInline.Text -> append(inline.text)
      is RichInline.Code -> withStyle(styles.code) { append(inline.code) }
      is RichInline.Emphasis -> withStyle(styles.emphasis) { appendInlines(inline.children, styles) }
      is RichInline.StrongEmphasis -> withStyle(styles.strong) { appendInlines(inline.children, styles) }
      is RichInline.Strikethrough -> withStyle(styles.struck) { appendInlines(inline.children, styles) }
      is RichInline.Link -> appendLink(inline, styles)
      RichInline.SoftLineBreak -> append(" ")
      RichInline.HardLineBreak -> append("\n")
    }
  }
}

private fun AnnotatedString.Builder.appendLink(link: RichInline.Link, styles: RichInlineStyles) {
  val start = length
  withStyle(styles.link) { appendInlines(link.children, styles) }
  addStringAnnotation(RICH_LINK_TAG, link.destination, start, length)
}
