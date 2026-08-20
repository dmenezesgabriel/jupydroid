package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * What one cell's editor draws for the find bar: the matches in **its** source, and which of them
 * the bar is standing on.
 *
 * Per cell rather than the notebook's whole hit list, because that is what a field can use - it
 * knows its own text and nothing about the cells above it. [EditorContent] slices the list once
 * for the notebook, the way it already slices the heading numbers.
 *
 * `@Immutable` so a cell holding one is skippable during recomposition, like [EditorPalette].
 */
@Immutable
data class CellSearchHighlight(
  val matches: List<IntRange> = emptyList(),
  /** The one match the arrows are on, when it is in this cell. Also in [matches]. */
  val current: IntRange? = null,
) {
  companion object {
    /** No search running, or none of it in this cell. */
    val NONE = CellSearchHighlight()
  }
}

/**
 * The same highlight with every range that the field's text cannot hold taken out.
 *
 * **Not defensive tidying - a real window.** Hits are found in the *model's* source and drawn over
 * the *field's* text, and the two differ for up to one [reportCellEdits] debounce after every
 * keystroke, so a range can name characters that were just deleted. `TextFieldBuffer.addStyle`
 * past the end throws, which would take the notebook down mid-type.
 *
 * Dropped rather than clamped: half a match highlighted is a wrong answer drawn confidently, where
 * a missing one is corrected 150 ms later by the query re-running against the new source.
 */
internal fun CellSearchHighlight.within(length: Int): CellSearchHighlight = CellSearchHighlight(
  matches = matches.filter { it.fitsIn(length) },
  current = current?.takeIf { it.fitsIn(length) },
)

private fun IntRange.fitsIn(length: Int): Boolean = first >= 0 && first <= last && last < length

/**
 * How a match is drawn, found and current - see [com.jupydroid.app.theme.EditorPalette.searchMatch].
 *
 * A pair rather than two parameters, because they are only ever wanted together and
 * [HighlightTransformation] already carries four.
 */
@Immutable
data class SearchMatchStyles(val match: SpanStyle, val current: SpanStyle)

/**
 * This text with the find bar's matches marked - the drawn-output half of what
 * [HighlightTransformation] does for a field.
 *
 * An `AnnotatedString` rather than an `OutputTransformation`, because an output is drawn and never
 * typed into: there is no `TextFieldBuffer`, no caret and nothing to keep in step with one. The
 * spans and their order are the same, current last so it wins where the two overlap.
 *
 * Returns the plain string when there is nothing to mark, so a notebook with no search open builds
 * no annotations at all.
 */
internal fun String.withSearchMatches(search: CellSearchHighlight, styles: SearchMatchStyles): AnnotatedString {
  val marks = search.within(length)
  if (marks.matches.isEmpty()) return AnnotatedString(this)
  return buildAnnotatedString {
    append(this@withSearchMatches)
    marks.matches.forEach { addStyle(styles.match, it.first, it.last + 1) }
    marks.current?.let { addStyle(styles.current, it.first, it.last + 1) }
  }
}

/**
 * The matches in one cell, and whether the current one is among them - what a field draws.
 *
 * Computed per cell rather than sliced once for the list, unlike the heading numbers beside it:
 * a heading's number depends on every heading before it, where a cell's matches depend only on
 * that cell, so there is nothing to thread and nothing quadratic to avoid.
 */
fun EditorUiState.searchHighlightOf(cellId: String, outputAt: Int? = null): CellSearchHighlight {
  val searching = search ?: return CellSearchHighlight.NONE
  val mine = searching.hits.filter { it.cellId == cellId && it.outputAt == outputAt }
  return if (mine.isEmpty()) {
    // Its own answer rather than an empty list, so a cell with no matches compares equal to every
    // other cell with none and stays skippable through recomposition.
    CellSearchHighlight.NONE
  } else {
    CellSearchHighlight(
      matches = mine.map { it.range },
      current = searching.currentHit()
        ?.takeIf { it.cellId == cellId && it.outputAt == outputAt }
        ?.range,
    )
  }
}

/** The hit the arrows are on, or null when they are on none. */
private fun SearchState.currentHit(): SearchHit? = current?.let { hits.getOrNull(it) }

/** Which cell the arrows are standing in, for the screen to scroll to. */
val SearchState.currentHitCellId: String? get() = currentHit()?.cellId

/**
 * What the find bar found in each of a cell's rendered outputs, in their order.
 *
 * Empty while nothing is searching outputs, which is the ordinary case - so a cell whose outputs
 * hold no matches hands the same empty list every other one does and stays skippable.
 */
fun EditorUiState.outputSearchHighlightsOf(cellId: String): List<CellSearchHighlight> =
  if (search?.outputsSearched != true) {
    emptyList()
  } else {
    renderedOutputsOf(cellId).indices.map { searchHighlightOf(cellId, outputAt = it) }
  }
