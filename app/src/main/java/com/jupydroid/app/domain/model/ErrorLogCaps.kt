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

  companion object {
    /**
     * The caps the app's bounded logs share. An error entry measured 2.9 KB with a real stack and
     * 18.7 KB with the same thrown 150 frames deep
     * (`DiskErrorLogOnDeviceTest.anEntryWithARealStackHasAMeasuredSize`, dev36), so 1 MiB holds
     * about 360 of the first or 56 of the second; a Space's server log rotates under the same
     * caps so a restart does not overwrite the run before it (ADR-0167).
     */
    val DEFAULT = ErrorLogCaps(maxBytes = 1_048_576, maxAgeDays = 7)
  }
}
