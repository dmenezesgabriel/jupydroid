package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.AppIconRepository
import com.jupydroid.app.domain.port.AppIconRepositoryContract

/**
 * The fake owes the port's contract too.
 *
 * It is what `AppContainer` defaults to, so the Settings row and every test that composes it
 * read the icon choice through this and not through PackageManager. A fake that answered
 * differently would make those tests agree with nothing that ships.
 */
class FakeAppIconRepositoryContractTest : AppIconRepositoryContract() {
  override fun repository(): AppIconRepository = FakeAppIconRepository()
}
