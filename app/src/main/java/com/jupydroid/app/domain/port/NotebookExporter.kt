package com.jupydroid.app.domain.port

/**
 * One of the formats the Space's nbconvert offers, as `GET /api/nbconvert` names it.
 *
 * The [name] is the id the export URL is built from, not a label - JupyterLab keeps its own
 * labels for the seven it shows (`packages/notebook-extension/src/index.ts:5550-5558`), because
 * `rst` reads as *"ReStructured Text"* and nothing on the wire says so.
 */
data class ExportFormat(val name: String, val outputMimeType: String)

/**
 * A converted notebook, exactly as the server handed it back.
 *
 * **All three fields come off the response and none is derived from [ExportFormat.name]**, and
 * that is a measurement rather than caution: exporting a notebook with an image output to
 * `markdown` returns a **zip** named `<stem>.zip`, because a markdown export writes each image
 * beside the document and `respond_zip` then packs both
 * (`jupyter_server/nbconvert/handlers.py:38-65`). The same notebook to `html` is not a zip, since
 * that exporter inlines images as data URIs. So the shape depends on the format *and* on the
 * notebook, and only the headers know. Measured on the tablet:
 * `evidence/p2j-2-export-formats/`.
 */
data class ExportedDocument(val fileName: String, val mediaType: String, val bytes: ByteArray) {
  // ByteArray identity would make two identical exports unequal, which is not what a test of an
  // export means - the same reading `RenderedOutput.Image` already made.
  override fun equals(other: Any?): Boolean =
    other is ExportedDocument &&
      fileName == other.fileName &&
      mediaType == other.mediaType &&
      bytes.contentEquals(other.bytes)

  override fun hashCode(): Int = 31 * (31 * fileName.hashCode() + mediaType.hashCode()) + bytes.contentHashCode()
}

/**
 * An export the server refused, carrying what it said about it.
 *
 * [format] is null when the refusal came before a format was chosen - [NotebookExporter.formats]
 * failing is still a failure to export, and there is nothing to name yet.
 */
class NotebookExportException(val format: String?, reason: String) :
  IllegalStateException(if (format == null) reason else "cannot export to '$format': $reason")

/**
 * `notebook:export-to-format`, over the server's nbconvert endpoints.
 *
 * A port rather than a call site because conversion is a **server-side algorithm** and this app
 * may not re-implement one (`docs/DECISIONS.md`): nbconvert runs in the Space, and what reaches
 * Kotlin is bytes and a filename.
 *
 * ```
 * val html = exporter.export("reports/Analysis.ipynb", "html")   // -> Analysis.html
 * ```
 */
interface NotebookExporter {
  /**
   * Every format the server says it has, unfiltered.
   *
   * **Unfiltered on purpose, and it is not the list to put in a menu.** `get_export_names` keeps
   * an exporter if it *constructs* (`nbconvert/exporters/base.py`), and constructing is not
   * converting: on the shipped rootfs fourteen are listed and six convert, the rest failing on a
   * pandoc, playwright or PyQtWebEngine that is not there. Deciding what to offer is the caller's,
   * and this reports what is there.
   */
  suspend fun formats(): List<ExportFormat>

  /**
   * Converts the notebook at [documentPath] - relative to the workspace, which is the server's
   * own `root_dir` - to [format], or throws [NotebookExportException].
   */
  suspend fun export(documentPath: String, format: String): ExportedDocument
}
