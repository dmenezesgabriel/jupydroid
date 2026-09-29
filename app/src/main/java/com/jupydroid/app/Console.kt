package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * A Console - either sharing [sessionPath]'s kernel, which is what a notebook's Kernel menu opens,
 * or a standalone one a Launcher card mints at `console-<count>-<uuid>` under the browsed
 * directory (the reference's `console:create`).
 *
 * Keyed by the session path - `sessionContext.path`'s own identity - so two taps on the same
 * console reveal the one already open rather than stacking a second. [title] is the reference's
 * `name || trans.__('Console %1', count)` (`packages/console/src/panel.ts:70`): "Console 1" for a
 * minted standalone, and the notebook's own path for an attached one, which is what the screen
 * header has always shown.
 *
 * At most one Console ever lives on the stack at a time - `entry<Console>` draws from the single
 * `consoleSession` holder, so both openers sweep any Console already present before pushing (see
 * [MutableList.openStandaloneConsole]).
 *
 * No "standalone" flag survives on the key: the one place that read it was the dismiss label,
 * which the tab strip now derives from the entry actually beneath (`upDestinationLabel`) - an
 * attached Console names its notebook, a minted one the browser, and the stack already knows
 * which is which.
 */
@Serializable
data class Console(val sessionPath: String, val title: String = sessionPath) : NavKey
