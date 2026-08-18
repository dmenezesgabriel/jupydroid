package com.jupydroid.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import com.jupydroid.app.markdown.MarkdownDocument

/** Named so a flow can tell the rendered cell apart from the field that edits it. */
internal const val RENDERED_MARKDOWN = "Rendered markdown"

/**
 * A markdown cell, either rendered or being written.
 *
 * Rendered is the resting state, which is Jupyter's: a markdown cell shows its output, and you
 * go to the source to change it (`MarkdownCell._rendered` and `showEditor()` in
 * `packages/cells/src/widget.ts`). Tapping the rendered text opens the editor; running the cell
 * renders it again, which is what running a markdown cell means in Jupyter too.
 *
 * **Which of the two it is arrives as a parameter and is not remembered here**, and P2d-6 moved it
 * for a reason a screenshot found: a *range* run has to be able to close the editor
 * (`Private.runCells` renders every markdown cell it is handed), and a flag only this composable
 * could see left an open editor open through a Run All. It is [EditorUiState.markdownBeingEdited]
 * now - see [MarkdownEditing].
 *
 * ```
 * MarkdownCell(source, rendered = state.isMarkdownRendered(cell.id), onEditRequested = { … }, …)
 * ```
 */
@Composable
fun MarkdownCell(
  state: TextFieldState,
  source: String,
  rendered: Boolean,
  onEditRequested: () -> Unit,
  onSourceEdited: (String) -> Unit,
  modifier: Modifier = Modifier,
  input: FocusedCellInput? = null,
  /** Whether this cell's editor was asked for - see [EditorUiState.markdownTakingCaret]. */
  takesCaret: Boolean = false,
) {
  if (!rendered) {
    MarkdownCellField(
      state = state,
      onSourceEdited = onSourceEdited,
      modifier = modifier,
      input = input,
      takesCaret = takesCaret,
    )
    return
  }
  val blocks = remember(source) { MarkdownDocument.parse(source) }
  val openEditor = modifier
    .fillMaxWidth()
    .clickable(onClickLabel = "Edit markdown") { onEditRequested() }
    .semantics { contentDescription = RENDERED_MARKDOWN }

  // Empty markdown renders to nothing at all, which would leave a row with no way back into it.
  if (blocks.isEmpty()) {
    Text(
      text = MARKDOWN_PLACEHOLDER,
      style = MaterialTheme.typography.bodyMedium,
      fontStyle = FontStyle.Italic,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = openEditor,
    )
    return
  }
  RichText(blocks = blocks, modifier = openEditor)
}
