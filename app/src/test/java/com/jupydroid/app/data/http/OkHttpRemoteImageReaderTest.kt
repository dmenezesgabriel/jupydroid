package com.jupydroid.app.data.http

import com.jupydroid.app.domain.port.RemoteImageReader
import com.jupydroid.app.domain.port.RemoteImageReaderContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.Cache
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The contract against a real loopback server, and what only HTTP has: a chunked body, a cache, a dead host. */
class OkHttpRemoteImageReaderTest : RemoteImageReaderContract() {
  @get:Rule val serverRule = MockWebServerRule()

  @get:Rule val cacheFolder = TemporaryFolder()

  override fun reader(servedByPath: Map<String, ByteArray>): RemoteImageReader {
    serverRule.server.dispatcher = ImageHost(servedByPath)
    return readerUnderTest()
  }

  override fun urlOf(path: String): String = serverRule.server.url(path).toString()

  /** A chunked body declares no length, so the cap is held while it streams. */
  @Test
  fun aChunkedImageOverTheCapIsNothing() = runTest {
    serverRule.server.dispatcher = ImageHost(mapOf(CHUNKED_PATH to ByteArray(RemoteImageReader.MAX_IMAGE_BYTES + 1)))

    assertNull(readerUnderTest().imageBytes(urlOf(CHUNKED_PATH)))
  }

  @Test
  fun aSecondReadWithinItsFreshnessIsServedFromTheCache() = runTest {
    serverRule.server.dispatcher = ImageHost(mapOf("/logo.png" to LOGO))
    val reader = readerUnderTest()

    reader.imageBytes(urlOf("/logo.png"))
    assertArrayEquals(LOGO, reader.imageBytes(urlOf("/logo.png")))
    assertEquals(1, serverRule.server.requestCount)
  }

  /**
   * Wikimedia answers 403 to OkHttp's own `okhttp/x.y.z` and to no User-Agent at all, and 200 to one
   * naming its client (measured against upload.wikimedia.org; its User-Agent policy:
   * https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy).
   */
  @Test
  fun aReadNamesThisAppBeforeItsLibrary() = runTest {
    serverRule.server.dispatcher = ImageHost(mapOf("/logo.png" to LOGO))

    readerUnderTest().imageBytes(urlOf("/logo.png"))

    val userAgent = serverRule.server.takeRequest().headers["User-Agent"].orEmpty()
    assertTrue(userAgent, userAgent.matches(Regex("""Jupydroid/2\.3 okhttp/\d+\.\d+\.\d+""")))
  }

  @Test
  fun aHostThatCannotBeReachedIsNothing() = runTest {
    val gone = urlOf("/logo.png")
    serverRule.server.close()

    assertNull(readerUnderTest().imageBytes(gone))
  }

  private fun readerUnderTest() = OkHttpRemoteImageReader(
    OkHttpClient.Builder().cache(Cache(cacheFolder.root, CACHE_BYTES)).build(),
    appVersion = "2.3",
    ioDispatcher = Dispatchers.IO,
  )

  /** Serves each path's bytes, cacheable for a minute, chunked at [CHUNKED_PATH]; 404 otherwise. */
  private class ImageHost(private val servedByPath: Map<String, ByteArray>) : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
      val path = request.url.encodedPath
      val bytes = servedByPath[path] ?: return MockResponse.Builder().code(404).body("no image at $path").build()
      val response = MockResponse.Builder().code(200).addHeader("Content-Type", "image/png")
        .addHeader("Cache-Control", "max-age=60")
      val body = Buffer().write(bytes)
      return (if (path == CHUNKED_PATH) response.chunkedBody(body, CHUNK_BYTES) else response.body(body)).build()
    }
  }

  private companion object {
    val LOGO = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
    const val CACHE_BYTES = 16L * 1024 * 1024
    const val CHUNK_BYTES = 64 * 1024
    const val CHUNKED_PATH = "/chunked.png"
  }
}
