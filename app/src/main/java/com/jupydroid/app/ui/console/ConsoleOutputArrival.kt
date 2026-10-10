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
 */
internal fun List<ConsoleEntry>.afterOutput(id: String, output: CellOutput): List<ConsoleEntry> {
  val redrawn = output.redrawnDisplay()?.let { withDisplayRedrawn(it) } ?: this
  if (output is CellOutput.DisplayUpdate) return redrawn
  return redrawn.withEntry(id) { it.copy(outputs = it.outputs.withOutputFolded(output)) }
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
