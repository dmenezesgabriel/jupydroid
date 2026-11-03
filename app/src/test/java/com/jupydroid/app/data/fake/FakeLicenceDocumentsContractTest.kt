package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.LicenceDocuments
import com.jupydroid.app.domain.port.LicenceDocumentsContract

/**
 * The fake owes the port's contract too: `AppContainer` defaults to it, so every navigation test
 * and every instrumented test lists the Licences screen's rows through this and not through the
 * APK's assets.
 */
class FakeLicenceDocumentsContractTest : LicenceDocumentsContract() {
  override fun documents(): LicenceDocuments = FakeLicenceDocuments()
}
