package com.jupydroid.app.domain.port

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [NotebookExporter] must do, whoever implements it.
 *
 * The shapes are the ones the tablet actually returned (`evidence/p2j-2-export-formats/`), so an
 * implementation that passes this has agreed with the device rather than with a guess.
 */
abstract class NotebookExporterContract {
  /**
   * An exporter over the server each case describes: what `GET /api/nbconvert` lists, what
   * converts, and what it refuses and with what message.
   *
   * The fake answers from these directly; the OkHttp adapter scripts a `MockWebServer` with them.
   */
  protected abstract fun exporter(
    formats: Map<String, String> = emptyMap(),
    converted: Map<String, ExportedDocument> = emptyMap(),
    refusals: Map<String, String> = emptyMap(),
  ): NotebookExporter

  @Test
  fun formats_areTheOnesTheServerNames() = runTest {
    val exporter = exporter(formats = mapOf("html" to "text/html", "markdown" to "text/markdown"))

    assertEquals(
      listOf(ExportFormat("html", "text/html"), ExportFormat("markdown", "text/markdown")),
      exporter.formats().sortedBy { it.name },
    )
  }

  /**
   * The server lists exporters that cannot convert - four of the fourteen on this rootfs fail on
   * a pandoc that is not installed. Filtering is the caller's decision, not the port's.
   */
  @Test
  fun formats_includeOnesThatWillNotConvert() = runTest {
    val exporter = exporter(formats = mapOf("html" to "text/html", "rst" to "text/restructuredtext"))

    assertTrue("rst is listed even though it cannot convert", exporter.formats().any { it.name == "rst" })
  }

  @Test
  fun export_returnsWhatTheServerConverted() = runTest {
    val exporter = exporter(converted = mapOf("html" to CONVERTED_HTML))

    assertEquals(CONVERTED_HTML, exporter.export("Analysis.ipynb", "html"))
  }

  /** A markdown export of a notebook with an image is a zip, and the name and type say so. */
  @Test
  fun export_carriesTheNameAndTypeTheServerChoseRatherThanOneDerivedFromTheFormat() = runTest {
    val exporter = exporter(converted = mapOf("markdown" to CONVERTED_ZIP))

    val exported = exporter.export("Analysis.ipynb", "markdown")

    assertEquals("Analysis.zip", exported.fileName)
    assertEquals("application/zip", exported.mediaType)
  }

  @Test
  fun export_failsWithWhatTheServerSaidAboutTheFormatItWasAsked() = runTest {
    val exporter = exporter(refusals = mapOf("rst" to "nbconvert failed: Pandoc wasn't found."))

    val failure = runCatching { exporter.export("Analysis.ipynb", "rst") }.exceptionOrNull()

    assertTrue("expected a NotebookExportException, got $failure", failure is NotebookExportException)
    assertEquals("rst", (failure as NotebookExportException).format)
    assertTrue("expected the server's own words in '${failure.message}'", failure.message.orEmpty().contains("Pandoc"))
  }

  /**
   * A Space with no runtime at all still has to answer, because the app has one on every device -
   * an x86_64 emulator has no rootfs and the fakes are what it runs on.
   */
  @Test
  fun formats_answerEvenWhenTheSpaceOffersNothing() = runTest {
    assertEquals(emptyList<ExportFormat>(), exporter().formats())
  }

  protected companion object {
    val CONVERTED_HTML = ExportedDocument("Analysis.html", "text/html", "<html>ok</html>".toByteArray())
    val CONVERTED_ZIP = ExportedDocument("Analysis.zip", "application/zip", byteArrayOf(0x50, 0x4B, 0x03, 0x04))
  }
}
