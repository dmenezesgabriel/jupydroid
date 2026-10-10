package com.jupydroid.app.ui.console

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.redrawnDisplay
import com.jupydroid.app.domain.model.withDisplayReplaced
import com.jupydroid.app.domain.model.withOutputFolded

/**
 * What one kernel output does to the transcript - the notebook's `afterOutput` rule, by entry.
 * A display update redraws every entry that showed its id, not only the one running, so
 * `h.update()` from a later entry reaches the entry that showed `h` (ADR-0085); the update itself
 * is spent here and appended nowhere.
 *
 * `clear_output` empties its own entry: an immediate one now, a `wait` one when the entry's next
 * output is added - a console entry is drawn through the same output model a cell is, so it obeys
 * the same `clear(wait)` (`packages/outputarea/src/model.ts:301-311`, jupyterlab@7f0e6906).
 */
internal fun List<ConsoleEntry>.afterOutput(id: String, output: CellOutput): List<ConsoleEntry> = when (output) {
  is CellOutput.ClearOutput -> withEntry(id) { it.cleared(output.wait) }
  is CellOutput.DisplayUpdate -> withDisplayRedrawn(output.display)
  else -> withOutputAdded(id, output)
}

private fun ConsoleEntry.cleared(wait: Boolean): ConsoleEntry =
  if (wait) copy(clearPending = true) else copy(outputs = emptyList())

/** [output] joins entry [id]'s outputs, spending a pending `clear(wait)`, after redrawing a shown id. */
private fun List<ConsoleEntry>.withOutputAdded(id: String, output: CellOutput): List<ConsoleEntry> {
  val redrawn = output.redrawnDisplay()?.let { withDisplayRedrawn(it) } ?: this
  return redrawn.withEntry(id) { entry ->
    val carried = if (entry.clearPending) emptyList() else entry.outputs
    entry.copy(outputs = carried.withOutputFolded(output), clearPending = false)
  }
}

/** Every entry showing [display]'s id redraws it; an entry it misses is kept as it is, for Compose. */
private fun List<ConsoleEntry>.withDisplayRedrawn(display: CellOutput.DisplayData): List<ConsoleEntry> = map { entry ->
  val outputs = entry.outputs.withDisplayReplaced(display)
  if (outputs === entry.outputs) entry else entry.copy(outputs = outputs)
}

internal inline fun List<ConsoleEntry>.withEntry(
  id: String,
  transform: (ConsoleEntry) -> ConsoleEntry,
): List<ConsoleEntry> = map { if (it.id == id) transform(it) else it }
