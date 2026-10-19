package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import com.jupydroid.app.theme.chromePalette
import kotlinx.coroutines.launch

/**
 * The rail at one pane, shown modally from the bottom bar's More: what the bar has no room for,
 * with Settings at its foot where `SidePanelRail` has it beside two panes (ADR-0109).
 *
 * **It does not repeat the bar's panels.** A row named Files over a bar item named Files puts one
 * string on screen twice (ADR-0055), so each destination at one pane is on exactly one surface.
 * Opened by a tap on More only: `ModalWideNavigationRail` has no edge swipe, so nothing competes
 * with the system Back edge. Back and the scrim close it.
 *
 * ```
 * val railState = rememberWideNavigationRailState()
 * MoreRail(railState, onNewTerminal = { … }, onOpenSettings = { … })
 * ```
 *
 * @param state expanded while the rail is open; More expands it, and every row collapses it.
 * @param onNewTerminal `terminal:create-new`, which gave the bar's fifth slot up to More.
 * @param onOpenSettings opens the app-wide Settings screen.
 */
@Composable
fun MoreRail(state: WideNavigationRailState, onNewTerminal: () -> Unit, onOpenSettings: () -> Unit) {
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
    // equivalent here: spreading the two rows puts Settings at the foot.
    arrangement = Arrangement.SpaceBetween,
  ) {
    MoreRailRow(NEW_TERMINAL, Icons.Filled.Terminal) { actThenClose(onNewTerminal) }
    MoreRailRow(OPEN_SETTINGS, Icons.Filled.Settings) { actThenClose(onOpenSettings) }
  }
}

/**
 * One row of [MoreRail]: labelled, because an expanded rail is Material's labelled form and nothing
 * else is on screen to share the string with. Never selected: both rows open something in the main
 * area, which owns its own selection.
 */
@Composable
private fun MoreRailRow(label: String, icon: ImageVector, onClick: () -> Unit) {
  WideNavigationRailItem(
    selected = false,
    onClick = onClick,
    icon = { Icon(icon, contentDescription = null) },
    label = { Text(label) },
    railExpanded = true,
  )
}

/** The bar's fifth item's name, the menu Material opens a modal rail from. */
const val MORE = "More"
