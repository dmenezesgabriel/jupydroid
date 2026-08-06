package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import com.jupydroid.app.syntax.PythonSyntax
import com.jupydroid.app.syntax.SyntaxHighlighter
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

/**
 * One cell's code editor.
 *
 * A plain Compose text field, so IME composing regions, autocorrect, gesture typing,
 * selection handles and the magnifier are the platform's problem rather than ours. Colour is
 * the only thing added, through [OutputTransformation] - which styles what is drawn without
 * touching what is stored.
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
) {
  val state = rememberTextFieldState(initialText = source)
  val colors = syntaxColors()

  ReportEdits(state, onSourceEdited)

  BasicTextField(
    state = state,
    modifier = modifier,
    textStyle = LocalTextStyle.current.copy(
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    ),
    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
    outputTransformation = remember(highlighter, colors) { HighlightTransformation(highlighter, colors) },
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
 * Applies the highlighter's spans as styles on the rendered text.
 *
 * Nothing is inserted or removed, so offsets need no mapping - a span from the lexer is
 * already an offset into exactly this text.
 */
private class HighlightTransformation(
  private val highlighter: SyntaxHighlighter,
  private val colors: Map<com.jupydroid.app.syntax.SyntaxKind, androidx.compose.ui.graphics.Color>,
) : OutputTransformation {
  override fun TextFieldBuffer.transformOutput() {
    val source = asCharSequence().toString()
    highlighter.spansOf(source).forEach { span ->
      colors[span.kind]?.let { addStyle(SpanStyle(color = it), span.start, span.end) }
    }
  }
}

private const val EDIT_REPORT_MILLIS = 150L
