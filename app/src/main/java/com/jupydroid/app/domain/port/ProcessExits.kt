package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.ProcessExit

/**
 * The platform's record of how this app's earlier processes ended, read once: what was already handed over is not
 * handed over again, even after the log it went into was cleared. How "already" is remembered is the
 * implementation's, so a caller never holds a cursor.
 *
 * ```
 * val exits = processExits.unreported()
 * exits.forEach(::record)
 * processExits.markReported(exits)
 * ```
 */
interface ProcessExits {
  /** The deaths since the last [markReported], oldest first; every one the platform still keeps on a first run. */
  suspend fun unreported(): List<ProcessExit>

  /** Remembers that [exits], and every death before the newest of them, are recorded. Nothing to do for none. */
  suspend fun markReported(exits: List<ProcessExit>)
}
