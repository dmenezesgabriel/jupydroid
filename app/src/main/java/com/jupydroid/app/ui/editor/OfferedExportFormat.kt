package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.port.ExportFormat

/** One row of the export picker: what to ask the server for, and what to call it on screen. */
data class OfferedExportFormat(val id: String, val label: String)

/**
 * The formats this app offers, in the order the reference's own menu builds them and under the
 * labels it gives them (`Private.getFormatLabels`,
 * `packages/notebook-extension/src/index.ts:5550-5558`).
 *
 * **Four rows out of the fourteen the server lists, and every departure was measured** on the
 * tablet against the shipped rootfs:
 *
 * - `asciidoc`, `latex`, `pdf` and `rst` are gone because they cannot work: each routes every
 *   markdown cell through `convert_pandoc` and there is no pandoc in the rootfs. `webpdf` wants
 *   playwright and `qtpdf`/`qtpng` want PyQtWebEngine, none of which are there either. The
 *   reference keeps all of them and lets a conversion fail; a row that can only fail is a control
 *   that lies, so this app does not draw one.
 * - `notebook` and `custom` are the reference's own exclusions (`FORMAT_EXCLUDE`, `:375`), and
 *   `custom` cannot construct without a template anyway.
 * - **`python` is offered where the reference offers `script`**, which is this list's one real
 *   departure. `ScriptExporter` picks its language and its extension out of
 *   `nb.metadata.language_info` (`nbconvert/exporters/script.py:52-60`), and this app writes
 *   `kernelspec` alone - so on the device `script` answered `text/plain`, named `.txt`, holding
 *   the cell sources concatenated with none of `python`'s `# In[1]` structure. The reference
 *   excludes `python` only because its own `script` already dispatches to it. Serving the
 *   *"Executable Script"* row with the exporter that actually produces one here is the adaptation;
 *   writing `language_info` is the fidelity fix and is not this step's.
 */
internal val OFFERED_EXPORT_FORMATS = listOf(
  OfferedExportFormat("html", "HTML"),
  OfferedExportFormat("markdown", "Markdown"),
  OfferedExportFormat("python", "Executable Script"),
  OfferedExportFormat("slides", "Reveal.js Slides"),
)

/**
 * The rows to draw for a server that lists [available].
 *
 * The intersection rather than either list alone: [OFFERED_EXPORT_FORMATS] is what this app has a
 * label and a measurement for, and [available] is what the Space in front of the user actually
 * has. A Space that lost an exporter loses its row rather than offering a button that 500s, and
 * one that gained pandoc still gains nothing until this list says so - the labels and the
 * measurement come together.
 *
 * ```
 * offeredExportFormats(exporter.formats())   // [HTML, Markdown, Executable Script, Reveal.js Slides]
 * ```
 */
internal fun offeredExportFormats(available: List<ExportFormat>): List<OfferedExportFormat> {
  val names = available.mapTo(mutableSetOf()) { it.name }
  return OFFERED_EXPORT_FORMATS.filter { it.id in names }
}
