package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.domain.port.LauncherSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Launcher settings held in memory - [AppContainer][com.jupydroid.app.di.AppContainer]'s default,
 * for the reason every port has one: a screen composes before its adapter is wired.
 *
 * ```
 * FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true))
 * ```
 */
class FakeLauncherSettingsRepository(initial: LauncherSettings = LauncherSettings()) : LauncherSettingsRepository {
  private val settings = MutableStateFlow(initial)

  override fun observeSettings(): Flow<LauncherSettings> = settings

  override suspend fun update(settings: LauncherSettings) {
    this.settings.value = settings
  }
}
