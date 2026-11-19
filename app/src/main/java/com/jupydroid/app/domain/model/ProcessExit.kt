package com.jupydroid.app.domain.model

/**
 * Why the platform says a process of this app ended - the app's own words, not `android.app.ApplicationExitInfo`'s.
 *
 * [kind] is what an entry for it is called in the log; null for a death someone chose (the person swiping the app
 * away, the app finishing itself, an update), which says nothing went wrong and would fill the log's size cap with
 * one line a day.
 */
enum class ProcessExitReason(val kind: ErrorLogKind?) {
  CRASH(ErrorLogKind.EXIT),
  CRASH_NATIVE(ErrorLogKind.NATIVE),
  ANR(ErrorLogKind.ANR),
  LOW_MEMORY(ErrorLogKind.EXIT),
  SIGNALED(ErrorLogKind.EXIT),
  INITIALIZATION_FAILURE(ErrorLogKind.EXIT),
  EXCESSIVE_RESOURCE_USAGE(ErrorLogKind.EXIT),
  DEPENDENCY_DIED(ErrorLogKind.EXIT),
  UNKNOWN(ErrorLogKind.EXIT),
  DELIBERATE(null),
}

/**
 * One death of the app's process as the platform recorded it - the only trace of a native crash or an ANR, where
 * the uncaught-exception handler never runs.
 *
 * ```
 * ProcessExit(timestampMillis = now, pid = 3363, reason = ProcessExitReason.ANR, description = "").toEntry(origin)
 * ```
 */
data class ProcessExit(
  val timestampMillis: Long,
  val pid: Int,
  val reason: ProcessExitReason,
  val description: String,
) {
  /** The log's line for this death, or null when [ProcessExitReason.kind] says it is not worth one. */
  fun toEntry(origin: ErrorLogOrigin): ErrorLogEntry? {
    val kind = reason.kind ?: return null
    return ErrorLogEntry(
      timestampMillis = timestampMillis,
      kind = kind,
      exceptionClass = reason.name,
      message = listOf("pid $pid", description).filter(String::isNotEmpty).joinToString(": "),
      appVersion = origin.appVersion,
      deviceModel = origin.deviceModel,
      androidVersion = origin.androidVersion,
    )
  }
}
