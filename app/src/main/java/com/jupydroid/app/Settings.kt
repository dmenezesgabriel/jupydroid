package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The app-wide Settings screen.
 *
 * A singleton, not a document: there is exactly one, it carries no id, and
 * [MutableList.openSettings] reveals it rather than reopening it. Reached beside two panes from the
 * gear at the foot of [SidePanelRail][com.jupydroid.app.ui.shell.SidePanelRail], and at one pane from
 * the foot of [MoreRail][com.jupydroid.app.ui.shell.MoreRail], behind the bottom bar's More (ADR-0109).
 */
@Serializable data object Settings : NavKey
