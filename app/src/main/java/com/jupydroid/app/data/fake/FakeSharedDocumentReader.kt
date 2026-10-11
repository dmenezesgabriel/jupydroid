package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.SharedDocumentReader

/**
 * A [SharedDocumentReader] over a fixed map of URI to bytes; any other URI is refused, as a
 * provider refuses a document it no longer has.
 *
 * `AppContainer`'s default, which serves nothing: a device with no runnable Space still opens, and
 * an import it is handed lands nowhere rather than as a document this app made up.
 */
class FakeSharedDocumentReader(private val bytesByUri: Map<String, ByteArray> = emptyMap()) : SharedDocumentReader {
  override suspend fun readBytes(uri: String): ByteArray =
    requireNotNull(bytesByUri[uri]) { "no shared document at '$uri'; this fake serves ${bytesByUri.keys}" }
}
