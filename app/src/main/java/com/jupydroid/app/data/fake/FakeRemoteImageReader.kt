package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.RemoteImageReader

/**
 * A [RemoteImageReader] over a fixed map of web address to bytes, held to the port's cap; any
 * other address serves nothing.
 *
 * `AppContainer`'s default, which serves nothing: a test or a preview draws every web image's alt
 * rather than reaching the network.
 */
class FakeRemoteImageReader(private val bytesByUrl: Map<String, ByteArray> = emptyMap()) : RemoteImageReader {
  override suspend fun imageBytes(url: String): ByteArray? =
    bytesByUrl[url]?.takeIf { it.size <= RemoteImageReader.MAX_IMAGE_BYTES }
}
