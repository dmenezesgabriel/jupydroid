package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.port.EditorSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Editor settings held in memory - [AppContainer][com.jupydroid.app.di.AppContainer]'s default,
 * for the reason every port has one: a screen composes before its adapter is wired
 * (`FakeKernelGateway`, `FakeRecentDocumentsRepository`).
 *
 * ```
 * FakeEditorSettingsRepository(EditorSettings(autoClosingBrackets = false))
 * ```
 */
class FakeEditorSettingsRepository(initial: EditorSettings = EditorSettings()) : EditorSettingsRepository {
  private val settings = MutableStateFlow(initial)

  override fun observeSettings(): Flow<EditorSettings> = settings

  override suspend fun update(settings: EditorSettings) {
    this.settings.value = settings
  }
}
