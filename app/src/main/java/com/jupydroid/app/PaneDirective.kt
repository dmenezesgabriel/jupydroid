package com.jupydroid.app

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable

/**
 * How many panes this window has room for, and how they are spaced.
 *
 * `calculatePaneScaffoldDirective` is the library's own answer and it is **already this app's
 * rule**: Compact and Medium get one horizontal partition, Expanded gets two, Large and above get
 * three. Expanded's lower bound is 840dp, which was read out of the library rather than assumed
 * before the hand-written strategy was deleted.
 *
 * The list pane's width is the library's too, by width class: 360dp up to Expanded and 412dp from
 * Large (1200dp), which is what the tablet in landscape is (ADR-0141, `PaneWidthByWindowClassTest`).
 *
 * **A width rule, never a device one.** The tablet in a split-screen window is a compact window
 * and gets one pane, and the class changes under the layout as the divider is dragged.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun paneScaffoldDirective(): PaneScaffoldDirective =
  calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())

/** Whether this window has room for both panes, which is `maxHorizontalPartitions > 1`. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun isWindowWideEnoughForTwoPanes(): Boolean = paneScaffoldDirective().maxHorizontalPartitions > 1

/**
 * The directive with both panes forbidden, for a caller that has been told to draw one.
 *
 * The seam exists because the measure and the order are different things: a collapsed side area
 * is a one-pane layout on a window that still measures two - `Navigation.kt`'s `collapseLeft`
 * reasoning - and `twoPane = false` on a wide window is the same order from a test. No
 * `ForcedSize` draws it: the override sizes layout and measure together, adaptive 1.3.0 reading
 * the container through `LocalDensity.current`
 * (https://issuetracker.google.com/issues/358626778).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun PaneScaffoldDirective.forcedToOnePane(): PaneScaffoldDirective = copy(maxHorizontalPartitions = 1)
