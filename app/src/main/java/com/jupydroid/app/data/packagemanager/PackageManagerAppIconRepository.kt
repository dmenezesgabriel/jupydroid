package com.jupydroid.app.data.packagemanager

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.jupydroid.app.domain.model.AppIcon
import com.jupydroid.app.domain.port.AppIconRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The icon choice as PackageManager's component enabled state - the platform's own record of
 * which `activity-alias` (`app/src/main/AndroidManifest.xml`) is the launchable one, and the
 * same record launcher, Recents and Settings > Apps resolve the icon from. There is no settings
 * file behind this port because a second record could disagree with the platform's, and the
 * platform's is the one that paints the icon.
 *
 * `select` enables the chosen alias before disabling the other, so the app is never left with
 * zero launchable components mid-flip, and `DONT_KILL_APP` asks the platform not to restart the
 * process for a change the running app already knows about. The observed value is seeded from
 * `getLaunchIntentForPackage` - which alias the platform resolves right now - so a process that
 * restarts after a flip reads the choice back rather than assuming the default.
 *
 * ```
 * PackageManagerAppIconRepository(applicationContext)
 * ```
 */
class PackageManagerAppIconRepository(context: Context) : AppIconRepository {
  private val packageManager = context.packageManager
  private val packageName = context.packageName
  private val blueAlias = ComponentName(packageName, "$packageName.BlueOrbitIcon")
  private val blackAlias = ComponentName(packageName, "$packageName.BlackOrbitIcon")
  private val icon = MutableStateFlow(seedIcon())
  private val selectionLock = Any()

  override fun observeIcon(): Flow<AppIcon> = icon

  override fun select(icon: AppIcon) {
    synchronized(selectionLock) {
      val (enable, disable) = when (icon) {
        AppIcon.BLUE_ORBIT -> blueAlias to blackAlias
        AppIcon.BLACK_ORBIT -> blackAlias to blueAlias
      }
      packageManager.setComponentEnabledSetting(
        enable,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP,
      )
      packageManager.setComponentEnabledSetting(
        disable,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP,
      )
      this.icon.value = icon
    }
  }

  /** Which alias the platform currently resolves as the app's launcher entry; anything unexpected wears blue. */
  private fun seedIcon(): AppIcon {
    val launchable = packageManager.getLaunchIntentForPackage(packageName)?.component
    return if (launchable == blackAlias) AppIcon.BLACK_ORBIT else AppIcon.BLUE_ORBIT
  }
}
