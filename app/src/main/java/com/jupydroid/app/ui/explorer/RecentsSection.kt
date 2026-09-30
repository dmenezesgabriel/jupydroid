package com.jupydroid.app.ui.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.theme.Spacing
import com.jupydroid.app.ui.Twistie
import com.jupydroid.app.ui.shell.MINIMUM_TAP_TARGET

/**
 * What was opened, above the directory being browsed.
 *
 * **Its own file because it is its own thing**, which is how the reference is arranged too:
 * recents belong to `docmanager` and are surfaced from `mainmenu-extension` and
 * `running-extension`, never from `filebrowser`. Putting the list inside the browser is this app's
 * adaptation - a phone has no menu bar to hang `File > Open Recent` on, and no second sidebar -
 * and keeping it in one file is what stops that adaptation bleeding through the listing.
 *
 * Items of the surrounding `LazyColumn` rather than a scrollable of its own: nesting a second
 * scrollable in the same direction is what Compose forbids outright, and the guidance is to
 * consolidate through the `item`/`items` DSL.
 */
internal fun LazyListScope.recentsSection(
  recents: List<RecentDocument>,
  collapsed: Boolean,
  onToggle: () -> Unit,
  onOpen: (RecentDocument) -> Unit,
  onClear: () -> Unit,
) {
  if (recents.isEmpty()) return
  item(key = RECENTS_HEADING_KEY, contentType = RECENTS_HEADING_KEY) {
    RecentsHeading(collapsed = collapsed, onToggle = onToggle, onClear = onClear)
  }
  if (!collapsed) {
    // Keys are namespaced: at the root a recent and a listing row can hold the same path, and two
    // items sharing a key inside one LazyColumn is a crash rather than a cosmetic clash.
    items(recents, key = { "recent:${it.path}" }, contentType = { RECENTS_ROW_TYPE }) { document ->
      RecentRow(document = document, onOpen = { onOpen(document) })
    }
  }
  // The divider is drawn either way: shut, the heading still has to be parted from the listing
  // under it - and [itemsAboveTheListing] counts it in both states for the same reason.
  item(key = RECENTS_DIVIDER_KEY, contentType = RECENTS_DIVIDER_KEY) { HorizontalDivider() }
}

/**
 * How many items [recentsSection] put above the listing, so a scroll to a listing row lands on it.
 *
 * The heading and the divider are the two that are not rows; an off-by-one here scrolls to a
 * neighbour, which looks exactly like the defect the scroll exists to fix (audit A3). A
 * [collapsed] section emits no rows at all, so it counts as those two and nothing else.
 */
internal fun itemsAboveTheListing(recents: Int, collapsed: Boolean): Int = when {
  recents == 0 -> 0
  collapsed -> HEADING_AND_DIVIDER
  else -> recents + HEADING_AND_DIVIDER
}

/**
 * What the section is, whether it is shut, and the way to its actions.
 *
 * "Recent" rather than "Recently opened": the reference's submenu is `Open Recent` and its panel
 * section is `Recently Closed`, both of which read as verbs on a phone where the heading has to sit
 * in a row of its own.
 *
 * **The whole row collapses it, not the chevron alone** - the reference binds both a click and a
 * *tap* on the header to `setExpanded(!isExpanded())` (`paneview.ts:285-292`), and does it there
 * rather than on the twistie so a thumb has the row's width to aim at. A tap that lands on the
 * overflow is consumed by it and never reaches here, which is the same split the reference draws
 * with `if (!e.defaultPrevented)` (`:287`).
 *
 * The row states `role = Button` and what it is doing, which is what the reference's own
 * `role="button"` plus aria-expanded says (`paneview.ts:261-263`); [expand]/[collapse] are the
 * actions TalkBack offers off the back of it.
 */
@Composable
private fun RecentsHeading(collapsed: Boolean, onToggle: () -> Unit, onClear: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      // Material's minimum, on the node that carries the tap - see [MINIMUM_TAP_TARGET]. A shut
      // section has no `IconButton` on this row to hold it open, so the row states its own height.
      .heightIn(min = MINIMUM_TAP_TARGET)
      // One node for the whole row, so a screen reader says "Recent, expanded, button" rather than
      // walking a chevron and a label - and so a flow has one string to address the heading by.
      .semantics(mergeDescendants = true) {
        role = Role.Button
        stateDescription = if (collapsed) COLLAPSED else EXPANDED
        if (collapsed) {
          expand {
            onToggle()
            true
          }
        } else {
          collapse {
            onToggle()
            true
          }
        }
      }
      .clickable(onClick = onToggle)
      // 8dp of lead, which is what the reference gives a header whose twistie is not drawn
      // (`paneviewlet.css:22-24`); here the twistie itself sits in it.
      .padding(start = Spacing.sm),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Twistie(collapsed = collapsed)
    Text(
      text = "Recent",
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f).padding(start = Spacing.sm),
    )
    if (!collapsed) RecentsActionsMenu(onClear = onClear)
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

/**
 * What the heading says it is doing, which is the reference's aria-expanded read aloud.
 *
 * Not a `contentDescription`: a state description is announced *after* the label and again on
 * every change, which is what a collapse is.
 */
private const val EXPANDED = "Expanded"
private const val COLLAPSED = "Collapsed"
