package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.HarnessScripts
import com.jupydroid.app.domain.port.HarnessScriptsContract

/**
 * The fake owes the port's contract too: `AppContainer` defaults to it, so every screen test
 * expands a harness's script through this and not through the APK's assets.
 */
class FakeHarnessScriptsContractTest : HarnessScriptsContract() {
  override fun scripts(): HarnessScripts = FakeHarnessScripts()
}
