package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.ThemeOverride

/**
 * Hands the app's theme choice down to the platform, so what the platform draws itself - the
 * window before Compose exists, `values-night` resources, the system bars' icons - takes the same
 * theme Compose does (ADR-0094).
 *
 * Write-only on purpose: [ThemeOverride], stored by [ThemeSettingsRepository], stays the one record
 * of the choice, and this is never read back as a second one.
 *
 * ```
 * platformNightMode.follow(ThemeOverride.DARK)
 * ```
 */
interface PlatformNightMode {
  /** Makes the platform draw this app in what [override] resolves to; [ThemeOverride.SYSTEM] defers to the device. */
  fun follow(override: ThemeOverride)
}
