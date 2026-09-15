package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.ThemeSettings
import com.jupydroid.app.domain.port.ThemeSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Theme settings held in memory - [AppContainer][com.jupydroid.app.di.AppContainer]'s default, for
 * the reason every port has one: a screen composes before its adapter is wired.
 *
 * ```
 * FakeThemeSettingsRepository(ThemeSettings(override = ThemeOverride.DARK))
 * ```
 */
class FakeThemeSettingsRepository(initial: ThemeSettings = ThemeSettings()) : ThemeSettingsRepository {
  private val settings = MutableStateFlow(initial)

  override fun observeSettings(): Flow<ThemeSettings> = settings

  override suspend fun update(settings: ThemeSettings) {
    this.settings.value = settings
  }
}
