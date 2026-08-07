package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Immutable
import java.time.Duration
import java.time.Instant

/**
 * Where a cell is in its run.
 *
 * The reference's `ProgressStatus` (`visualization/ui/ProgressStatus.kt`), minus the one state
 * this app has no way to reach. It carries seven; the six here are the ones something in this
 * codebase actually produces, and a status nothing can produce is a branch nobody can test.
 *
 * The reference's seventh is its own `SKIPPED`/`CANCELLED` pairing for a queue we do not have in
 * the same shape - ours are produced by Run All stopping at a failure, and by the Stop button.
 */
enum class ExecutionStatus {
  /** Never run, or reset. The state a freshly opened notebook is in. */
  NOT_STARTED,

  /** Run All has claimed it, but the kernel has not started it yet. */
  QUEUED,

  RUNNING,

  STOPPED_OK,

  STOPPED_ERROR,

  /**
   * Interrupted from the Stop button. Distinct from [STOPPED_ERROR] on purpose: the kernel
   * reports a `KeyboardInterrupt` either way, so without this the user's own Stop would be
   * reported back to them as their code being broken.
   */
  CANCELLED,

  /**
   * Queued, then never reached, because an earlier cell in the same Run All raised. Left at
   * [NOT_STARTED] it would look exactly like a cell nobody had ever asked to run.
   */
  SKIPPED,
}

/**
 * One cell's run: what happened, and when.
 *
 * `@Immutable` so a cell composable holding one is skippable. Times are [Instant] rather than
 * elapsed millis because the two ends are recorded by different events, which is also how the
 * reference carries them (`ExecutionStarted(startTime)` / `ExecutionStopped(endTime)`).
 */
@Immutable
data class CellExecution(
  val status: ExecutionStatus,
  val startedAt: Instant? = null,
  val endedAt: Instant? = null,
) {
  /** How long the run took, once it has finished. Null while it is still going. */
  val took: Duration?
    get() = if (startedAt != null && endedAt != null) Duration.between(startedAt, endedAt) else null
}
