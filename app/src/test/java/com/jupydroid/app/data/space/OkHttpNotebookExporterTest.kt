package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ExportedDocument
import com.jupydroid.app.domain.port.NotebookExportException
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.NotebookExporterContract
import com.jupydroid.app.domain.port.ServerConnection
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The contract, driven against a real loopback server scripted to answer the way the tablet's did.
 *
 * Every header form here is one `evidence/p2j-2-export-formats/bodies/` holds: the RFC 5987
 * `Content-Disposition`, the charset on the `Content-Type`, the zip for `markdown`, and tornado's
 * error page for the four that fail on pandoc.
 */
class OkHttpNotebookExporterTest : NotebookExporterContract() {
  @get:Rule val serverRule = MockWebServerRule()

  override fun exporter(
    formats: Map<String, String>,
    converted: Map<String, ExportedDocument>,
    refusals: Map<String, String>,
  ): NotebookExporter {
    serverRule.server.dispatcher = NbConvertLikeTheTabletsServer(formats, converted, refusals)
    return exporterUnderTest()
  }

  /**
   * The path's segments are encoded and its slashes are kept, which is what `URLExt.encodeParts`
   * does in the reference - a notebook in a subfolder is one path, not one segment. X1b makes a
   * title the file name, so the space is the case that matters.
   */
  @Test
  fun export_asksForTheNotebookByItsPathWithTheSegmentsEncoded() = runTest {
    serverRule.server.dispatcher = NbConvertLikeTheTabletsServer(converted = mapOf("html" to CONVERTED_HTML))

    exporterUnderTest().export("reports/Quarterly Report.ipynb", "html")

    val request = serverRule.server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!
    assertEquals("GET", request.method)
    assertEquals("/nbconvert/html/reports/Quarterly%20Report.ipynb?download=true", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  /** `?download=true` is what makes the server send the `Content-Disposition` at all. */
  @Test
  fun export_asksForTheDownloadFormBecauseThatIsWhatNamesTheFile() = runTest {
    serverRule.server.dispatcher = NbConvertLikeTheTabletsServer(converted = mapOf("html" to CONVERTED_HTML))

    exporterUnderTest().export("Analysis.ipynb", "html")

    val target = serverRule.server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!.target
    assertEquals(true, target.endsWith("?download=true"))
  }

  @Test
  fun formats_asksTheEndpointTheReferenceAsks() = runTest {
    serverRule.server.dispatcher = NbConvertLikeTheTabletsServer(formats = mapOf("html" to "text/html"))

    exporterUnderTest().formats()

    assertEquals("/api/nbconvert", serverRule.server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!.target)
  }

  /**
   * A 200 that named no file is a server behaving unlike the one this app ships, and saying so is
   * better than inventing a name: `respond_zip` sets the header unconditionally and `download=true`
   * sets it otherwise, so there is no legitimate way to get here.
   */
  @Test
  fun export_refusesAConversionTheServerNamedNoFileFor() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = "<html>ok</html>"))

    val failure = runCatching { exporterUnderTest().export("Analysis.ipynb", "html") }

    assertEquals(true, failure.exceptionOrNull()?.message?.contains("named no file"))
  }

  /**
   * A 200 carrying something that is not the listing - a captive portal, a proxy, a server that
   * has moved on. It is still this port's failure, and its own exception is what has to say so.
   */
  @Test
  fun formats_refuseAnAnswerThatIsNotAFormatListRatherThanLettingTheParserThrough() = runTest {
    serverRule.server.enqueue(MockResponse(code = 200, body = "<html>not the listing</html>"))

    val failure = runCatching { exporterUnderTest().formats() }.exceptionOrNull()

    assertEquals(true, failure is NotebookExportException)
    assertEquals(true, failure?.message?.contains("did not answer a format list"))
  }

  private fun exporterUnderTest() = OkHttpNotebookExporter(
    connection = ServerConnection(
      baseUrl = serverRule.server.url("/").toString(),
      port = serverRule.server.port,
      token = "secret-token",
    ),
  )

  private companion object {
    const val TIMEOUT_SECONDS = 5L
  }
}

/**
 * A `jupyter_server` answering nbconvert's two endpoints the way the tablet's did.
 *
 * A dispatcher rather than a queue of responses, because the contract's cases ask in whatever
 * order they need to and one of them asks twice.
 */
private class NbConvertLikeTheTabletsServer(
  private val formats: Map<String, String> = emptyMap(),
  private val converted: Map<String, ExportedDocument> = emptyMap(),
  private val refusals: Map<String, String> = emptyMap(),
) : Dispatcher() {
  override fun dispatch(request: RecordedRequest): MockResponse {
    val target = request.target.substringBefore('?')
    if (target == "/api/nbconvert") return listing()
    val format = target.removePrefix("/nbconvert/").substringBefore('/')
    return refusals[format]?.let(::tornadoErrorPage)
      ?: converted[format]?.let(::attachment)
      ?: tornadoErrorPage("No exporter for format: $format")
  }

  private fun listing() = MockResponse(
    code = 200,
    headers = okhttp3.Headers.headersOf("Content-Type", "application/json"),
    body = formats.entries.joinToString(",", "{", "}") { """"${it.key}":{"output_mimetype":"${it.value}"}""" },
  )

  /** The RFC 5987 form and the charset, exactly as the shipped tornado sends them. */
  private fun attachment(document: ExportedDocument) = MockResponse.Builder()
    .code(200)
    .addHeader("Content-Type", "${document.mediaType}; charset=utf-8")
    .addHeader("Content-Disposition", "attachment; filename*=utf-8''${document.fileName.replace(" ", "%20")}")
    .body(Buffer().write(document.bytes))
    .build()

  /** What a failed conversion actually looks like: HTML, with the sentence escaped in a `<pre>`. */
  private fun tornadoErrorPage(message: String) = MockResponse(
    code = 500,
    headers = okhttp3.Headers.headersOf("Content-Type", "text/html"),
    body = "<html><body><h1>500 : Internal Server Error</h1><pre>${message.replace("'", "&#39;")}</pre></body></html>",
  )
}
