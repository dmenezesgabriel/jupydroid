package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.ExplorerSettings
import com.jupydroid.app.domain.port.ExplorerSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Explorer settings held in memory - [AppContainer][com.jupydroid.app.di.AppContainer]'s default,
 * for the reason every port has one: a screen composes before its adapter is wired.
 *
 * ```
 * FakeExplorerSettingsRepository(ExplorerSettings(recentsCollapsed = false))
 * ```
 */
class FakeExplorerSettingsRepository(initial: ExplorerSettings = ExplorerSettings()) : ExplorerSettingsRepository {
  private val settings = MutableStateFlow(initial)

  override fun observeSettings(): Flow<ExplorerSettings> = settings

  override suspend fun update(settings: ExplorerSettings) {
    this.settings.value = settings
  }
}
