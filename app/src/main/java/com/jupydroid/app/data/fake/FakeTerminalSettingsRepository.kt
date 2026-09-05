package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.TerminalSettings
import com.jupydroid.app.domain.port.TerminalSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Terminal settings held in memory - [AppContainer][com.jupydroid.app.di.AppContainer]'s default,
 * for the reason every port has one: a screen composes before its adapter is wired.
 *
 * ```
 * FakeTerminalSettingsRepository(TerminalSettings(fontSizeSp = 20))
 * ```
 */
class FakeTerminalSettingsRepository(initial: TerminalSettings = TerminalSettings()) : TerminalSettingsRepository {
  private val settings = MutableStateFlow(initial)

  override fun observeSettings(): Flow<TerminalSettings> = settings

  override suspend fun update(settings: TerminalSettings) {
    this.settings.value = settings
  }
}
