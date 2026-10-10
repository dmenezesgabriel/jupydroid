package com.jupydroid.app.domain.model

/**
 * Replaces every output shown with [display]'s `displayId` by [display] itself, as JupyterLab's
 * `_onIOPub` sets each index its display-id map holds (jupyterlab@7f0e690
 * packages/outputarea/src/widget.ts). The id rides on the output rather than in a side map of
 * indices, so nothing has to be kept in step with a clear, an undo or a paste (ADR-0085).
 *
 * A list holding no target is returned as it is, not copied: a notebook-wide update visits every
 * cell, and the ones it misses keep their identity for Compose and for the undo stack's sharing.
 *
 * ```kotlin
 * listOf(DisplayData(frame0, displayId = "d1")).withDisplayReplaced(DisplayData(frame3, displayId = "d1"))
 * // == listOf(DisplayData(frame3, displayId = "d1"))
 * ```
 */
fun List<CellOutput>.withDisplayReplaced(display: CellOutput.DisplayData): List<CellOutput> {
  val shows = { output: CellOutput -> output is CellOutput.DisplayData && output.displayId == display.displayId }
  if (none(shows)) return this
  return map { if (shows(it)) display else it }
}

/**
 * The display an arriving output redraws wherever its id is already shown: an update's own, or a
 * display that carries an id - JupyterLab's `_onIOPub` updates the earlier outputs before appending
 * it (jupyterlab@7f0e690 packages/outputarea/src/widget.ts). Null for anything else.
 *
 * ```kotlin
 * CellOutput.DisplayUpdate(frame3).redrawnDisplay() // == frame3
 * CellOutput.Stream(STDOUT, "a").redrawnDisplay()  // == null
 * ```
 */
fun CellOutput.redrawnDisplay(): CellOutput.DisplayData? = when (this) {
  is CellOutput.DisplayUpdate -> display
  is CellOutput.DisplayData -> takeIf { displayId != null }
  else -> null
}
