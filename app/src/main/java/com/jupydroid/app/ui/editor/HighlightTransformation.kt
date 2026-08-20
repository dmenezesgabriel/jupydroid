package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.jupydroid.app.syntax.SyntaxHighlighter
import com.jupydroid.app.syntax.SyntaxKind
import com.jupydroid.app.syntax.bracketPairAround

/**
 * Applies the highlighter's spans as styles on the rendered text, marks the bracket pair the
 * cursor is touching when [matched] says to, and lights whatever the find bar found.
 *
 * Nothing is inserted or removed, so offsets need no mapping - a span from the lexer is
 * already an offset into exactly this text.
 *
 * Re-runs when the cursor moves, when the field gains or loses focus, and when the search moves,
 * without any plumbing to make it: foundation holds the transformed text in a `derivedStateOf`
 * over `TextFieldState.value`, whose equality covers the selection, and any state read *inside*
 * `transformOutput` joins that same dependency set - which is what [focused] relies on, and why
 * [search] is a `State` rather than a value the instance would have to be rebuilt for.
 */
internal class HighlightTransformation(
  private val highlighter: SyntaxHighlighter,
  /**
   * A style rather than a colour per kind, because two of them carry no colour: emphasis is
   * weight and slant, the way IntelliJ declares `MARKDOWN_BOLD` and `MARKDOWN_ITALIC`.
   */
  private val styles: Map<SyntaxKind, SpanStyle>,
  private val focused: State<Boolean>,
  /** Null where matching brackets means nothing - prose has parentheses, not structure. */
  private val matched: SpanStyle? = null,
  /** What the find bar found in *this* cell, or [CellSearchHighlight.NONE] while none is open. */
  private val search: State<CellSearchHighlight> = mutableStateOf(CellSearchHighlight.NONE),
  private val searchStyles: SearchMatchStyles = NO_SEARCH_STYLES,
) : OutputTransformation {
  override fun TextFieldBuffer.transformOutput() {
    val source = asCharSequence().toString()
    val spans = highlighter.spansOf(source)
    spans.forEach { span ->
      styles[span.kind]?.let { addStyle(it, span.start, span.end) }
    }
    // An unfocused cell still carries a cursor - at the end of its text, on the very first
    // composition - so without this every notebook would open with brackets lit up in cells
    // nobody had touched.
    val wanted = matched != null && focused.value && selection.collapsed
    val pair = if (wanted) bracketPairAround(source, selection.start, spans) else null
    if (pair != null && matched != null) {
      addStyle(matched, pair.open, pair.open + 1)
      addStyle(matched, pair.close, pair.close + 1)
    }
    addSearchSpans()
  }

  /**
   * Last, so a match's own colours win over the syntax and bracket spans under it - overlapping
   * spans merge with the later one taking precedence, which is the same order JupyterLab reaches
   * with `!important` on `.cm-searchMatch` (`packages/codemirror/src/theme.ts:106-115`).
   *
   * The current match is applied after the others for the same reason: it is one of them.
   */
  private fun TextFieldBuffer.addSearchSpans() {
    val highlight = search.value.within(length)
    highlight.matches.forEach { addStyle(searchStyles.match, it.first, it.last + 1) }
    highlight.current?.let { addStyle(searchStyles.current, it.first, it.last + 1) }
  }
}

/** No search open, so the styles are never read - the default a field driven alone gets. */
private val NO_SEARCH_STYLES = SearchMatchStyles(SpanStyle(), SpanStyle())
