package com.jupydroid.app.data.http

import com.jupydroid.app.domain.model.PackageReleases
import com.jupydroid.app.domain.model.normalizedPackageName
import com.jupydroid.app.domain.port.WheelIndex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.IOException

/**
 * A package's own index on PyPI, over OkHttp - the release files a miss is explained from, because
 * pip's text says the same thing for a name that exists nowhere (ADR-0165).
 *
 * A name PyPI does not have is a 404 and a host that cannot be reached is an `IOException`, and both
 * are the same `null`: the answer that says nothing rather than one the cell would have to carry.
 *
 * ```
 * PyPiWheelIndex(OkHttpClient()).releasesOf("duckdb")?.latestVersion   // "2.0.1"
 * ```
 */
class PyPiWheelIndex(
  private val client: OkHttpClient = OkHttpClient(),
  private val baseUrl: String = PYPI_INDEX_URL,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WheelIndex {
  override suspend fun releasesOf(packageName: String): PackageReleases? = withContext(ioDispatcher) {
    val request = runCatching {
      Request.Builder().url("$baseUrl/${normalizedPackageName(packageName)}/json").build()
    }.getOrNull() ?: return@withContext null
    try {
      client.newCall(request).execute().use { response ->
        if (response.isSuccessful) response.body.indexOrNull() else null
      }
    } catch (_: IOException) {
      null
    }
  }
}

/** PyPI's JSON API, per package: `https://pypi.org/pypi/<name>/json`. */
private const val PYPI_INDEX_URL = "https://pypi.org/pypi"

/**
 * The package this body lists, or null when it is not that document - a gateway's HTML, a truncated
 * body, or one over [WheelIndex.MAX_INDEX_BYTES], which no real index comes near.
 */
private fun ResponseBody.indexOrNull(): PackageReleases? {
  val (latest, releases) = cappedDocument()?.latestAndReleases() ?: return null
  val wheels = releases.mapValues { (_, files) -> files.wheelNames() }
  // A version whose files PyPI lists under no key would otherwise read as "ships no wheel at all".
  return PackageReleases(latest, wheels + (latest to wheels[latest].orEmpty()))
}

/** The body as JSON, or null when it is not JSON or is longer than an index could be. */
private fun ResponseBody.cappedDocument(): JsonObject? {
  val source = source()
  source.request(WheelIndex.MAX_INDEX_BYTES + 1L)
  if (source.buffer.size > WheelIndex.MAX_INDEX_BYTES) return null
  return runCatching { Json.parseToJsonElement(source.buffer.readUtf8()) }.getOrNull() as? JsonObject
}

/** The version PyPI calls latest, and the files of every release it lists. */
private fun JsonObject.latestAndReleases(): Pair<String, JsonObject>? {
  val latest = (get("info") as? JsonObject)?.get("version").stringOrNull() ?: return null
  return (get("releases") as? JsonObject)?.let { latest to it }
}

private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** The wheel filenames one release published; an sdist is a file a Space's pip cannot install. */
private fun JsonElement.wheelNames(): List<String> {
  val files = this as? JsonArray ?: return emptyList()
  return files.mapNotNull { file ->
    (file as? JsonObject)?.get("filename").stringOrNull()
  }.filter { it.endsWith(".whl") }
}
