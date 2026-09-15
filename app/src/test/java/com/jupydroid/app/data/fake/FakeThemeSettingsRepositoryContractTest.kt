package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.ThemeSettingsRepository
import com.jupydroid.app.domain.port.ThemeSettingsRepositoryContract

/**
 * The fake owes the port's contract too.
 *
 * `AppContainer` defaults to it, so every `SettingsScreenTest` case and every device with no Space
 * reads the theme through this and not through the disk store.
 */
class FakeThemeSettingsRepositoryContractTest : ThemeSettingsRepositoryContract() {
  override fun repository(): ThemeSettingsRepository = FakeThemeSettingsRepository()
}
