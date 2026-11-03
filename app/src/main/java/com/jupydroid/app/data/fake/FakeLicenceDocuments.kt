package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.LicenceDocument
import com.jupydroid.app.domain.port.LicenceDocuments

/**
 * A [LicenceDocuments] holding two short documents, so the Licences screen composes where no APK
 * assets stand behind it. `AppContainer`'s default, like every port's fake.
 */
class FakeLicenceDocuments : LicenceDocuments {
  override suspend fun documents(): List<LicenceDocument> = listOf(
    LicenceDocument(title = "Licence", text = "Apache License, Version 2.0"),
    LicenceDocument(title = "Notices", text = "Jupydroid"),
  )
}
