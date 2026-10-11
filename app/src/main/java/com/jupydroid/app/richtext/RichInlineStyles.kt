package com.jupydroid.app.richtext

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

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
  buildAnnotatedString { appendInlines(this@toAnnotatedString, styles, ImageSlotCounter()) }

/**
 * The id of the `n`th image's slot, counted as [images] lists them - what a renderer keys its
 * `InlineTextContent` by.
 *
 * ```
 * richImageId(0) // "rich-image-0"
 * ```
 */
fun richImageId(index: Int): String = "rich-image-$index"

/** How many image slots have been written so far, so each takes the next number. */
private class ImageSlotCounter {
  var next = 0
}

private fun AnnotatedString.Builder.appendInlines(
  inlines: List<RichInline>,
  styles: RichInlineStyles,
  slots: ImageSlotCounter,
) {
  inlines.forEach { inline ->
    when (inline) {
      is RichInline.Text -> append(inline.text)
      is RichInline.Code -> withStyle(styles.code) { append(inline.code) }
      is RichInline.Emphasis -> withStyle(styles.emphasis) { appendInlines(inline.children, styles, slots) }
      is RichInline.StrongEmphasis -> withStyle(styles.strong) { appendInlines(inline.children, styles, slots) }
      is RichInline.Strikethrough -> withStyle(styles.struck) { appendInlines(inline.children, styles, slots) }
      is RichInline.Link -> appendLink(inline, styles, slots)
      is RichInline.Image -> appendInlineContent(richImageId(slots.next++), inline.altText())
      RichInline.SoftLineBreak -> append(" ")
      RichInline.HardLineBreak -> append("\n")
    }
  }
}

/**
 * What an image's slot holds, and so what draws when no picture fills it: Jewel keeps a failed
 * image's text (intellij-community@406a27a8 `platform/jewel/markdown/core/.../DefaultMarkdownBlockRenderer.kt`,
 * `resolveImages`). A slot cannot hold nothing, so no alt is JupyterLab's default `alt`, "Image"
 * (jupyterlab@7f0e690 packages/rendermime/src/renderers.ts, `handleDefaults`).
 */
internal fun RichInline.Image.altText(): String = alt.plainText().ifEmpty { "Image" }

/**
 * A link is a [LinkAnnotation.Url], which a `Text` makes tappable by itself and hands to
 * `LocalUriHandler` - so the tap is the link's own and whatever the text sits in keeps the rest.
 * The destination is carried as written; judging it is the handler's.
 */
private fun AnnotatedString.Builder.appendLink(
  link: RichInline.Link,
  styles: RichInlineStyles,
  slots: ImageSlotCounter,
) {
  withLink(LinkAnnotation.Url(link.destination, TextLinkStyles(styles.link))) {
    appendInlines(link.children, styles, slots)
  }
}
