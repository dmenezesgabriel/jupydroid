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

/** Named so a flow can tell a collapsed cell from the field it replaced. */
internal const val COLLAPSED_SOURCE = "Collapsed code"

/**
 * What a collapsed cell shows instead of its source: **its first line**, then an ellipsis, and
 * a tap anywhere on it expands again.
 *
 * All three are JupyterLab's, not invented - its `InputPlaceholder` is built with
 * `text: source.split('\n')[0]`, an ellipsis icon beside it and `title: "Click to expand"`
 * (`packages/cells/src/widget.ts:626-632`, `packages/cells/src/placeholder.ts:72-105`).
 * The first line is what makes a collapsed cell identifiable at all; a bare "collapsed" row
 * would turn a folded notebook into a list of identical stubs.
 */
@Composable
fun CollapsedCell(source: String, onExpand: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClickLabel = "Expand code") { onExpand() }
      .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
      .padding(horizontal = 8.dp, vertical = 6.dp)
      .semantics { contentDescription = COLLAPSED_SOURCE },
  ) {
    Text(
      text = source.lineSequence().firstOrNull().orEmpty(),
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
