package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName

/** Past this many outputs a cell draws only the first ones - JupyterLab's `maxNumberOutputs` default. */
const val OUTPUT_COUNT_LIMIT = 50

/** The row after the drawn outputs, worded as JupyterLab's `TrimmedOutputs` button. */
const val SHOW_MORE_OUTPUTS = "Show more outputs"

/**
 * How many of [outputs] are drawn, or null when all of them are - the rest stay in the model, so a
 * save, a search and the collapsed preview still read every one.
 *
 * JupyterLab's count cut: outputs at index `limit` and past it get no widget, only a button that
 * draws them (jupyterlab@7f0e690 packages/outputarea/src/widget.ts, `_insertOutput`). It counts the
 * model's outputs, not the drawn ones, and so does this. When an answer to `input()` lands past the
 * limit, `onInputRequest` raises the cap to reach it, so the last stdin echo is always drawn.
 *
 * ```kotlin
 * drawnOutputCountOf(List(300) { CellOutput.Stream(StreamName.STDOUT, "$it\n") })  // 50
 * ```
 */
fun drawnOutputCountOf(outputs: List<CellOutput>, limit: Int = OUTPUT_COUNT_LIMIT): Int? {
  val lastEcho = outputs.indexOfLast { it is CellOutput.Stream && it.name == StreamName.STDIN }
  return maxOf(limit, lastEcho + 1).takeIf { it < outputs.size }
}

/**
 * Whether the hit the find bar's arrows stepped onto is in an output past the [drawn] ones. [search]
 * has one entry per rendered output, and the drawn outputs are a prefix of those, so an index past
 * [drawn] is one with no widget.
 *
 * ```kotlin
 * isCurrentHitUndrawn(outputSearchHighlightsOf(cellId), drawn = 50)  // true when stepped onto output 277
 * ```
 */
fun isCurrentHitUndrawn(search: List<CellSearchHighlight>, drawn: Int): Boolean =
  search.drop(drawn).any { it.current != null && it.currentStepped }
