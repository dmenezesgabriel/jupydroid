package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.jupydroid.app.SidePanel
import com.jupydroid.app.onePaneDestinations
import com.jupydroid.app.theme.chromePalette

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
  // **The bar measures itself**, rather than being told a width or asking the window. At one pane
  // the pane *is* the window, so its own constraints are the width the rule is about - and unlike
  // `currentWindowAdaptiveInfo()`, which reads the activity's window, a constraint is something a
  // test can actually set, so both branches get driven rather than one of them twice
  // (`ActionRowPlacement` records what that cost).
  BoxWithConstraints(modifier = modifier) {
    // The same `activityBar.background` the rail states, because it is the same bar moved -
    // the reference gives its three `ActivityBarPosition`s one colour id, not three.
    ShortNavigationBar(
      arrangement = itemArrangementAt(maxWidth),
      containerColor = chromePalette.activityBarBackground,
    ) {
      onePaneDestinations.forEach { panel ->
        ShortNavigationBarItem(
          selected = panel == selected,
          onClick = { onChoose(panel) },
          icon = { Icon(panel.icon, contentDescription = null) },
          // **No label, which is the rail's rule and the reference's own.** VS Code's activity bar
          // is icon-only in all three of its positions, and this app already had the rule for the
          // rail: the name goes on the *item* so a screen reader and a Maestro flow address one
          // string (P2b). Measured before it was chosen: `ExplorerScreen` titles itself "Files" and
          // `SearchSidePanel` "Search", and a labelled item directly below either one would put
          // that string on screen twice - `onNodeWithText` throws on two matches and `tapOn` picks
          // one of them. The departure from Material's "labels recommended" is in
          // `docs/DECISIONS.md`.
          label = null,
          modifier = Modifier.semantics { contentDescription = panel.label },
        )
      }
    }
  }
}

/**
 * How the bar distributes its items at [barWidth] - and **the rule is the reference's own sentence**.
 *
 * `ShortNavigationBar`'s KDoc (material3 1.4.0): *"In small screens … its arrangement should be
 * [ShortNavigationBarArrangement.EqualWeight], so that the navigation items are equally distributed
 * on the bar"*; *"In medium screens … its arrangement should be
 * [ShortNavigationBarArrangement.Centered], so that the navigation items are distributed grouped on
 * the center of the bar"*.
 *
 * **The tablet is what asked for this, and only a screenshot could have.** P2y-c drew one bar at
 * both widths; measured at 800dp, each of two items was **396dp** wide with centres 404dp apart - a
 * 24dp icon inside a tap target half the window across, with the two controls stranded at the
 * quarter points. Grouped, `calculateCenteredContentHorizontalPadding` puts two items in 50% of the
 * bar, so their centres sit a quarter of it apart. `evidence/p2y-bottom-navigation/` has both.
 *
 * A pure `Dp` predicate rather than a window query, so the seam a test drives is the same one the
 * bar reads.
 */
internal fun itemArrangementAt(barWidth: Dp): ShortNavigationBarArrangement =
  if (barWidth >= WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp) {
    ShortNavigationBarArrangement.Centered
  } else {
    ShortNavigationBarArrangement.EqualWeight
  }
