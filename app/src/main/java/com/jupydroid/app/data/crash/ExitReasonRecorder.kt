package com.jupydroid.app.data.crash

import com.jupydroid.app.domain.model.ErrorLogOrigin
import com.jupydroid.app.domain.port.ErrorLog
import com.jupydroid.app.domain.port.ProcessExits

/**
 * Appends one entry for each death of an earlier process that [exits] has not handed over, so a native crash or
 * an ANR - where the uncaught-exception handler never ran - still reaches the log.
 *
 * The deaths are marked reported only after every append returned: a log that refuses a line leaves them for the
 * next start rather than losing them, and the refusal reaches the caller instead of being swallowed. A death
 * [ProcessExit.toEntry][com.jupydroid.app.domain.model.ProcessExit.toEntry] declines (one somebody chose) is
 * marked too, so it is not read again.
 *
 * ```
 * ExitReasonRecorder(container.processExits, container.errorLog, origin).recordUnreported()
 * ```
 */
class ExitReasonRecorder(
  private val exits: ProcessExits,
  private val errorLog: ErrorLog,
  private val origin: ErrorLogOrigin,
) {
  suspend fun recordUnreported() {
    val unreported = exits.unreported()
    unreported.mapNotNull { it.toEntry(origin) }.forEach { errorLog.append(it) }
    exits.markReported(unreported)
  }
}
