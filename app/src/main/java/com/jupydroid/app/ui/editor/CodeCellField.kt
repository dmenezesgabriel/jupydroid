package com.jupydroid.app.ui.editor

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.syntax.PythonSyntax
import com.jupydroid.app.syntax.SyntaxHighlighter
import com.jupydroid.app.syntax.SyntaxKind
import com.jupydroid.app.syntax.bracketPairAround
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

/**
 * One cell's code editor.
 *
 * A plain Compose text field, so IME composing regions, autocorrect, gesture typing,
 * selection handles and the magnifier are the platform's problem rather than ours. What is
 * added sits on top of it and never inside it: colour and bracket matching through
 * [OutputTransformation], which style what is drawn without touching what is stored, the
 * indent/bracket edits through [InputTransformation], and the [lineNumbers] gutter, which reads
 * the field's layout rather than the field reading it.
 *
 * ```
 * CodeCellField(source = cell.source, onSourceEdited = { … })
 * ```
 */
@Composable
fun CodeCellField(
  source: String,
  onSourceEdited: (String) -> Unit,
  modifier: Modifier = Modifier,
  completionsAt: suspend (code: String, cursorOffset: Int) -> CompletionResult = NO_COMPLETIONS,
  highlighter: SyntaxHighlighter = remember { PythonSyntax() },
  typing: InputTransformation = remember { PythonTypingRules() },
) {
  val state = rememberTextFieldState(initialText = source)
  val colors = syntaxColors()
  val matched = bracketMatchStyle()
  val interactions = remember { MutableInteractionSource() }
  val focused = interactions.collectIsFocusedAsState()
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  val codeStyle = LocalTextStyle.current.copy(
    fontFamily = FontFamily.Monospace,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  val numberStyle = codeStyle.copy(color = MaterialTheme.colorScheme.outline)
  val gutter = gutterWidth(state.text, numberStyle)
  val completions by rememberCellCompletions(state, focused, completionsAt)
  // The code a popup was already answered for, so accepting or dismissing one does not
  // immediately re-offer against the very text it just produced.
  var settled by remember { mutableStateOf<String?>(null) }

  ReportEdits(state, onSourceEdited)

  Box {
    BasicTextField(
      state = state,
      // The gutter's width comes from the line *count*, so it never depends on the layout this
      // callback publishes - which is what keeps writing state from here out of a measure loop.
      modifier = modifier.lineNumbers(state.text, layout, numberStyle, gutter),
      onTextLayout = { getResult -> layout = getResult() },
      textStyle = codeStyle,
      interactionSource = interactions,
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      inputTransformation = typing,
      outputTransformation = remember(highlighter, colors, matched) {
        HighlightTransformation(highlighter, colors, matched, focused)
      },
    )
    val offered = completions?.takeIf { it.query.code != settled }
    if (offered != null && layout != null) {
      CompletionPopup(
        completions = offered,
        anchor = cursorAnchor(layout, offered.query.cursorOffset, gutter),
        onPick = { match ->
          applyCompletion(state, offered, match)
          settled = state.text.toString()
        },
        onDismiss = { settled = offered.query.code },
      )
    }
  }
}

/**
 * Where the popup sits: under the cursor, offset by the gutter the code is pushed past.
 *
 * [TextLayoutResult.getCursorRect] answers in the text's own coordinates, which start at the
 * gutter's right edge rather than at the cell's left.
 */
@Composable
private fun cursorAnchor(layout: TextLayoutResult?, cursorOffset: Int, gutter: Dp): IntOffset {
  val gutterPx = with(LocalDensity.current) { gutter.roundToPx() }
  val rect = layout?.getCursorRect(cursorOffset.coerceIn(0, layout.layoutInput.text.length))
    ?: return IntOffset(gutterPx, 0)
  return IntOffset(gutterPx + rect.left.toInt(), rect.bottom.toInt())
}

/**
 * Debounced because a highlighter runs on every keystroke anyway; the ViewModel does not also
 * need to hear about each one. `drop(1)` skips the initial value, which would otherwise mark
 * an untouched notebook dirty the moment it opened.
 */
@Composable
private fun ReportEdits(state: TextFieldState, onSourceEdited: (String) -> Unit) {
  LaunchedEffect(state) {
    snapshotFlow { state.text.toString() }
      .drop(1)
      .debounce(EDIT_REPORT_MILLIS)
      .collect(onSourceEdited)
  }
}

/**
 * Applies the highlighter's spans as styles on the rendered text, then marks the bracket pair
 * the cursor is touching.
 *
 * Nothing is inserted or removed, so offsets need no mapping - a span from the lexer is
 * already an offset into exactly this text.
 *
 * Re-runs when the cursor moves, and when the field gains or loses focus, without any plumbing
 * to make it: foundation holds the transformed text in a `derivedStateOf` over
 * `TextFieldState.value`, whose equality covers the selection, and any state read *inside*
 * `transformOutput` joins that same dependency set - which is what [focused] relies on.
 */
private class HighlightTransformation(
  private val highlighter: SyntaxHighlighter,
  private val colors: Map<SyntaxKind, Color>,
  private val matched: SpanStyle,
  private val focused: State<Boolean>,
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
    if (!focused.value || !selection.collapsed) return
    val pair = bracketPairAround(source, selection.start, spans) ?: return
    addStyle(matched, pair.open, pair.open + 1)
    addStyle(matched, pair.close, pair.close + 1)
  }
}

private const val EDIT_REPORT_MILLIS = 150L

/** A cell with no kernel behind it still edits; it just has nothing to suggest. */
private val NO_COMPLETIONS: suspend (String, Int) -> CompletionResult = { _, _ -> CompletionResult.NONE }
