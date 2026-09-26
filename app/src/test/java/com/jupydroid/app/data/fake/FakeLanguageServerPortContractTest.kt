package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.LanguageServerPortContract

/** [FakeLanguageServerPort] held to the contract every implementation answers. */
class FakeLanguageServerPortContractTest : LanguageServerPortContract() {
  override fun languageServer(): LanguageServerPort = FakeLanguageServerPort()
}
