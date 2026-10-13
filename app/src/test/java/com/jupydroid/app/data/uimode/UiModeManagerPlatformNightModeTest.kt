package com.jupydroid.app.data.uimode

import android.app.UiModeManager
import com.jupydroid.app.domain.model.ThemeOverride
import org.junit.Assert.assertEquals
import org.junit.Test

class UiModeManagerPlatformNightModeTest {
  /** System leaves the device to decide; the two fixed choices pin the app's night mode either way. */
  @Test
  fun eachThemeChoice_namesThePlatformsOwnNightMode() {
    assertEquals(UiModeManager.MODE_NIGHT_AUTO, uiModeNightModeFor(ThemeOverride.SYSTEM))
    assertEquals(UiModeManager.MODE_NIGHT_NO, uiModeNightModeFor(ThemeOverride.LIGHT))
    assertEquals(UiModeManager.MODE_NIGHT_YES, uiModeNightModeFor(ThemeOverride.DARK))
  }
}
