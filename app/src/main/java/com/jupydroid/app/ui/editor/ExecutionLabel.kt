package com.jupydroid.app.ui.editor

import java.time.Duration
import java.util.Locale

/**
 * The execution count at a code cell's gutter foot, in the reference's own notation - or null when
 * there is nothing to say. P2v-2 moved it here from the header strip, whose prompt it replaces:
 * `[7]:` above the editor and `[7]` at its foot are one fact twice.
 *
 * `[ ]` never run, `[*]` working, `[7]` ran seventh - read out of the reference rather than
 * remembered: `cellExecution.ts` draws `[N]` from the kernel's count and `[ ]` when the cell has
 * never run (`view/cellParts/cellExecution.ts:94,133,181,194`). **No trailing colon**: that was
 * JupyterLab's `InputPrompt`, and VS Code's execution count is the bracket alone
 * (`media/notebook.css:256-266`). Drawn only where a kernel can put a count - code cells;
 * markdown renders and raw does nothing (`cellexecutor.ts:36-141`) - which is [CellGutter]'s
 * switch rather than this function's.
 *
 * `[-]` is ours: Jupyter has no notation for skipped, because Jupyter's Run All does not stop at
 * a failure. Without it a skipped cell draws as `[ ]` and is indistinguishable from one nobody
 * ran - which is precisely the state SKIPPED exists to tell apart.
 */
internal fun executionCountLabel(status: ExecutionStatus, count: Int?): String? = when {
  status == ExecutionStatus.QUEUED || status == ExecutionStatus.RUNNING -> "[*]"
  // See KDoc: ours, because Run All stops and the queue below the failure must say so.
  status == ExecutionStatus.SKIPPED -> "[-]"
  // Before the queued checks would hide a count kernel history walked back in - a cell the
  // document re-opened with is not one nobody ran.
  count != null -> "[$count]"
  else -> "[ ]"
}

/**
 * How long the last run took, or null when there is nothing worth saying.
 *
 * Tenths of a second up to a minute, minutes and seconds beyond - past a minute the tenth is not
 * the digit anyone is reading. The reference shows the same thing from its own
 * `EXECUTION_TIME_COLOR`, which is the colour this is drawn in.
 *
 * A negative duration is reported as nothing rather than as nonsense: the device's clock can move
 * under a running cell, and `Instant`s taken either side of that would subtract to less than zero.
 */
internal fun runDurationLabel(took: Duration?): String? {
  val millis = took?.takeUnless { it.isNegative }?.toMillis() ?: return null
  val whole = millis / MILLIS_PER_SECOND
  return if (whole < SECONDS_PER_MINUTE) {
    String.format(Locale.US, "ran in %.1f s", millis / MILLIS_PER_SECOND.toDouble())
  } else {
    String.format(Locale.US, "ran in %d m %02d s", whole / SECONDS_PER_MINUTE, whole % SECONDS_PER_MINUTE)
  }
}

private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

/**
 * What a screen reader says about a cell's run.
 *
 * `stateDescription` rather than more `contentDescription`: TalkBack announces state separately
 * from identity, so this rides alongside "Run cell" instead of replacing it.
 */
internal fun ExecutionStatus.spokenState(): String = when (this) {
  ExecutionStatus.NOT_STARTED -> "not run"
  ExecutionStatus.QUEUED -> "queued"
  ExecutionStatus.RUNNING -> "running"
  ExecutionStatus.STOPPED_OK -> "finished"
  ExecutionStatus.STOPPED_ERROR -> "failed"
  ExecutionStatus.CANCELLED -> "stopped"
  ExecutionStatus.SKIPPED -> "skipped"
}
