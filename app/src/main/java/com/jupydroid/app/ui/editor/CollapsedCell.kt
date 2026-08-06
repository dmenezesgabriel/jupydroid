package com.jupydroid.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Named so a flow can tell a folded cell from the field or the outputs it replaced. */
internal const val COLLAPSED_SOURCE = "Collapsed code"
internal const val COLLAPSED_OUTPUTS = "Collapsed outputs"

/**
 * What a collapsed source or a collapsed set of outputs shows in place of itself: **one line of
 * what is folded**, then an ellipsis, and a tap anywhere on it expands again.
 *
 * All three are JupyterLab's, not invented - its placeholders carry a line of the hidden
 * content, an ellipsis icon beside it and `title: "Click to expand"`
 * (`packages/cells/src/widget.ts:626-632`, `packages/cells/src/placeholder.ts:72-105`). The
 * line is what makes a folded cell identifiable at all; a bare "collapsed" row would turn a
 * folded notebook into a list of identical stubs.
 */
@Composable
fun CollapsedCell(preview: String, description: String, onExpand: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClickLabel = "Expand") { onExpand() }
      .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
      .padding(horizontal = 8.dp, vertical = 6.dp)
      .semantics { contentDescription = description },
  ) {
    Text(
      text = preview,
      style = MaterialTheme.typography.bodySmall,
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false),
    )
    Text(
      text = "…",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 8.dp),
    )
  }
}

/**
 * The line that names what is folded.
 *
 * JupyterLab's two placeholders disagree here - the input one takes `source.split('\n')[0]`
 * and the output one takes the first *non-empty* line (`widget.ts:630`, `:1283-1289`). The
 * non-empty rule is used for both, because taking line zero literally shows an empty
 * placeholder for a cell that opens with a blank line, which is the one case a preview is for.
 */
internal fun firstNonEmptyLine(text: String): String =
  text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

/**
 * A line of the first output that has one. An image or a rendered document has no string form,
 * and neither does JupyterLab's placeholder for it - `getOutputPlaceholderText` returns
 * undefined and the row is just the ellipsis.
 */
internal fun List<RenderedOutput>.collapsedPreview(): String =
  firstNotNullOfOrNull { (it as? RenderedOutput.Text)?.text?.takeIf { text -> text.isNotBlank() } }
    ?.let(::firstNonEmptyLine)
    .orEmpty()
