package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.LicenceDocument

/**
 * The licence documents that travel with the APK, which the Licences screen lists.
 *
 * A port because where the text comes from is a platform mechanism (the APK's assets), and because
 * a screen test must list documents without an installed APK behind it.
 *
 * ```
 * val shown = licenceDocuments.documents().map { it.title }
 * ```
 */
interface LicenceDocuments {
  /** Every document, in the order a reader meets them; each title is unique. */
  suspend fun documents(): List<LicenceDocument>
}
