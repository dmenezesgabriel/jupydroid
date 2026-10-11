package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.RemoteImageReader
import com.jupydroid.app.domain.port.RemoteImageReaderContract

class FakeRemoteImageReaderContractTest : RemoteImageReaderContract() {
  override fun reader(servedByPath: Map<String, ByteArray>): RemoteImageReader =
    FakeRemoteImageReader(servedByPath.mapKeys { (path, _) -> urlOf(path) })

  override fun urlOf(path: String): String = "https://images.example$path"
}
