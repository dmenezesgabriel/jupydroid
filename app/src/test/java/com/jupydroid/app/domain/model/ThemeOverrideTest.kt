package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The pure fact [ThemeOverride] carries: which two of its three states listen to the system. */
class ThemeOverrideTest {
  @Test
  fun systemFollowsTheDeviceWhenTheDeviceIsDark() {
    assertEquals(true, ThemeOverride.SYSTEM.resolvesToDark(systemInDarkTheme = true))
  }

  @Test
  fun systemFollowsTheDeviceWhenTheDeviceIsLight() {
    assertEquals(false, ThemeOverride.SYSTEM.resolvesToDark(systemInDarkTheme = false))
  }

  @Test
  fun lightIgnoresADarkDevice() {
    assertEquals(false, ThemeOverride.LIGHT.resolvesToDark(systemInDarkTheme = true))
  }

  @Test
  fun lightIgnoresALightDevice() {
    assertEquals(false, ThemeOverride.LIGHT.resolvesToDark(systemInDarkTheme = false))
  }

  @Test
  fun darkIgnoresALightDevice() {
    assertEquals(true, ThemeOverride.DARK.resolvesToDark(systemInDarkTheme = false))
  }

  @Test
  fun darkIgnoresADarkDevice() {
    assertEquals(true, ThemeOverride.DARK.resolvesToDark(systemInDarkTheme = true))
  }
}
