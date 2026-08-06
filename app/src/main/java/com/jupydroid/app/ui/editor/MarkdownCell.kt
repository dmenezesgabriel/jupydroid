package com.jupydroid.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
 * `packages/cells/src/widget.ts`). Tapping the rendered text opens the editor; the header's Run
 * button renders it again, which is what running a markdown cell means in Jupyter too.
 *
 * ```
 * MarkdownCell(source, rendered, onEditRequested = { rendered = false }, onSourceEdited = { … })
 * ```
 */
@Composable
fun MarkdownCell(
  source: String,
  rendered: Boolean,
  onEditRequested: () -> Unit,
  onSourceEdited: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!rendered) {
    MarkdownCellField(source = source, onSourceEdited = onSourceEdited, modifier = modifier)
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
  MarkdownText(blocks = blocks, modifier = openEditor)
}

/** Rendered or editing, remembered per cell so scrolling or rotating does not reset it. */
@Composable
fun rememberMarkdownRendered(cellId: String): MutableState<Boolean> =
  rememberSaveable(cellId) { mutableStateOf(true) }
