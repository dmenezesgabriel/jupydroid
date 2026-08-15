package com.jupydroid.app.ui.editor

import androidx.compose.ui.text.TextRange
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.PreservedJson
import kotlinx.serialization.json.JsonObject

/**
 * Cutting one cell into several - `notebook:split-cell-at-cursor`, and merge's inverse.
 *
 * Its own file rather than a sixth function in [CellRestructure]: it is the only restructuring
 * that reads the editor rather than the document, and it brings four rules and four helpers with
 * it. detekt's `TooManyFunctions` asked, and asking is how [CellUndo] and `NotebookCellEdits`
 * were found too.
 *
 * Same rules as its neighbours: pure, new ids passed in, a stale id is a no-op.
 */

/**
 * Cuts [cellId] into fragments at the caret [at].
 *
 * Four rules from `splitCell` (`actions.tsx:187-320`), and two of them are not the obvious answer:
 *
 * - **The original cell becomes the *last* fragment**, not the first: *"The last piece will remain
 *   in the original cell to preserve kernel connection."* The new cells go in at its index.
 * - **Every new cell is created with `outputs: undefined`**, so the results and the execution
 *   count stay on that last fragment. Here that costs nothing: the original keeps its id and the
 *   outputs are keyed by id.
 * - **Each fragment loses its own leading and trailing newlines** and nothing else
 *   (`/^\n+/`, `/\n+$/`) - stripping whitespace would take the indentation of a fragment cut from
 *   inside a block.
 * - **A cell mid-run does not hand its `execution` metadata to the fragments**, which never ran.
 *
 * It cuts the cell's **own** source, and [CellEdits.applying] is what makes that safe: it brings
 * the cell up to the editor's text first, because the two are allowed to disagree by up to one
 * debounce and cutting a stale copy at a fresh caret would cut in the wrong place.
 *
 * One adaptation, and it is a narrowing: the reference splits at *every* selection, while a
 * Compose `TextFieldState` carries one - so this is two fragments from a caret and three from a
 * range, never more.
 */
fun EditorUiState.withCellSplit(cellId: String, at: TextRange, newIds: List<String>): EditorUiState {
  val index = cells.indexOfFirst { it.id == cellId }
  if (index < 0) return this
  val original = cells[index]
  val pieces = original.source.splitAtOffsets(at)
  val fragments = original.fragmentedInto(pieces.dropLast(1), newIds)
  return copy(
    cells = cells.toMutableList().apply {
      set(index, original.copy(source = pieces.last()))
      addAll(index, fragments)
    },
    selectedCellId = selectedCellId.movedToTheSelectedFragment(at, fragments, original),
    dirty = true,
  )
}

/**
 * The cells the leading [pieces] become: this cell's kind and metadata, and none of its results.
 *
 * *"outputs: undefined"* on each, which is the reference's way of keeping the run with the last
 * fragment - here they simply never leave the original, since it keeps the id they are keyed by.
 */
private fun Cell.fragmentedInto(pieces: List<String>, newIds: List<String>): List<Cell> =
  pieces.mapIndexed { nth, piece ->
    copy(
      id = newIds.getOrElse(nth) { newCellId() },
      source = piece,
      outputs = emptyList(),
      executionCount = null,
      preserved = preserved.withoutUnfinishedExecution(),
    )
  }

/**
 * The pieces [TextRange] cuts [this] into: two around a caret, three around a range.
 *
 * `offsets` is the reference's own construction - `[0]`, then the selection's ends low first,
 * then the length - and the pieces are the gaps between consecutive offsets. An empty source
 * collapses it to `[0, 0, 0]`, which is why *"If there is no content, two empty cells will be
 * created"* falls out rather than being special-cased.
 */
private fun String.splitAtOffsets(at: TextRange): List<String> {
  val ends = if (at.collapsed) listOf(at.start) else listOf(at.min, at.max)
  val offsets = (listOf(0) + ends + listOf(length)).map { it.coerceIn(0, length) }
  return offsets.zipWithNext { from, to -> substring(from, to).trim('\n') }
}

/**
 * *"If text was selected, the cell containing the selection will be activated"* - which is the
 * fragment the selection became. A bare caret leaves the selection alone: the original cell is
 * where it was, and it is the cell the split was invoked from.
 */
private fun String?.movedToTheSelectedFragment(at: TextRange, fragments: List<Cell>, original: Cell): String? {
  if (at.collapsed || this != original.id) return this
  return fragments.lastOrNull()?.id ?: this
}

/**
 * Drops `metadata.execution` when it records a run that started and has not replied, which is the
 * reference's own test for a cell being mid-run: `execution['iopub.execute_input']` present and
 * `execution['shell.execute_reply']` absent. A finished record is copied, because it is complete.
 */
private fun PreservedJson.withoutUnfinishedExecution(): PreservedJson {
  val running = (this["metadata"] as? JsonObject)?.takeIf { it.recordsARunInFlight() } ?: return this
  return JsonObject(this + ("metadata" to JsonObject(running - "execution")))
}

/** `execution['iopub.execute_input']` present and `shell.execute_reply` absent - the cell is running. */
private fun JsonObject.recordsARunInFlight(): Boolean {
  val execution = this["execution"] as? JsonObject ?: return false
  return "iopub.execute_input" in execution && "shell.execute_reply" !in execution
}
