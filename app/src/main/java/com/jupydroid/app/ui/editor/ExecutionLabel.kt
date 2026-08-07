package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellKind
import java.time.Duration
import java.util.Locale

/**
 * The bracket beside a cell, in Jupyter's own notation.
 *
 * `[ ]` never run, `[*]` working, `[7]` ran seventh - a notation a Jupyter user reads without
 * being told. It replaces a label that showed `[7]` or the word "code", under which a code cell
 * nobody had run and one waiting its turn were the same thing.
 *
 * Markdown is named rather than numbered: it never reaches a kernel, so a bracket would promise
 * a count that cannot arrive.
 */
internal fun executionLabel(kind: CellKind, status: ExecutionStatus, count: Int?): String = when {
  kind == CellKind.MARKDOWN -> kind.label()
  status == ExecutionStatus.QUEUED || status == ExecutionStatus.RUNNING -> "[*]"
  // `[-]` is ours: Jupyter has no notation for skipped, because Jupyter's Run All does not stop
  // at a failure. Without it a skipped cell draws as `[ ]` and is indistinguishable from one
  // nobody ran - which is precisely the state SKIPPED exists to tell apart.
  status == ExecutionStatus.SKIPPED -> "[-]"
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

/** What a cell is, for the kinds that are never numbered. */
internal fun CellKind.label(): String = when (this) {
  CellKind.CODE -> "code"
  CellKind.MARKDOWN -> "markdown"
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
