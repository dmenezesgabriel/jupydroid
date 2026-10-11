package com.jupydroid.app.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * These bytes as a picture a canvas draws, or null when they are no image `BitmapFactory` reads. It
 * sniffs the format from the header, and decodes a GIF's first frame only.
 *
 * The header is read first, so an image too large to draw is shrunk while it decodes
 * ([imageSampleSize]) rather than decoded whole and thrown at draw (ADR-0087). An image output and a
 * markdown cell's image both decode here.
 *
 * ```
 * val picture = remember(output) { output.encoded.decodedImageBitmap() } ?: return
 * ```
 */
internal fun ByteArray.decodedImageBitmap(): ImageBitmap? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(this, 0, size, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
  val sampled = BitmapFactory.Options().apply { inSampleSize = imageSampleSize(bounds.outWidth, bounds.outHeight) }
  return BitmapFactory.decodeByteArray(this, 0, size, sampled)?.asImageBitmap()
}
