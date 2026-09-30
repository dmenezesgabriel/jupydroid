package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The app-wide Settings screen.
 *
 * A singleton, not a document: there is exactly one, it carries no id, and
 * [MutableList.openSettings] reveals it rather than reopening it. Reached from
 * [ExplorerScreen][com.jupydroid.app.ui.explorer.ExplorerScreen]'s own bar, the one persistent
 * chrome not scoped to an open document, since the rail's next slot is reserved for a Launcher.
 */
@Serializable data object Settings : NavKey
