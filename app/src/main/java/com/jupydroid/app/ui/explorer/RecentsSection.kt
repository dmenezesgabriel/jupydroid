package com.jupydroid.app.ui.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.theme.Spacing

/**
 * What was opened, above the directory being browsed.
 *
 * **Its own file because it is its own thing**, which is how the reference is arranged too:
 * recents belong to `docmanager` and are surfaced from `mainmenu-extension` and
 * `running-extension`, never from `filebrowser`. Putting the list inside the browser is X2h's
 * adaptation - a phone has no menu bar to hang `File > Open Recent` on, and no second sidebar -
 * and keeping it in one file is what stops that adaptation bleeding through the listing.
 *
 * Items of the surrounding `LazyColumn` rather than a scrollable of its own: nesting a second
 * scrollable in the same direction is what Compose forbids outright, and the guidance is to
 * consolidate through the `item`/`items` DSL.
 */
internal fun LazyListScope.recentsSection(
  recents: List<RecentDocument>,
  onOpen: (RecentDocument) -> Unit,
  onClear: () -> Unit,
) {
  if (recents.isEmpty()) return
  item(key = RECENTS_HEADING_KEY, contentType = RECENTS_HEADING_KEY) {
    RecentsHeading(onClear = onClear)
  }
  // Keys are namespaced: at the root a recent and a listing row can hold the same path, and two
  // items sharing a key inside one LazyColumn is a crash rather than a cosmetic clash.
  items(recents, key = { "recent:${it.path}" }, contentType = { RECENTS_ROW_TYPE }) { document ->
    RecentRow(document = document, onOpen = { onOpen(document) })
  }
  item(key = RECENTS_DIVIDER_KEY, contentType = RECENTS_DIVIDER_KEY) { HorizontalDivider() }
}

/**
 * How many items [recentsSection] put above the listing, so a scroll to a listing row lands on it.
 *
 * The heading and the divider are the two that are not rows; an off-by-one here scrolls to a
 * neighbour, which looks exactly like the defect the scroll exists to fix (audit A3).
 */
internal fun itemsAboveTheListing(recents: Int): Int = if (recents == 0) 0 else recents + HEADING_AND_DIVIDER

/**
 * What the section is, and the one way to empty it.
 *
 * "Recent" rather than "Recently opened": the reference's submenu is `Open Recent` and its panel
 * section is `Recently Closed`, both of which read as verbs on a phone where the heading has to sit
 * in a row of its own. Clearing is `docmanager:clear-recents`, which the reference puts at the foot
 * of the same submenu; here it is an action on the heading, because a phone has no submenu to put a
 * last item in.
 */
@Composable
private fun RecentsHeading(onClear: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "Recent",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    IconButton(onClick = onClear) {
      Icon(
        Icons.Filled.Clear,
        contentDescription = "Clear recents",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * One recent: the path it was opened under, and an icon saying whether it is a folder.
 *
 * **The whole path, not the basename.** A recents list is flat and global, so two `summary.ipynb`
 * in different folders would be one row repeated; the reference labels its own recents by path too
 * (`joinWithLeadingSlash(recent.root, recent.path)`, `mainmenu-extension/src/recents.ts`). The
 * *start* is ellipsised rather than the end, because the end is the part that names the document.
 *
 * **No long press, deliberately.** Renaming and deleting belong to the file, which the listing row
 * below offers; the reference's own per-item action on a recent is `Forget`, and never either of
 * those. No timestamp either: neither reference surface carries one, and when a thing was last
 * written belongs to the listing, which is where `DirListing` puts it.
 */
@Composable
private fun RecentRow(document: RecentDocument, onOpen: () -> Unit) {
  val directory = document.kind == WorkspaceEntryKind.DIRECTORY
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onOpen)
      .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = if (directory) Icons.Filled.Folder else Icons.Filled.History,
      contentDescription = if (directory) "Recent folder" else "Recent file",
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = document.path,
      style = MaterialTheme.typography.bodyLarge,
      maxLines = 1,
      overflow = TextOverflow.StartEllipsis,
      modifier = Modifier.padding(start = Spacing.lg),
    )
  }
}

/**
 * Keys for the section's non-row items, and a type for its rows.
 *
 * Namespaced on purpose: at the root a recent and a listing row can hold the same path.
 */
private const val RECENTS_HEADING_KEY = "recents-heading"
private const val RECENTS_DIVIDER_KEY = "recents-divider"
private const val RECENTS_ROW_TYPE = "recent"

/** The heading above the recent rows and the divider below them, both items in the same list. */
private const val HEADING_AND_DIVIDER = 2
