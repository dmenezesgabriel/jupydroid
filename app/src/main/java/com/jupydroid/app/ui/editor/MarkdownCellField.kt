package com.jupydroid.app.ui.editor

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle

/** What an empty markdown cell says, so it is never a row with nothing in it. */
internal const val MARKDOWN_PLACEHOLDER = "Write markdown…"

/**
 * One markdown cell's editor.
 *
 * Prose, not code: no line numbers, no bracket matching, no Python auto-indent and no kernel
 * completions - every one of those is meaningless here, which is why this is its own field
 * rather than [CodeCellField] with the language parts switched off.
 *
 * Rendering the markdown is a separate concern and still open; this shows and edits its source.
 *
 * ```
 * MarkdownCellField(source = cell.source, onSourceEdited = { … })
 * ```
 */
@Composable
fun MarkdownCellField(source: String, onSourceEdited: (String) -> Unit, modifier: Modifier = Modifier) {
  val state = rememberTextFieldState(initialText = source)
  val empty by remember { derivedStateOf { state.text.isEmpty() } }

  ReportEdits(state, onSourceEdited)

  BasicTextField(
    state = state,
    modifier = modifier,
    textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyMedium),
    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    decorator = { field ->
      // Dimmed and italic, the way JupyterLab marks an empty markdown cell
      // (packages/cells/style/widget.css, jp-mod-emptyMarkdownPlaceholder).
      if (empty) {
        Text(
          text = MARKDOWN_PLACEHOLDER,
          style = MaterialTheme.typography.bodyMedium,
          fontStyle = FontStyle.Italic,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      field()
    },
  )
}
