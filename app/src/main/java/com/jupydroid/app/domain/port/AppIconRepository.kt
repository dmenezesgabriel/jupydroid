package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.AppIcon
import kotlinx.coroutines.flow.Flow

/**
 * The launcher-icon choice, observed and selected. The record of which icon is worn is
 * PackageManager's component enabled state - the platform's own answer to "which alias is the
 * launchable one", which is also what launcher, Recents and Settings > Apps read - so there is
 * no settings file behind this port and none should grow one: a second record could disagree
 * with the platform's, and the platform's is the one that paints the icon.
 *
 * ```
 * appIcon.select(AppIcon.BLACK_ORBIT)
 * ```
 */
interface AppIconRepository {
  /** The icon currently worn, and every change after - seeded from which alias resolves as launchable. */
  fun observeIcon(): Flow<AppIcon>

  /**
   * Wears [icon] from now on: enables its alias, disables the other. Non-suspending because the
   * platform call is immediate.
   */
  fun select(icon: AppIcon)
}
