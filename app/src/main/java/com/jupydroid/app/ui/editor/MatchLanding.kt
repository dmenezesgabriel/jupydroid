package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.model.rangeIn

/**
 * A match the **workspace** search found, landed on in the notebook it names.
 *
 * The seam between two features, which is why it is its own file rather than a corner of either -
 * the same reason [CellReveal] is one. The panel walks files and reports a [MatchedLine]; the
 * notebook knows cells and [SearchHit]s, and neither should have to know the other's shape.
 *
 * **The offsets are resolved against the notebook the editor loaded, not against the ones the walk
 * reported**, which is the reference's own order: `searchView.open` opens the notebook and then
 * calls `updateMatchesForEditorWidget()` to find the match again in the widget
 * (`searchView.ts:2283-2295`). A workspace is shared with the kernel here, so a cell can rewrite a
 * file between the walk and the tap.
 *
 * ```
 * state.landedOn(match)   // its section open, its source open, the head on it
 * ```
 */

/**
 * Where [match] sits in this notebook, or null when it no longer sits anywhere in it.
 *
 * **By index, because that is the only thing a walk and an editor agree on.** nbformat's cell id is
 * optional before 4.5 and `CellFile.toDomain` mints a `UUID` for a cell without one, so the id the
 * searcher read need not be the id this state holds - see [MatchedLine.cell].
 */
fun EditorUiState.hitFor(match: MatchedLine): SearchHit? =
  cellNamedBy(match)?.let { cell -> match.rangeIn(cell.source)?.let { SearchHit(cell.id, it) } }

/**
 * The cell [match]'s index names, or null when this notebook has no such cell - and null for a
 * match that names none at all, which is a `.py`'s and belongs to the file editor.
 *
 * Its own function because the two halves answer different questions - which cell, and where in
 * it - and detekt asked for the split by counting returns, the same seam it found in
 * `MatchedFile.startOf` and `asSearchRegex`.
 */
private fun EditorUiState.cellNamedBy(match: MatchedLine): Cell? = cells.getOrNull(match.cell ?: return null)

/**
 * This notebook with [match] revealed and the head moved onto it - **or itself**, unchanged, when
 * the notebook no longer holds it.
 *
 * [withMatchRevealed] and the selection together, which is the pair [withSearchStepped] applies for
 * the find bar's arrows and for the same reason: a match nobody can see is not a result, and the
 * cell the reader was sent to is the one the notebook should be standing on.
 *
 * The unchanged return is load-bearing rather than an optimisation - two of the three collapses a
 * reveal undoes are persisted, so a landing that missed must not mark the document dirty and
 * rewrite it for having been opened.
 */
fun EditorUiState.landedOn(match: MatchedLine): EditorUiState {
  val hit = hitFor(match) ?: return this
  // The anchor goes for `withSearchStepped`'s reason: a marked run that survived a jump across the
  // notebook would be a run nobody chose.
  return withMatchRevealed(hit).copy(selectedCellId = hit.cellId, anchorCellId = null)
}
