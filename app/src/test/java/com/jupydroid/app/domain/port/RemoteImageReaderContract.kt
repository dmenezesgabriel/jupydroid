package com.jupydroid.app.domain.port

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What every [RemoteImageReader] must do, whoever implements it. */
abstract class RemoteImageReaderContract {
  /** A reader over a host serving each path's bytes and nothing else. */
  protected abstract fun reader(servedByPath: Map<String, ByteArray>): RemoteImageReader

  /** The web address [path] is served at by the host [reader] reads. */
  protected abstract fun urlOf(path: String): String

  @Test
  fun aServedImageIsItsBytes() = runTest {
    val reader = reader(mapOf("/logo.png" to LOGO))

    assertArrayEquals(LOGO, reader.imageBytes(urlOf("/logo.png")))
  }

  @Test
  fun anAddressServingNothingIsNothing() = runTest {
    assertNull(reader(mapOf("/logo.png" to LOGO)).imageBytes(urlOf("/gone.png")))
  }

  @Test
  fun anImageAtTheCapIsItsBytes() = runTest {
    val atCap = ByteArray(RemoteImageReader.MAX_IMAGE_BYTES)

    assertArrayEquals(atCap, reader(mapOf("/big.png" to atCap)).imageBytes(urlOf("/big.png")))
  }

  @Test
  fun anImageOverTheCapIsNothing() = runTest {
    val overCap = ByteArray(RemoteImageReader.MAX_IMAGE_BYTES + 1)

    assertNull(reader(mapOf("/huge.png" to overCap)).imageBytes(urlOf("/huge.png")))
  }

  private companion object {
    val LOGO = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
  }
}
