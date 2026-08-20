package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ExportFormat
import com.jupydroid.app.domain.port.ExportedDocument
import com.jupydroid.app.domain.port.NotebookExportException
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.ServerConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * `notebook:export-to-format` over the two endpoints `jupyter_server` registers for nbconvert
 * (`serverapp.py:197-199`).
 *
 * ```
 * OkHttpNotebookExporter(connection).export("reports/Analysis.ipynb", "html")
 * ```
 *
 * **Both are `GET`s, and token auth is enough.** `NbconvertFileHandler.get` calls
 * `check_xsrf_cookie`, which returns early for a token-authenticated request
 * (`jupyter_server/base/handlers.py:530-538`) - the same `Authorization: token …` header
 * [OkHttpKernelLifecycle] already sends.
 *
 * **A conversion is slower than OkHttp's default read timeout allows for.** nbconvert runs in the
 * Space, on a tablet, and a `pdf` or `webpdf` attempt does not fail until its missing tool has
 * been looked for; ten seconds is the wrong budget for that, and the failure would surface as a
 * socket timeout naming nothing. [EXPORT_TIMEOUT] is the default client's instead - matching the
 * `--max-time 120` the probe driver uses against the same endpoint.
 */
class OkHttpNotebookExporter(
  private val connection: ServerConnection,
  private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(EXPORT_TIMEOUT.toJavaDuration()).build(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NotebookExporter {
  /**
   * What the server says it has - **all of it**, including the exporters that cannot convert.
   * Filtering belongs to whoever draws the menu; see [NotebookExporter.formats].
   */
  override suspend fun formats(): List<ExportFormat> {
    val body = get(connection.baseUrl.toHttpUrl().newBuilder().addPathSegments(FORMATS_PATH).build())
      .orFailed(format = null, asked = FORMATS_PATH)
      .text()
    // A 200 that is not the listing is still a failure of this port, and it is the port's own
    // exception that says so - a `SerializationException` escaping here would name kotlinx rather
    // than the server, and the caller has nothing to do with either.
    return runCatching { Json.parseToJsonElement(body).jsonObject }
      .getOrElse {
        throw NotebookExportException(
          format = null,
          reason = "$FORMATS_PATH did not answer a format list: ${body.take(LISTING_EXCERPT)}",
        )
      }
      .map { (name, described) ->
        ExportFormat(
          name = name,
          outputMimeType = described.jsonObject[OUTPUT_MIMETYPE]?.jsonPrimitive?.content.orEmpty(),
        )
      }
  }

  override suspend fun export(documentPath: String, format: String): ExportedDocument {
    val answer = get(exportUrl(documentPath, format)).orFailed(format, asked = documentPath)
    return ExportedDocument(
      // A missing name is loud rather than guessed: `?download=true` is what makes the server
      // set the header, and `respond_zip` sets it unconditionally, so a 200 without one is a
      // server that does not behave like the one this app ships.
      fileName = NbConvertResponse.fileName(answer.contentDisposition)
        ?: throw NotebookExportException(format, "the server converted '$documentPath' but named no file"),
      mediaType = NbConvertResponse.mediaType(answer.contentType) ?: OCTET_STREAM,
      bytes = answer.bytes,
    )
  }

  /**
   * `nbconvert/<format>/<path>?download=true`, with the path's segments encoded and its slashes
   * kept - which is what the reference's `URLExt.encodeParts` does and what a notebook in a
   * subfolder needs. `download=true` is what asks for the `Content-Disposition` at all.
   */
  private fun exportUrl(documentPath: String, format: String): HttpUrl =
    connection.baseUrl.toHttpUrl().newBuilder()
      .addPathSegment(EXPORT_PATH)
      .addPathSegment(format)
      .addPathSegments(documentPath.trim('/'))
      .addQueryParameter("download", "true")
      .build()

  private suspend fun get(url: HttpUrl): NbConvertAnswer = withContext(ioDispatcher) {
    val request = Request.Builder()
      .url(url)
      .header("Authorization", "token ${connection.token}")
      .build()
    client.newCall(request).execute().use { response ->
      NbConvertAnswer(
        code = response.code,
        bytes = response.body?.bytes() ?: ByteArray(0),
        contentType = response.header("Content-Type"),
        contentDisposition = response.header("Content-Disposition"),
      )
    }
  }

  /** The answer itself when the server converted, and nbconvert's own words when it did not. */
  private fun NbConvertAnswer.orFailed(format: String?, asked: String): NbConvertAnswer {
    if (code in SUCCESS) return this
    throw NotebookExportException(format, "'$asked' failed: HTTP $code ${NbConvertResponse.failureMessage(text())}")
  }

  private companion object {
    const val FORMATS_PATH = "api/nbconvert"
    const val EXPORT_PATH = "nbconvert"
    const val OUTPUT_MIMETYPE = "output_mimetype"
    const val OCTET_STREAM = "application/octet-stream"

    /** Enough of an unexpected answer to recognise it by, and not a page pasted into a snackbar. */
    const val LISTING_EXCERPT = 200
    val SUCCESS = 200..299
  }
}

/** Long enough for the slowest conversion the tablet measured, and for the slowest refusal. */
private val EXPORT_TIMEOUT = 120.seconds

/** One nbconvert response, read whole - the body is bytes because it may be a zip. */
private data class NbConvertAnswer(
  val code: Int,
  val bytes: ByteArray,
  val contentType: String?,
  val contentDisposition: String?,
) {
  fun text(): String = bytes.toString(Charsets.UTF_8)

  override fun equals(other: Any?): Boolean =
    other is NbConvertAnswer &&
      code == other.code &&
      bytes.contentEquals(other.bytes) &&
      contentType == other.contentType &&
      contentDisposition == other.contentDisposition

  override fun hashCode(): Int = 31 * (31 * code + bytes.contentHashCode()) + contentType.hashCode()
}
