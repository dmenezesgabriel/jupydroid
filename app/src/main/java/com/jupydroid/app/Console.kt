package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * A Console - either sharing [sessionPath]'s kernel, which is what a notebook's Kernel menu opens
 * (P6b-9), or a standalone one a Launcher card mints at `console-<count>-<uuid>` under the browsed
 * directory (P6d-5, the reference's `console:create`).
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
 * [standalone] marks the minted kind, and the dismiss label is what needs it: an attached Console
 * goes "Back to notebook" while a standalone one goes to the browser like every other document.
 */
@Serializable
data class Console(val sessionPath: String, val title: String = sessionPath, val standalone: Boolean = false) : NavKey
