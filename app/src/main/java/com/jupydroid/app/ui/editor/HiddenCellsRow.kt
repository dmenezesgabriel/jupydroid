package com.jupydroid.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.theme.Spacing

/**
 * What a collapsed heading shows in place of the section it is hiding: how many cells went, and a
 * tap that brings them back.
 *
 * The reference's own affordance, not an invention. `maybeCreateOrUpdateExpandButton` appends a
 * button carrying `addIcon` and `trans._n('%1 cell hidden', '%1 cells hidden', numberChildNodes)`,
 * whose `onclick` is `this.headingCollapsed = false`
 * (`packages/cells/src/widget.ts:2412-2455`) - and it exists only while the heading is collapsed
 * **and** owns something, which is why the count is never zero here.
 *
 * Its own file rather than a branch inside [CollapsedCell], because the two say different things: a
 * collapsed cell shows a line of what is folded so it stays identifiable, while a section has no
 * one line to show and is named by its size instead.
 */
@Composable
internal fun HiddenCellsRow(count: Int, onExpand: () -> Unit, modifier: Modifier = Modifier) {
  val label = hiddenCellsLabel(count)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClickLabel = "Expand") { onExpand() }
      // Inside the ripple, so it draws at the reserved height - see [CollapsedCell]'s KDoc.
      .minimumInteractiveComponentSize()
      .padding(vertical = Spacing.sm)
      .semantics { contentDescription = label },
  ) {
    Icon(
      imageVector = Icons.Filled.Add,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = Spacing.sm),
    )
  }
}

/** `trans._n('%1 cell hidden', '%1 cells hidden', …)` in this app's sentence case. */
internal fun hiddenCellsLabel(count: Int): String =
  if (count == 1) "1 cell hidden" else "$count cells hidden"
