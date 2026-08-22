package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellKind
import java.time.Duration
import java.util.Locale

/**
 * The prompt beside a cell, in Jupyter's own notation - or null on a cell the reference gives none.
 *
 * `[ ]:` never run, `[*]:` working, `[7]:` ran seventh - a notation a Jupyter user reads without
 * being told. It replaces a label that showed `[7]` or the word "code", under which a code cell
 * nobody had run and one waiting its turn were the same thing.
 *
 * **The trailing colon is the reference's, not decoration.** `InputPrompt`'s setter writes
 * `` `[${'$'}{value || ' '}]:` `` (`packages/cells/src/inputarea.ts:283-290`), and the empty string
 * falling through `||` to a space is where `[ ]:` comes from.
 *
 * **Nothing that is not code carries a prompt at all**, which is read rather than reasoned:
 * `_setPrompt` has exactly one caller, `CodeCell._updatePrompt`
 * (`packages/cells/src/widget.ts:1693-1700`). `MarkdownCell` and `RawCell` never call it, so
 * `InputPrompt.executionCount` is never set and their prompt node stays textless. Neither ever
 * reaches a kernel either - `runCell` renders the first and falls through to `default: break` for
 * the second (`packages/notebook/src/cellexecutor.ts:36-141`) - so there is no count to show.
 *
 * The words "markdown" and "raw" that used to sit here were this app's own, and they cost more
 * than a word: a heading cell's collapser was drawn in front of one, so two markdown cells in the
 * same frame started 48dp apart (`evidence/p2n-gutter/before/gutter.txt`).
 */
internal fun executionLabel(kind: CellKind, status: ExecutionStatus, count: Int?): String? = when {
  kind != CellKind.CODE -> null
  status == ExecutionStatus.QUEUED || status == ExecutionStatus.RUNNING -> "[*]:"
  // `[-]` is ours: Jupyter has no notation for skipped, because Jupyter's Run All does not stop
  // at a failure. Without it a skipped cell draws as `[ ]:` and is indistinguishable from one
  // nobody ran - which is precisely the state SKIPPED exists to tell apart.
  status == ExecutionStatus.SKIPPED -> "[-]:"
  count != null -> "[$count]:"
  else -> "[ ]:"
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
