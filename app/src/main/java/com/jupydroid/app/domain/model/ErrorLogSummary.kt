package com.jupydroid.app.domain.model

/**
 * What the error log holds right now: [entryCount] entries taking [sizeBytes] bytes of storage.
 *
 * ```
 * ErrorLogSummary(entryCount = 12, sizeBytes = 35_400)
 * ```
 */
data class ErrorLogSummary(val entryCount: Int, val sizeBytes: Long) {
  init {
    require(entryCount >= 0) { "an error log holds a non-negative entry count, got entryCount=$entryCount" }
    require(sizeBytes >= 0) { "an error log takes a non-negative byte count, got sizeBytes=$sizeBytes" }
  }
}
