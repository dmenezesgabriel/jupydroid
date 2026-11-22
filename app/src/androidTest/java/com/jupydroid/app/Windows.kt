package com.jupydroid.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The window a test needs is part of the test, so the test states it.
 *
 * `DeviceConfigurationOverride.ForcedSize` changes the layout constraints **and** the window
 * `paneScaffoldDirective()` measures: adaptive 1.3.0 reads the container's pixels through
 * `LocalDensity.current`, which the override sets
 * (https://issuetracker.google.com/issues/358626778). The 900dp answer is Expanded -
 * `maxHorizontalPartitions = 2` with the 360dp list pane (`PaneDirective.kt`); 360dp is Compact,
 * one partition. Run a two-pane case at the emulator's real 320dp instead and the detail has no
 * width at all: `ListDetailSceneStrategy` draws one entry and the list's rows never enter the
 * tree - what `TwoPaneLayoutTest` records of the runs before the size was stated.
 */
internal fun windowFor(twoPane: Boolean): DpSize = if (twoPane) DpSize(900.dp, 800.dp) else DpSize(360.dp, 720.dp)
