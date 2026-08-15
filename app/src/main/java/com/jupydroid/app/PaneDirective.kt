package com.jupydroid.app

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable

/**
 * How many panes this window has room for, and how they are spaced.
 *
 * `calculatePaneScaffoldDirective` is the library's own answer and it is **already this app's
 * rule**: Compact and Medium get one horizontal partition, Expanded gets two, Large and above get
 * three. Expanded's lower bound is 840dp, which is the number `docs/DECISIONS.md` argues for from
 * the list pane's width - 360dp of list plus a divider leaves 239dp for the notebook in a 600dp
 * window, narrower than the phone the single-pane layout was designed for. P1e checked that
 * before deleting the hand-written strategy rather than assuming it.
 *
 * **A width rule, never a device one.** The tablet in a split-screen window is a compact window
 * and gets one pane, and the class changes under the layout as the divider is dragged.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun paneScaffoldDirective(): PaneScaffoldDirective =
  calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())

/** Whether this window has room for both panes, which is `maxHorizontalPartitions > 1`. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun isWindowWideEnoughForTwoPanes(): Boolean = paneScaffoldDirective().maxHorizontalPartitions > 1

/**
 * The directive with both panes forbidden, for a caller that has been told to draw one.
 *
 * The seam exists because `currentWindowAdaptiveInfo()` measures the **activity's** window, which a
 * `DeviceConfigurationOverride` around a composable does not change - so a test cannot choose the
 * branch by forcing a width, and would drive one branch twice while appearing to cover both. The
 * same reason `MainNavigation` takes `twoPane` as a parameter at all (`CellActionsTest`).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun PaneScaffoldDirective.forcedToOnePane(): PaneScaffoldDirective = copy(maxHorizontalPartitions = 1)
