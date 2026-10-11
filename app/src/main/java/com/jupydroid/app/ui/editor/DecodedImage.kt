package com.jupydroid.app.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * A picture a canvas draws, beside the width its image has.
 *
 * The two differ for an image shrunk while it decoded: [bitmap] is then a fraction of the image's
 * width, and an image draws at its own width whatever its decoder did, as an `<img>` does
 * (ADR-0087). So [ownWidth] is the header's, never the bitmap's.
 */
internal class DecodedImage(val bitmap: ImageBitmap, val ownWidth: Int)

/**
 * These bytes as a picture a canvas draws, or null when they are no image `BitmapFactory` reads. It
 * sniffs the format from the header, and decodes a GIF's first frame only.
 *
 * The header is read first, so an image too large to draw is shrunk while it decodes
 * ([imageSampleSize]) rather than decoded whole and thrown at draw (ADR-0087). An image output and a
 * markdown cell's image both decode here.
 *
 * ```
 * val image = remember(output) { output.encoded.decodedImage() } ?: return
 * ```
 */
internal fun ByteArray.decodedImage(): DecodedImage? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(this, 0, size, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
  val sampled = BitmapFactory.Options().apply { inSampleSize = imageSampleSize(bounds.outWidth, bounds.outHeight) }
  val bitmap = BitmapFactory.decodeByteArray(this, 0, size, sampled)
  return bitmap?.let { DecodedImage(it.asImageBitmap(), bounds.outWidth) }
}
