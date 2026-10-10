package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.redrawnDisplay
import com.jupydroid.app.domain.model.withDisplayReplaced
import com.jupydroid.app.domain.model.withOutputFolded
import com.jupydroid.app.domain.port.KernelEvent

/**
 * What one kernel output does to the notebook's outputs. Two are instructions rather than results,
 * and neither lands in [EditorUiState.outputs]: `update_display_data` redraws outputs already shown
 * ([withDisplayUpdated]), and `clear_output` empties a cell.
 *
 * `clear_output` is an instruction to the *model*, which is where it is executed: an immediate
 * one empties the cell's list now, and a `wait` one is held in [clearsPending] and spent by the
 * next output to arrive - the in-place swap `clear(wait)` exists for
 * (`packages/outputarea/src/model.ts:301-311`, jupyterlab@7f0e6906). It never lands in [outputs]:
 * what it drew would render as nothing and persist as `{}`, which nbformat refuses to validate.
 */
internal fun EditorUiState.afterOutput(event: KernelEvent.Output): EditorUiState = when (val output = event.output) {
  is CellOutput.ClearOutput -> afterClear(event.cellId, output.wait)
  is CellOutput.DisplayUpdate -> withDisplayUpdated(output.display)
  else -> withOutputAdded(event.cellId, output)
}

private fun EditorUiState.afterClear(cellId: String, wait: Boolean): EditorUiState = if (wait) {
  copy(clearsPending = clearsPending + cellId)
} else {
  copy(outputs = outputs - cellId)
}

/**
 * [output] joins [cellId]'s outputs, spending a pending `clear(wait)`. A display carrying an id
 * already shown first updates the earlier ones, then is appended, as JupyterLab's `_onIOPub` does.
 */
private fun EditorUiState.withOutputAdded(cellId: String, output: CellOutput): EditorUiState {
  val shown = output.redrawnDisplay()?.let { withDisplayUpdated(it) } ?: this
  val carried = if (cellId in clearsPending) emptyList() else shown.outputs[cellId].orEmpty()
  return shown.copy(
    outputs = shown.outputs + (cellId to carried.withOutputFolded(output)),
    clearsPending = clearsPending - cellId,
  )
}

/**
 * Every cell's outputs shown with [display]'s id become [display] - the whole notebook, so
 * `h.update()` from a later cell reaches the cell that showed `h` (ADR-0085). A pending
 * `clear(wait)` is left for the next output *added*, as JupyterLab's `set` leaves `_clearNext`.
 */
private fun EditorUiState.withDisplayUpdated(display: CellOutput.DisplayData): EditorUiState =
  copy(outputs = outputs.mapValues { (_, cellOutputs) -> cellOutputs.withDisplayReplaced(display) })
