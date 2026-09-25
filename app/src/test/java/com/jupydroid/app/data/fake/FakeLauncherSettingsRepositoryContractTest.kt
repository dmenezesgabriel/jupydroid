package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.LauncherSettingsRepository
import com.jupydroid.app.domain.port.LauncherSettingsRepositoryContract

/**
 * The fake owes the port's contract too.
 *
 * It is not a convenience here: `AppContainer` defaults to it, so every navigation test and every
 * device with no Space reads the first-run flag through this and not through the disk store. A
 * fake that answered differently would make those tests agree with nothing that ships.
 */
class FakeLauncherSettingsRepositoryContractTest : LauncherSettingsRepositoryContract() {
  override fun repository(): LauncherSettingsRepository = FakeLauncherSettingsRepository()
}
