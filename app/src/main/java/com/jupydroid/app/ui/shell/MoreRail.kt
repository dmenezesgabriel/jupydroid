package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jupydroid.app.ShownPanel
import com.jupydroid.app.theme.chromePalette
import com.jupydroid.extension.api.SidePanelContribution
import com.jupydroid.extension.api.focusRing
import com.jupydroid.extension.api.glyphBesideText
import kotlinx.coroutines.launch

/**
 * The rail at one pane, shown modally from the bottom bar's More: what the bar has no room for -
 * New terminal, then every contributed panel - with Spaces and Settings at its foot where
 * `SidePanelRail` has them beside two panes (ADR-0109).
 *
 * **It does not repeat the bar's panels.** A row named Files over a bar item named Files puts one
 * string on screen twice (ADR-0055), so each destination at one pane is on exactly one surface.
 * Opened by a tap on More only: `ModalWideNavigationRail` has no edge swipe, so nothing competes
 * with the system Back edge. Back and the scrim close it.
 *
 * ```
 * val railState = rememberWideNavigationRailState()
 * MoreRail(railState, contributions, selected = shown, onChoose = { … }, onNewTerminal = { … }) { … }
 * ```
 *
 * @param state expanded while the rail is open; More expands it, and every row collapses it.
 * @param contributions the panels extensions add (ADR-0104), one row each in the order given.
 * @param selected the panel filling the pane; a contributed panel's row is marked while it shows, as
 *   VS Code radio-checks the active container in its overflow menu
 *   (`vscode@3f3380c6 src/vs/workbench/browser/parts/compositeBarActions.ts`).
 * @param onChoose reports a contributed row's pick, as the bar's own items report theirs.
 * @param onNewTerminal `terminal:create-new`, which gave the bar's fifth slot up to More.
 * @param onOpenSettings opens the app-wide Settings screen.
 * @param onLeaveWorkspace leaves the Space for the Spaces list - the dirty-gated leave.
 */
@Composable
fun MoreRail(
  state: WideNavigationRailState,
  contributions: List<SidePanelContribution>,
  selected: ShownPanel,
  onChoose: (ShownPanel) -> Unit,
  onNewTerminal: () -> Unit,
  onOpenSettings: () -> Unit,
  onLeaveWorkspace: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val actThenClose = { act: () -> Unit ->
    act()
    scope.launch { state.collapse() }
    Unit
  }
  ModalWideNavigationRail(
    state = state,
    // Hidden until More expands it: collapsed, the bar is the one-pane activity bar, not this.
    hideOnCollapse = true,
    // `activityBar.background`, the colour the two-pane rail states: it is the same rail, shown
    // modally.
    colors = WideNavigationRailDefaults.colors(modalContainerColor = chromePalette.activityBarBackground),
    // The wide rail's content is no `ColumnScope`, so the two-pane rail's weighted spacer has no
    // equivalent here: spreading the rows apart puts the foot group at the bottom, and the two
    // groups above it keep theirs together in a `Column`.
    arrangement = Arrangement.SpaceBetween,
  ) {
    Column {
      MoreRailRow(NEW_TERMINAL, Icons.Filled.Terminal) { actThenClose(onNewTerminal) }
      contributions.forEach { panel ->
        val shown = ShownPanel.Contributed(panel.id)
        MoreRailRow(panel.label, panel.icon, selected = shown == selected) { actThenClose { onChoose(shown) } }
      }
    }
    Column {
      MoreRailRow(LEAVE_WORKSPACE, Icons.Filled.Home) { actThenClose(onLeaveWorkspace) }
      MoreRailRow(OPEN_SETTINGS, Icons.Filled.Settings) { actThenClose(onOpenSettings) }
    }
  }
}

/**
 * One row of [MoreRail]: labelled, because an expanded rail is Material's labelled form. New terminal
 * Spaces and Settings are never selected: they open something in the main area, which owns its own
 * selection. A contributed panel's row is, while that panel fills the pane.
 */
@Composable
internal fun MoreRailRow(label: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
  val glyph = LocalDensity.current.glyphBesideText(MORE_ROW_ICON, MaterialTheme.typography.labelLarge.fontSize)
  WideNavigationRailItem(
    selected = selected,
    onClick = onClick,
    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(glyph).testTag(MORE_ROW_GLYPH_TAG)) },
    label = { Text(label) },
    railExpanded = true,
    modifier = Modifier.focusRing(),
  )
}

/**
 * A row's glyph at the default font scale: Material's `NavigationRailBaselineItemTokens.IconSize`, which
 * `Icon` also defaults to, so a row at 1.0 is as it was. It grows with the label from there.
 */
private val MORE_ROW_ICON = 24.dp

/** A row's glyph, which has no description to find it by; `MoreRailFontScaleTest` reads its size. */
internal const val MORE_ROW_GLYPH_TAG = "More row glyph"

/** The bar's fifth item's name, the menu Material opens a modal rail from. */
const val MORE = "More"
