package com.jupydroid.app.ui.shell

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
