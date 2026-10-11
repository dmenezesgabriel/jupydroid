package com.jupydroid.app.data.http

import com.jupydroid.app.domain.port.RemoteImageReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException

/**
 * A web image over OkHttp, through an HTTP cache, so a notebook reopened offline still draws what
 * it drew, for as long as the server said it stays fresh.
 *
 * The cap is held twice: a declared `Content-Length` over it is refused before the body is read,
 * and a body that declares none is read no further than one byte past it.
 *
 * Each request names this app, at [appVersion], before the library: upload.wikimedia.org answers
 * 403 to OkHttp's own `okhttp/x.y.z` and 200 to a User-Agent naming its client, as its policy asks
 * (https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy).
 *
 * ```
 * OkHttpRemoteImageReader(remoteImageClient(File(cacheDir, "remote-images")), "1.0").imageBytes(url)
 * ```
 */
class OkHttpRemoteImageReader(
  private val client: OkHttpClient,
  appVersion: String,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RemoteImageReader {
  private val userAgent = "Jupydroid/$appVersion okhttp/${OkHttp.VERSION}"

  override suspend fun imageBytes(url: String): ByteArray? = withContext(ioDispatcher) {
    val request = runCatching { Request.Builder().url(url).header("User-Agent", userAgent).build() }.getOrNull()
      ?: return@withContext null
    try {
      client.newCall(request).execute().use { response ->
        if (response.isSuccessful) response.body.cappedBytes() else null
      }
    } catch (_: IOException) {
      null
    }
  }

  private fun ResponseBody.cappedBytes(): ByteArray? {
    if (contentLength() > RemoteImageReader.MAX_IMAGE_BYTES) return null
    val source = source()
    source.request(RemoteImageReader.MAX_IMAGE_BYTES + 1L)
    return source.buffer.takeIf { it.size <= RemoteImageReader.MAX_IMAGE_BYTES }?.readByteArray()
  }
}

/**
 * The client [OkHttpRemoteImageReader] reads through, caching in [cacheDirectory] - under the
 * app's `cacheDir`, which the platform may clear when storage runs low, as a cache should be.
 *
 * ```
 * remoteImageClient(File(context.cacheDir, "remote-images"))
 * ```
 */
fun remoteImageClient(cacheDirectory: File): OkHttpClient =
  OkHttpClient.Builder().cache(Cache(cacheDirectory, REMOTE_IMAGE_CACHE_BYTES)).build()

/** Ten images at the cap (ADR-0087). */
private const val REMOTE_IMAGE_CACHE_BYTES = 10L * RemoteImageReader.MAX_IMAGE_BYTES
