package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.jupydroid.app.syntax.MarkdownSyntax
import com.jupydroid.app.syntax.SyntaxHighlighter

/** What an empty markdown cell says, so it is never a row with nothing in it. */
internal const val MARKDOWN_PLACEHOLDER = "Write markdown…"

/**
 * One markdown cell's editor.
 *
 * Prose, not code: no bracket matching, no Python auto-indent and no kernel completions - every
 * one of those is meaningless here, which is why this is its own field rather than
 * [CodeCellField] with the language parts switched off.
 *
 * It does get a line-number gutter, because [lineNumbers] counts lines rather than code, and a
 * line number is as useful for finding your place in prose as in Python.
 *
 * ```
 * MarkdownCellField(source = cell.source, onSourceEdited = { … })
 * ```
 */
@Composable
fun MarkdownCellField(
  source: String,
  onSourceEdited: (String) -> Unit,
  modifier: Modifier = Modifier,
  highlighter: SyntaxHighlighter = remember { MarkdownSyntax() },
) {
  val state = rememberTextFieldState(initialText = source)
  val colors = syntaxColors()
  val interactions = remember { MutableInteractionSource() }
  val focused = interactions.collectIsFocusedAsState()
  val empty by remember { derivedStateOf { state.text.isEmpty() } }
  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
  val numberStyle = markdownSourceStyle().copy(color = MaterialTheme.colorScheme.outline)
  val gutter = gutterWidth(state.text, numberStyle)

  ReportEdits(state, onSourceEdited)

  BasicTextField(
    state = state,
    // The panel is the editor's, not the cell's: it is what says "you are typing in this", the
    // same way a code cell reads. Rendered prose sitting in a box would be wrong, so this is
    // here rather than around MarkdownCell.
    modifier = modifier
      .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
      .padding(8.dp)
      .lineNumbers(state.text, layout, numberStyle, gutter),
    onTextLayout = { getResult -> layout = getResult() },
    textStyle = markdownSourceStyle(),
    interactionSource = interactions,
    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    // No bracket matching: a parenthesis in prose is punctuation, not structure.
    outputTransformation = remember(highlighter, colors) {
      HighlightTransformation(highlighter, colors, focused)
    },
    decorator = { field ->
      // Dimmed and italic, the way JupyterLab marks an empty markdown cell
      // (packages/cells/style/widget.css, jp-mod-emptyMarkdownPlaceholder).
      if (empty) {
        Text(
          text = MARKDOWN_PLACEHOLDER,
          modifier = Modifier.padding(start = gutter),
          style = MaterialTheme.typography.bodyMedium,
          fontStyle = FontStyle.Italic,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      field()
    },
  )
}
