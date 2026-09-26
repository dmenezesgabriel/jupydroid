package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.LanguageDiagnostic
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.LanguageServerPortContract
import com.jupydroid.app.domain.port.PublishDiagnostics

/** [FakeLanguageServerPort] held to the contract every implementation answers. */
class FakeLanguageServerPortContractTest : LanguageServerPortContract() {
  private var fake = FakeLanguageServerPort()

  override fun languageServer(): LanguageServerPort = FakeLanguageServerPort().also { fake = it }

  override suspend fun pushDiagnostics(uri: String) {
    fake.emitDiagnostics(
      PublishDiagnostics(
        uri,
        listOf(
          LanguageDiagnostic(
            range = LanguageRange(LanguagePosition(0, 0), LanguagePosition(0, 5)),
            severity = 1,
            code = "F821",
            source = "pyflakes",
            message = "undefined name 'x'",
          ),
        ),
      ),
    )
  }
}
