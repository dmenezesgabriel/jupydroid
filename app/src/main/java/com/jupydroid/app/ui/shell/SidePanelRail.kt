package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.ShownPanel
import com.jupydroid.app.railOrder
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.extension.api.SidePanelContribution

/**
 * The activity bar: which side panel is showing, and the only control that changes it.
 *
 * **Beside the whole shell, not inside the side area**, which is where the reference puts it:
 * `hboxPanel` holds `leftHandler.sideBar` next to the split that contains *both* the panel and the
 * document area (`packages/application/src/shell.ts:468-477`). An earlier plan row said "at the left edge
 * of the list pane"; reading the shell corrected it, and the correction is what lets the area
 * collapse to the bar alone while the document keeps the width.
 *
 * One item per `SidePanel` and per contributed panel, in [railOrder], and `selected` says which panel
 * is up - to the eye and to a screen reader at once, which is what a `NavigationRailItem` already
 * carries.
 *
 * ```
 * SidePanelRail(selected = panel, onChoose = { panel = panel.chosen(it) })
 * ```
 *
 * @param selected the panel on screen, or null when the area is collapsed - see `chosen`.
 * @param onChoose reports the pick as it was made. Whether picking the showing panel *collapses*
 *   the area is the caller's rule, not this one's, so that the two can be tested apart.
 * @param onOpenLauncher the tab strip's `+` - `launcher-extension`'s `addButtonEnabled` button,
 *   labelled *New Launcher*. A rail item and not a panel pick because the Launcher is a document:
 *   it has no selected state to hold.
 * @param onOpenSettings opens the app-wide Settings screen, from the rail's foot - see the item's
 *   own comment for why there.
 * @param contributions the panels extensions add (ADR-0104), each drawn with its own label and icon.
 */
@Composable
fun SidePanelRail(
  selected: ShownPanel?,
  onChoose: (ShownPanel) -> Unit,
  onOpenLauncher: () -> Unit,
  onOpenSettings: () -> Unit,
  contributions: List<SidePanelContribution>,
  modifier: Modifier = Modifier,
) {
  val contributed = contributions.associateBy { it.id }
  // **`activityBar.background`, stated.** Material's default here is `colorScheme.surface`, which
  // this app maps to `editor.background` - so the rail used to be the same field as the document
  // beside it, with only the divider between them (
  // measured at y=900). The reference draws the bar a step off the page.
  NavigationRail(modifier = modifier, containerColor = chromePalette.activityBarBackground) {
    railOrder(contributions.map { it.id }).forEach { panel ->
      val (label, icon) = when (panel) {
        is ShownPanel.Shell -> panel.panel.label to panel.panel.icon
        is ShownPanel.Contributed -> contributed.getValue(panel.id).let { it.label to it.icon }
      }
      NavigationRailItem(
        selected = panel == selected,
        onClick = { onChoose(panel) },
        icon = { Icon(icon, contentDescription = null) },
        // **Icon-only, which is the reference's own activity bar**: a 32px-wide tab strip carrying a
        // 20px icon and no text (`--jp-private-sidebar-tab-width: 32px`,
        // `packages/application/style/sidepanel.css:10,41-44`). Measured before it
        // was chosen: "Table of contents" is wider than the rail and spilled across the panel
        // beside it (the run before this). Material allows a rail
        // item with no label, and the name is still on the item for a screen reader and a flow.
        // The name on the item rather than on the icon inside it, so a screen reader and a Maestro
        // flow address one string (ADR-0143).
        modifier = Modifier.semantics { contentDescription = label },
      )
    }
    // `launcher:create` as the tab strip's `+` - JupyterLab's own reach point for a launcher,
    // after the panels the way the tab strip's add button sits after the last tab. Always
    // unselected: it opens a document, and the document owns its selection state.
    NavigationRailItem(
      selected = false,
      onClick = onOpenLauncher,
      icon = { Icon(Icons.Filled.Add, contentDescription = null) },
      label = null,
      modifier = Modifier.semantics { contentDescription = NEW_LAUNCHER },
    )
    // **VS Code's Manage**: the gear is the global activity, drawn after the view containers at the
    // activity bar's foot (`vscode@3f3380c6 src/vs/workbench/browser/parts/globalCompositeBar.ts`,
    // `GLOBAL_ACTIVITY_ID` in `src/vs/workbench/common/activity.ts`), so it is on screen whatever
    // panel is - which the Files bar's gear, its home before this, was not. Always unselected for
    // the `+`'s reason: Settings opens in the main area, and selection there is the strip's.
    Spacer(Modifier.weight(1f))
    NavigationRailItem(
      selected = false,
      onClick = onOpenSettings,
      icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
      label = null,
      modifier = Modifier.semantics { contentDescription = OPEN_SETTINGS },
    )
  }
}

/**
 * Settings' name, on the rail's gear and on the modal rail's row alike - one string, so a screen
 * reader and a Maestro flow address Settings the same way at either width.
 */
const val OPEN_SETTINGS = "Settings"
