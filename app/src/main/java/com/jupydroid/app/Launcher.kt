package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The Launcher.
 *
 * A `MainAreaWidget` in the reference, added to `'main'` rather than to a panel
 * (`packages/launcher-extension/src/index.ts:118`) - so it is a detail-pane key here, never a
 * [SidePanel]. At most one ever exists: the reference's `maybeCreate` keeps it singular, and a
 * `data object`'s single `contentKey` would make a second equal entry share one `ViewModelStore`
 * anyway - [MutableList.openLauncher] dedupes rather than stacking it.
 */
@Serializable data object Launcher : NavKey
