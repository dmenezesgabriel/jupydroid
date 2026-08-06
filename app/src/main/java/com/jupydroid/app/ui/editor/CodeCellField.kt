package com.jupydroid.app.ui.editor

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
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
 * selection handles and the magnifier are the platform's problem rather than ours. Two things
 * are added, and both are the language's rather than the widget's: colour and bracket matching
 * through [OutputTransformation], which style what is drawn without touching what is stored,
 * and the indent/bracket edits through [InputTransformation].
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
  highlighter: SyntaxHighlighter = remember { PythonSyntax() },
  typing: InputTransformation = remember { PythonTypingRules() },
) {
  val state = rememberTextFieldState(initialText = source)
  val colors = syntaxColors()
  val matched = bracketMatchStyle()
  val interactions = remember { MutableInteractionSource() }
  val focused = interactions.collectIsFocusedAsState()

  ReportEdits(state, onSourceEdited)

  BasicTextField(
    state = state,
    modifier = modifier,
    textStyle = LocalTextStyle.current.copy(
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
    interactionSource = interactions,
    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    inputTransformation = typing,
    outputTransformation = remember(highlighter, colors, matched) {
      HighlightTransformation(highlighter, colors, matched, focused)
    },
  )
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
