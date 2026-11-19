package com.jupydroid.app.data.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import com.jupydroid.app.domain.model.ProcessExit
import com.jupydroid.app.domain.model.ProcessExitReason
import com.jupydroid.app.domain.port.ProcessExits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The platform's own record of how this app's processes ended (`ActivityManager.getHistoricalProcessExitReasons`,
 * Android 11), which is the only trace of a native crash or an ANR.
 *
 * The newest death already handed over is kept in [markFile], a file of its own and not a line of the error log:
 * clearing or pruning the log must not make the platform's memory of old deaths read as new ones. A missing or
 * unreadable mark reads as "nothing handed over", so the worst case is a death recorded twice, never lost.
 *
 * ```
 * ActivityManagerProcessExits(context.getSystemService(ActivityManager::class.java), File(filesDir, "exit-mark"))
 * ```
 */
class ActivityManagerProcessExits(private val activityManager: ActivityManager, private val markFile: File) :
  ProcessExits {
  override suspend fun unreported(): List<ProcessExit> = withContext(Dispatchers.IO) {
    val mark = readMark()
    // A null package is the caller's own; a `maxNum` of 0 is every record the platform keeps.
    activityManager.getHistoricalProcessExitReasons(null, 0, 0)
      .filter { it.timestamp > mark }
      .sortedBy { it.timestamp }
      .map { ProcessExit(it.timestamp, it.pid, processExitReasonOf(it.reason), it.description.orEmpty()) }
  }

  override suspend fun markReported(exits: List<ProcessExit>) {
    val newest = exits.maxOfOrNull { it.timestampMillis } ?: return
    withContext(Dispatchers.IO) { markFile.writeText(maxOf(readMark(), newest).toString()) }
  }

  private fun readMark(): Long = markFile.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: 0L
}

/** Anything this build does not name is [ProcessExitReason.UNKNOWN] and recorded, never silently dropped. */
internal fun processExitReasonOf(platformReason: Int): ProcessExitReason = when (platformReason) {
  ApplicationExitInfo.REASON_CRASH -> ProcessExitReason.CRASH
  ApplicationExitInfo.REASON_CRASH_NATIVE -> ProcessExitReason.CRASH_NATIVE
  ApplicationExitInfo.REASON_ANR -> ProcessExitReason.ANR
  ApplicationExitInfo.REASON_LOW_MEMORY -> ProcessExitReason.LOW_MEMORY
  ApplicationExitInfo.REASON_SIGNALED -> ProcessExitReason.SIGNALED
  ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> ProcessExitReason.INITIALIZATION_FAILURE
  ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> ProcessExitReason.EXCESSIVE_RESOURCE_USAGE
  ApplicationExitInfo.REASON_DEPENDENCY_DIED -> ProcessExitReason.DEPENDENCY_DIED
  ApplicationExitInfo.REASON_EXIT_SELF,
  ApplicationExitInfo.REASON_USER_REQUESTED,
  ApplicationExitInfo.REASON_USER_STOPPED,
  ApplicationExitInfo.REASON_PERMISSION_CHANGE,
  -> ProcessExitReason.DELIBERATE
  else -> ProcessExitReason.UNKNOWN
}
