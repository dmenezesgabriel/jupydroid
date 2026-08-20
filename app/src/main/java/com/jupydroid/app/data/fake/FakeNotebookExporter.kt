package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.ExportFormat
import com.jupydroid.app.domain.port.ExportedDocument
import com.jupydroid.app.domain.port.NotebookExportException
import com.jupydroid.app.domain.port.NotebookExporter

/**
 * A [NotebookExporter] that converts nothing and answers like the real Space does.
 *
 * **Its defaults are the tablet's own measurement**, not an invention: the six formats
 * `GET /api/nbconvert` listed *and converted* on the shipped rootfs, with the mime types it
 * reported (`evidence/p2j-2-export-formats/`). So an x86_64 emulator - which has no rootfs at all
 * and falls back to the fakes - draws the same menu with the same rows as the tablet, and a flow
 * about the *menu* is worth running there.
 *
 * What it cannot do is convert, so [export] returns a stub naming what it was asked for. A flow
 * asserting an exported file's *contents* is a tablet flow, the way a cell-running one is.
 *
 * ```
 * FakeNotebookExporter(refusals = mapOf("rst" to "Pandoc wasn't found."))
 * ```
 */
class FakeNotebookExporter(
  private val formats: Map<String, String> = CONVERTIBLE_ON_THE_SHIPPED_ROOTFS,
  /** Conversions to answer verbatim; anything else gets [stub]. */
  private val converted: Map<String, ExportedDocument> = emptyMap(),
  /** Formats to refuse, and what the server would have said about each. */
  private val refusals: Map<String, String> = emptyMap(),
) : NotebookExporter {
  override suspend fun formats(): List<ExportFormat> = formats.map { (name, mime) -> ExportFormat(name, mime) }

  override suspend fun export(documentPath: String, format: String): ExportedDocument {
    refusals[format]?.let { throw NotebookExportException(format, it) }
    return converted[format] ?: stub(documentPath, format)
  }

  /**
   * Something of the right shape: named for the document and the format, typed as the format's
   * own mime, and holding text that says where it came from rather than a converted notebook.
   */
  private fun stub(documentPath: String, format: String): ExportedDocument {
    val stem = documentPath.substringAfterLast('/').substringBeforeLast('.')
    return ExportedDocument(
      fileName = "$stem.${EXTENSIONS[format] ?: format}",
      mediaType = formats[format] ?: "text/plain",
      bytes = "$stem exported to $format by FakeNotebookExporter\n".toByteArray(),
    )
  }

  private companion object {
    /**
     * The six of fourteen that answered 200 on the tablet, with the mime types the server sent.
     * The other eight failed on a pandoc, playwright or PyQtWebEngine the rootfs does not carry.
     */
    val CONVERTIBLE_ON_THE_SHIPPED_ROOTFS = mapOf(
      "html" to "text/html",
      "markdown" to "text/markdown",
      "notebook" to "application/json",
      "python" to "text/x-python",
      "script" to "text/plain",
      "slides" to "text/html",
    )

    /** What the server named each one, so the fake's file lands where the real one would. */
    val EXTENSIONS = mapOf(
      "html" to "html",
      "markdown" to "md",
      "notebook" to "ipynb",
      "python" to "py",
      "script" to "txt",
      "slides" to "slides.html",
    )
  }
}
