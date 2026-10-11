package com.jupydroid.app.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jupydroid.app.richtext.RichInline
import com.jupydroid.app.richtext.RichInlineStyles
import com.jupydroid.app.richtext.altText
import com.jupydroid.app.richtext.images
import com.jupydroid.app.richtext.richImageId
import com.jupydroid.app.richtext.toAnnotatedString

/**
 * A run of inlines as one `Text`, with each image drawn in its slot, in the line, the way Jewel
 * draws one (intellij-community@406a27a8 `platform/jewel/markdown/core/.../DefaultInlineMarkdownRenderer.kt`,
 * `renderImage`) - so a row of badges stays a row.
 *
 * An image that resolves to nothing leaves its slot unfilled, and the slot's text - its alt - draws
 * instead.
 *
 * ```
 * RichInlineText(paragraph.inlines, richInlineStyles(), richTextProseStyle())
 * ```
 */
@Composable
internal fun RichInlineText(inlines: List<RichInline>, styles: RichInlineStyles, style: TextStyle) {
  val text = inlines.toAnnotatedString(styles)
  val images = remember(inlines) { inlines.images() }
  if (images.isEmpty()) {
    Text(text, style = style)
    return
  }
  BoxWithConstraints {
    val pictures = rememberResolvedImages(images)
    Text(text, style = style, inlineContent = inlineImages(images, pictures, maxWidth))
  }
}

/** Each image's picture, null until it resolves and for good when it resolves to nothing. */
@Composable
private fun rememberResolvedImages(images: List<RichInline.Image>): List<ImageBitmap?> {
  val resolver = LocalRichImageResolver.current
  val pictures by produceState<List<ImageBitmap?>>(List(images.size) { null }, images, resolver) {
    value = images.map { image -> resolver.imageBytes(image.source)?.decodedImageBitmap() }
  }
  return pictures
}

/**
 * Decoded as an image output is (`OutputImage`): `BitmapFactory` sniffs the format from the header,
 * and null is bytes that are no image it reads. The header is read first, so an image too large to
 * draw is shrunk while it decodes ([imageSampleSize]) rather than decoded whole and thrown at draw.
 */
private fun ByteArray.decodedImageBitmap(): ImageBitmap? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(this, 0, size, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
  val sampled = BitmapFactory.Options().apply { inSampleSize = imageSampleSize(bounds.outWidth, bounds.outHeight) }
  return BitmapFactory.decodeByteArray(this, 0, size, sampled)?.asImageBitmap()
}

@Composable
private fun inlineImages(
  images: List<RichInline.Image>,
  pictures: List<ImageBitmap?>,
  columnWidth: Dp,
): Map<String, InlineTextContent> = pictures.withIndex()
  .mapNotNull { (index, picture) -> picture?.let { richImageId(index) to inlineImage(it, images[index], columnWidth) } }
  .toMap()

/**
 * The picture at its own width with a pixel to a dp, shrunk to the column with its ratio kept -
 * an image output's rule (ADR-0086), which is JupyterLab's `max-width: 100%` and `height: auto` on
 * every `<img>`.
 */
@Composable
private fun inlineImage(picture: ImageBitmap, image: RichInline.Image, columnWidth: Dp): InlineTextContent {
  val width = minOf(picture.width.dp, columnWidth)
  val height = width * (picture.height / picture.width.toFloat())
  val placeholder = with(LocalDensity.current) {
    Placeholder(width.toSp(), height.toSp(), PlaceholderVerticalAlign.Bottom)
  }
  return InlineTextContent(placeholder) {
    Image(
      bitmap = picture,
      contentDescription = image.altText(),
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.FillBounds,
    )
  }
}
