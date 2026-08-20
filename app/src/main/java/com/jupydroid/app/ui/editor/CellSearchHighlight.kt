package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.SpanStyle

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
internal fun CellSearchHighlight.within(length: Int): CellSearchHighlight =
  CellSearchHighlight(
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
