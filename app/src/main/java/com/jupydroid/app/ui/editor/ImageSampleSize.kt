package com.jupydroid.app.ui.editor

/**
 * The `inSampleSize` that decodes a [width] by [height] image into a bitmap a canvas still draws:
 * 1 when it fits, else the smallest power of two that brings it under [DRAWABLE_BITMAP_BYTES].
 *
 * Past that size a hardware canvas throws `"Canvas: trying to draw too large(…bytes) bitmap."`
 * and takes the app with it (AOSP `graphics/java/android/graphics/RecordingCanvas.java`,
 * `throwIfCannotDraw`) - and a few kilobytes of PNG can decode to it. Shrunk, it still draws at
 * the column's width, which is all ADR-0086 lets it take.
 *
 * ```
 * BitmapFactory.Options().apply { inSampleSize = imageSampleSize(bounds.outWidth, bounds.outHeight) }
 * ```
 */
internal fun imageSampleSize(width: Int, height: Int): Int {
  var sampleSize = 1
  while (width.toLong() * height * WORST_CASE_BYTES_PER_PIXEL / (sampleSize.toLong() * sampleSize) >
    DRAWABLE_BITMAP_BYTES
  ) {
    sampleSize *= 2
  }
  return sampleSize
}

/**
 * `RecordingCanvas.MAX_BITMAP_SIZE` is hidden and differs by release: 100 MiB on API 32
 * (`android12L-release`, the app's `minSdk`), 150 MiB on API 36 (`android16-release`), raised by
 * `ro.hwui.max_texture_allocation_size` where a device sets it. The floor holds on all of them.
 */
private const val DRAWABLE_BITMAP_BYTES = 100L * 1024 * 1024

/** A PNG with 16 bits a channel decodes to `RGBA_F16`, twice `ARGB_8888`'s four bytes. */
private const val WORST_CASE_BYTES_PER_PIXEL = 8
