package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.jupydroid.app.syntax.SyntaxHighlighter
import com.jupydroid.app.syntax.SyntaxKind
import com.jupydroid.app.syntax.bracketPairAround

/**
 * Applies the highlighter's spans as styles on the rendered text, and marks the bracket pair the
 * cursor is touching when [matched] says to.
 *
 * Nothing is inserted or removed, so offsets need no mapping - a span from the lexer is
 * already an offset into exactly this text.
 *
 * Re-runs when the cursor moves, and when the field gains or loses focus, without any plumbing
 * to make it: foundation holds the transformed text in a `derivedStateOf` over
 * `TextFieldState.value`, whose equality covers the selection, and any state read *inside*
 * `transformOutput` joins that same dependency set - which is what [focused] relies on.
 */
internal class HighlightTransformation(
  private val highlighter: SyntaxHighlighter,
  private val colors: Map<SyntaxKind, Color>,
  private val focused: State<Boolean>,
  /** Null where matching brackets means nothing - prose has parentheses, not structure. */
  private val matched: SpanStyle? = null,
) : OutputTransformation {
  override fun TextFieldBuffer.transformOutput() {
    val source = asCharSequence().toString()
    val spans = highlighter.spansOf(source)
    spans.forEach { span ->
      colors[span.kind]?.let { addStyle(SpanStyle(color = it), span.start, span.end) }
    }
    // An unfocused cell still carries a cursor - at the end of its text, on the very first
    // composition - so without this every notebook would open with brackets lit up in cells
    // nobody had touched.
    val wanted = matched != null && focused.value && selection.collapsed
    val pair = if (wanted) bracketPairAround(source, selection.start, spans) else null
    if (pair == null || matched == null) return
    addStyle(matched, pair.open, pair.open + 1)
    addStyle(matched, pair.close, pair.close + 1)
  }
}
