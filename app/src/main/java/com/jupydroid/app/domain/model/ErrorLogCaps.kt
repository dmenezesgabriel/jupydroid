package com.jupydroid.app.domain.model

/**
 * How much of the error log the app keeps: at most [maxBytes] in total, and nothing from a UTC day more
 * than [maxAgeDays] days before today.
 *
 * ```
 * ErrorLogCaps(maxBytes = 1_048_576, maxAgeDays = 7)
 * ```
 */
data class ErrorLogCaps(val maxBytes: Long, val maxAgeDays: Int) {
  init {
    require(maxBytes > 0) { "an error log's size cap is a positive byte count, got maxBytes=$maxBytes" }
    require(maxAgeDays > 0) { "an error log's age cap is a positive day count, got maxAgeDays=$maxAgeDays" }
  }
}
