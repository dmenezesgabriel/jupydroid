package com.jupydroid.app.domain.model

/**
 * Which build wrote an [ErrorLogEntry]: the three facts that tell a reader whether a crash is this release's, on
 * this phone's Android.
 *
 * A type of its own because the three are strings that mean different things and travel together into every
 * entry, the crash handler's and the exit reasons' alike; as bare parameters they swap without a compiler's help.
 *
 * ```
 * ErrorLogOrigin(appVersion = "0.9.1", deviceModel = "Pixel 9", androidVersion = "16")
 * ```
 */
data class ErrorLogOrigin(val appVersion: String, val deviceModel: String, val androidVersion: String)
