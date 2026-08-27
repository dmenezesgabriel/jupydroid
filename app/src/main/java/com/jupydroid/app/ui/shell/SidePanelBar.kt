package com.jupydroid.app.ui.shell

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jupydroid.app.SidePanel
import com.jupydroid.app.onePaneDestinations

/**
 * The activity bar where the shell draws **one pane**: `SidePanelRail`'s bar, moved to the bottom.
 *
 * **A relocation, not an invention.** VS Code's own activity bar has a bottom position carrying the
 * same containers - `ActivityBarPosition.BOTTOM`
 * (`vscode/…/services/layout/browser/layoutService.ts:75-80`), driven by
 * `LayoutSettings.ACTIVITY_BAR_LOCATION` (`…/parts/activitybar/activitybarPart.ts:604-616`).
 * JupyterLab answers nothing here: `packages/application/` has no media query and no width branch,
 * and its `'desktop' | 'mobile'` form factor (`packages/application/src/frontend.ts:28,44,120`) is
 * never assigned - which is the split `docs/DECISIONS.md` records under P2x-1e.
 *
 * **Two destinations rather than the rail's three**, which is [onePaneDestinations]' own rule, and
 * two is below Material's *"three to five destinations of equal importance"* - recorded in
 * `docs/DECISIONS.md` rather than padded.
 *
 * ```
 * if (!twoPane) SidePanelBar(selected = showingPanel, onChoose = { sidePanel = it })
 * ```
 *
 * @param selected the panel filling the pane. Never null, unlike the rail's: a collapsed area over
 *   an empty window is not a state, so this bar has no deselected form - `SidePanel.showingAtOnePane`
 *   is what guarantees the caller has one to pass.
 * @param onChoose reports the pick. **Not `SidePanel.chosen`'s toggle**: re-picking the showing
 *   panel here would collapse the only thing on screen, so the caller assigns rather than folds.
 */
@Composable
fun SidePanelBar(selected: SidePanel, onChoose: (SidePanel) -> Unit, modifier: Modifier = Modifier) {
  NavigationBar(modifier = modifier) {
    onePaneDestinations.forEach { panel ->
      NavigationBarItem(
        selected = panel == selected,
        onClick = { onChoose(panel) },
        icon = { Icon(panel.icon, contentDescription = null) },
        // **No `label`, which is the rail's rule and the reference's own.** VS Code's activity bar
        // is icon-only in all three of its positions, and this app already had the rule for the
        // rail: the name goes on the *item* so a screen reader and a Maestro flow address one
        // string (P2b). Measured before it was chosen: `ExplorerScreen` titles itself "Files" and
        // `SearchSidePanel` "Search", and a labelled item directly below either one would put that
        // string on screen twice - `onNodeWithText` throws on two matches and `tapOn` picks one of
        // them. The departure from Material's "labels recommended" is in `docs/DECISIONS.md`.
        modifier = Modifier.semantics { contentDescription = panel.label },
      )
    }
  }
}
