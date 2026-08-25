package com.jupydroid.app.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.SidePanel

/**
 * The activity bar: which side panel is showing, and the only control that changes it.
 *
 * **Beside the whole shell, not inside the side area**, which is where the reference puts it:
 * `hboxPanel` holds `leftHandler.sideBar` next to the split that contains *both* the panel and the
 * document area (`packages/application/src/shell.ts:468-477`). `docs/PLAN.md` said "at the left edge
 * of the list pane"; reading the shell corrected it, and the correction is what lets the area
 * collapse to the bar alone while the document keeps the width.
 *
 * One item per [SidePanel], in the reference's rank order, and `selected` says which panel is up -
 * to the eye and to a screen reader at once, which is what a `NavigationRailItem` already carries.
 *
 * ```
 * SidePanelRail(selected = panel, onChoose = { panel = panel.chosen(it) })
 * ```
 *
 * @param selected the panel on screen, or null when the area is collapsed - see `SidePanel.chosen`.
 * @param onChoose reports the pick as it was made. Whether picking the showing panel *collapses*
 *   the area is the caller's rule, not this one's, so that the two can be tested apart.
 */
@Composable
fun SidePanelRail(selected: SidePanel?, onChoose: (SidePanel) -> Unit, modifier: Modifier = Modifier) {
  NavigationRail(modifier = modifier) {
    SidePanel.entries.forEach { panel ->
      NavigationRailItem(
        selected = panel == selected,
        onClick = { onChoose(panel) },
        icon = { Icon(panel.icon, contentDescription = null) },
        // **Icon-only, which is the reference's own activity bar**: a 32px-wide tab strip carrying a
        // 20px icon and no text (`--jp-private-sidebar-tab-width: 32px`,
        // `packages/application/style/sidepanel.css:10,41-44`). Measured before it
        // was chosen: "Table of contents" is wider than the rail and spilled across the panel
        // beside it (`evidence/p2o-2-side-panel/`, the run before this). Material allows a rail
        // item with no label, and the name is still on the item for a screen reader and a flow.
        // The name on the item rather than on the icon inside it, which is the rule a flow cost
        // this project in P2b: a screen reader and a Maestro flow address one string.
        modifier = Modifier.semantics { contentDescription = panel.label },
      )
    }
  }
}

/**
 * What the rail calls each panel. Each reference's own caption for the panel it owns -
 * `type: 'File Browser'` (`packages/filebrowser-extension/src/index.ts:676`),
 * `title: "Search"` (`vscode/…/search/browser/search.contribution.ts:55`) and
 * `type: 'Table of Contents'` (`packages/toc-extension/src/index.ts:309`) - in this app's sentence
 * case, and "Files" because that is already the name its own screen carries.
 */
internal val SidePanel.label: String
  get() = when (this) {
    SidePanel.FILES -> "Files"
    SidePanel.SEARCH -> "Search"
    SidePanel.TABLE_OF_CONTENTS -> "Table of contents"
  }

/** Material's nearest equivalents of `folder.svg`, `search.svg` and `toc.svg` in the icon registry. */
private val SidePanel.icon: ImageVector
  get() = when (this) {
    SidePanel.FILES -> Icons.Filled.Folder
    // **Not** AutoMirrored, unlike the two below, and that is Material's own rule rather than an
    // omission: the magnifier is on its list of icons that keep their direction in a right-to-left
    // locale, which is why the icon set ships no mirrored variant of it to import.
    SidePanel.SEARCH -> Icons.Filled.Search
    // AutoMirrored, for the reason the explorer's own file icon is: the glyph's ragged edge
    // flips in a right-to-left locale, and the plain `Icons.Filled.Toc` is deprecated for it.
    SidePanel.TABLE_OF_CONTENTS -> Icons.AutoMirrored.Filled.Toc
  }
