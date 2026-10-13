package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.ThemeOverride
import com.jupydroid.app.domain.port.PlatformNightMode

/**
 * A [PlatformNightMode] that keeps what it was told and tells the platform nothing.
 *
 * `AppContainer`'s default: a JVM test has no platform, and an instrumented one must not leave the
 * app's night mode pinned for whatever runs on the device after it.
 *
 * ```
 * FakePlatformNightMode().followed // []
 * ```
 */
class FakePlatformNightMode : PlatformNightMode {
  private val told = mutableListOf<ThemeOverride>()

  /** Every `follow` so far, in order. */
  val followed: List<ThemeOverride> get() = told.toList()

  override fun follow(override: ThemeOverride) {
    told += override
  }
}
