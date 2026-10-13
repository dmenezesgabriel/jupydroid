package com.jupydroid.app.data.uimode

import android.app.UiModeManager
import com.jupydroid.app.domain.model.ThemeOverride
import com.jupydroid.app.domain.port.PlatformNightMode

/**
 * The app's theme as the platform's per-app night mode (`UiModeManager.setApplicationNightMode`,
 * API 31), which the system keeps across launches and folds into this app's configuration - so the
 * window, `values-night` and `isSystemInDarkTheme()` all answer with the app's choice (ADR-0094).
 * termux-app@3df69d1d does the same through `AppCompatDelegate`; this app has no AppCompat.
 *
 * ```
 * UiModeManagerPlatformNightMode(context.getSystemService(UiModeManager::class.java))
 * ```
 */
class UiModeManagerPlatformNightMode(private val uiModeManager: UiModeManager) : PlatformNightMode {
  override fun follow(override: ThemeOverride) = uiModeManager.setApplicationNightMode(uiModeNightModeFor(override))
}

/**
 * The `UiModeManager.MODE_NIGHT_*` value [override] names. `MODE_NIGHT_AUTO` is what leaves the
 * app's night mode undefined, so the device's own setting shows through.
 *
 * ```
 * uiModeNightModeFor(ThemeOverride.DARK) // UiModeManager.MODE_NIGHT_YES
 * ```
 */
fun uiModeNightModeFor(override: ThemeOverride): Int = when (override) {
  ThemeOverride.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
  ThemeOverride.LIGHT -> UiModeManager.MODE_NIGHT_NO
  ThemeOverride.DARK -> UiModeManager.MODE_NIGHT_YES
}
