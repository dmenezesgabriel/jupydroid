package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.ExplorerSettingsRepository
import com.jupydroid.app.domain.port.ExplorerSettingsRepositoryContract

/**
 * The fake owes the port's contract too.
 *
 * It is not a convenience here: `AppContainer` defaults to it, so every `ExplorerScreenTest` case
 * and every device with no Space reads the collapse through this and not through the disk store.
 * A fake that answered differently would make those tests agree with nothing that ships.
 */
class FakeExplorerSettingsRepositoryContractTest : ExplorerSettingsRepositoryContract() {
  override fun repository(): ExplorerSettingsRepository = FakeExplorerSettingsRepository()
}
