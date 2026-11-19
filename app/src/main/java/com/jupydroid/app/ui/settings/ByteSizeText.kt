package com.jupydroid.app.ui.settings

import java.util.Locale

private const val BYTES_PER_STEP = 1024.0

/**
 * [bytes] as a person reads a size: whole bytes under 1 KB, then one decimal of KB or MB (powers of 1024).
 * The root locale keeps the decimal separator a point, so the text a test reads is the one every device shows.
 *
 * ```
 * byteSizeText(2_970) // "2.9 KB"
 * ```
 */
internal fun byteSizeText(bytes: Long): String = when {
  bytes < BYTES_PER_STEP -> "$bytes B"
  bytes < BYTES_PER_STEP * BYTES_PER_STEP -> oneDecimal(bytes / BYTES_PER_STEP, "KB")
  else -> oneDecimal(bytes / (BYTES_PER_STEP * BYTES_PER_STEP), "MB")
}

private fun oneDecimal(value: Double, unit: String): String = String.format(Locale.ROOT, "%.1f %s", value, unit)
