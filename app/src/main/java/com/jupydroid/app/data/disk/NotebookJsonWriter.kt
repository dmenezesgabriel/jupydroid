package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_DISPLAY_NAME
import com.jupydroid.app.domain.model.Notebook
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A notebook back into nbformat JSON, as an **overlay on what was read** rather than a rebuild.
 *
 * That is the whole design. nbformat is open - every interface in JupyterLab's
 * `packages/nbformat/src/index.ts` extends `PartialJSONObject` - so a writer that emits only the
 * keys this app models silently destroys `tags`, `slideshow`, `attachments`, an output's own
 * metadata and every object-valued mime payload. Each preserved object is laid down first and
 * the fields the app owns are written over it; everything else survives untouched.
 *
 * ```
 * json.encodeToString(JsonObject.serializer(), notebook.toFileJson())
 * ```
 */
fun Notebook.toFileJson(): JsonObject = buildJsonObject {
  preserved.forEach { (key, value) -> put(key, value) }
  put("cells", JsonArray(cells.map { it.toCellJson() }))
  put("metadata", notebookMetadataJson())
  put("nbformat", NBFORMAT_MAJOR)
  // Forced rather than preserved: cell ids are a 4.5 feature and this writer always writes them,
  // so claiming an older minor would describe a file we are not producing.
  put("nbformat_minor", NBFORMAT_MINOR)
}

private fun Notebook.notebookMetadataJson(): JsonObject = buildJsonObject {
  // Dropped rather than carried over: this app owns the `jupydroid` namespace and no longer
  // writes anything into it, so a key from an older build must not linger. It held the title
  // until X1b, and a file that kept one would be claiming a name its own filename contradicts -
  // which is precisely the split Phase X exists to close. `updated_at` went the same way in A6.
  preserved.objectAt("metadata")
    .filterKeys { it != JUPYDROID_METADATA }
    .forEach { (key, value) -> put(key, value) }
  put("kernelspec", kernelspecJson())
}

/**
 * Only `name` is ours - it is where [Notebook.kernelId] was read from. `display_name` is kept if
 * the file had one, because overwriting someone's "Python 3.11 (conda)" with our default would
 * be the same loss in miniature.
 */
private fun Notebook.kernelspecJson(): JsonObject = buildJsonObject {
  val preservedSpec = preserved.objectAt("metadata").objectAt("kernelspec")
  preservedSpec.forEach { (key, value) -> put(key, value) }
  put("name", kernelId)
  if ("display_name" !in preservedSpec) put("display_name", DEFAULT_KERNEL_DISPLAY_NAME)
}

/**
 * Keys the app owns are excluded from the copy rather than overwritten after it, because some
 * of them must end up *absent*: an un-collapsed cell has no `collapsed` key at all, and a
 * markdown cell has no `outputs`, so a stale value copied first could survive.
 */
private fun Cell.toCellJson(): JsonObject = buildJsonObject {
  preserved.forEach { (key, value) -> if (key !in OWNED_CELL_KEYS) put(key, value) }
  put("id", id)
  put("cell_type", kind.cellType())
  put("source", source)
  put("metadata", metadataJson())
  // nbformat requires both on a code cell and **forbids** both on the other two: `markdown_cell`
  // and `raw_cell` are each `additionalProperties: false` over a list that has neither (shipped
  // rootfs, `nbformat/v4/nbformat.v4.schema.json`). So neither is omitted for being empty and
  // neither survives a cell changing kind.
  if (kind == CellKind.CODE) {
    put("outputs", outputsJson())
    put("execution_count", executionCount?.let { JsonPrimitive(it) } ?: JsonNull)
  }
}

/** nbformat's own spelling of each kind - the value of `cell_type`. */
private fun CellKind.cellType(): String = when (this) {
  CellKind.CODE -> "code"
  CellKind.MARKDOWN -> "markdown"
  CellKind.RAW -> "raw"
}

private fun Cell.metadataJson(): JsonObject = buildJsonObject {
  preserved.objectAt("metadata").forEach { (key, value) -> if (key !in OWNED_METADATA_KEYS) put(key, value) }
  jupyterNamespace()?.let { put("jupyter", it) }
  if (outputsHidden) put("collapsed", true)
  if (headingCollapsed) put(HEADING_COLLAPSED_KEY, true)
  if (outputsScrolled) put("scrolled", true)
}

/**
 * The `jupyter` namespace with only `source_hidden` ours, so another tool's keys inside it -
 * `outputs_hidden` among them - are neither read nor evicted. Null when nothing is left, which
 * is what JupyterLab does: it deletes the object once emptied (`widget.ts:487-491`).
 */
private fun Cell.jupyterNamespace(): JsonObject? {
  val namespace = buildJsonObject {
    preserved.objectAt("metadata").objectAt("jupyter")
      .forEach { (key, value) -> if (key != "source_hidden") put(key, value) }
    if (sourceHidden) put("source_hidden", true)
  }
  return namespace.takeIf { it.isNotEmpty() }
}

/**
 * Outputs the app did not change are written back **byte for byte**. Regenerating them from
 * [com.jupydroid.app.domain.model.CellOutput] would drop each output's own metadata and flatten
 * any object-valued mime payload, which is how a jupyter-widget view is stored.
 */
private fun Cell.outputsJson(): JsonArray {
  val asRead = preserved["outputs"] as? JsonArray
  if (asRead != null && asRead.mapNotNull { it.toDomainOutput() } == outputs) return asRead
  return JsonArray(outputs.map { it.toFileOutput() })
}

private fun JsonObject.objectAt(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())

private val OWNED_CELL_KEYS =
  setOf("id", "cell_type", "source", "metadata", "outputs", "execution_count")

private val OWNED_METADATA_KEYS = setOf("jupyter", "collapsed", HEADING_COLLAPSED_KEY, "scrolled")

private const val NBFORMAT_MAJOR = 4
private const val NBFORMAT_MINOR = 5

/** The namespace this app used to keep its own fields in. Nothing writes it now; writes strip it. */
private const val JUPYDROID_METADATA = "jupydroid"
