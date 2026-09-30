package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID

/**
 * nbformat 4 JSON shape for one notebook file. The one app-only field, the title,
 * lives under `metadata.jupydroid` so other tools can read the file unchanged;
 * cells keep their Jupyter shape (`cell_type`, `source`, `outputs`, `execution_count`).
 */
@Serializable
data class NotebookFile(
  val cells: List<CellFile> = emptyList(),
  val metadata: NotebookFileMetadata = NotebookFileMetadata(),
  val nbformat: Int = 4,
  @SerialName("nbformat_minor") val nbformatMinor: Int = 5,
)

@Serializable
data class NotebookFileMetadata(val kernelspec: KernelSpecMeta? = null, val jupydroid: JupyterDroidMeta? = null)

@Serializable
data class KernelSpecMeta(val name: String, @SerialName("display_name") val displayName: String)

/**
 * Only the title. There was an `updated_at` here until 2026-08-07, and it was the bug: nothing
 * bumped it on save, so an edited notebook kept the time it was created. Jupyter never stored
 * one - `last_modified` is the file's mtime - so the app stopped storing one too. Files written
 * before then still carry the key; `ignoreUnknownKeys` reads past it and the next save drops it.
 */
@Serializable
data class JupyterDroidMeta(val title: String)

@Serializable
data class CellFile(
  val id: String = "",
  @SerialName("cell_type") val cellType: String,
  @Serializable(with = MultiLineSourceSerializer::class)
  val source: String = "",
  val metadata: JsonObject = buildJsonObject { },
  val outputs: List<JsonElement> = emptyList(),
  @SerialName("execution_count") val executionCount: Int? = null,
)

/**
 * Jupyter writes `source` as either a string or an array of lines; accept both.
 *
 * The array is joined with **nothing**, because nbformat's lines already carry their own
 * trailing newlines - that is what its spec means by "each ending with a newline except
 * possibly the last", and what nbformat itself does (`"".join(source)`). Joining with "\n"
 * instead inserts a blank line between every line of every notebook Jupyter ever wrote.
 */
object MultiLineSourceSerializer : KSerializer<String> {
  override val descriptor: SerialDescriptor =
    PrimitiveSerialDescriptor("MultiLineSource", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: String) {
    encoder.encodeString(value)
  }

  override fun deserialize(decoder: Decoder): String =
    when (val element = JsonElement.serializer().deserialize(decoder)) {
      is JsonPrimitive -> element.content
      is JsonArray -> element.joinToString("") { it.jsonPrimitive.content }
      else ->
        throw IllegalArgumentException(
          "expected source as string or array of strings, got ${element::class.simpleName}",
        )
    }
}

/**
 * A notebook file into the domain, keeping the file's own JSON alongside.
 *
 * Parsed twice on purpose: once into [NotebookFile] for the fields the app models, and once as a
 * raw object that rides along on the domain types so a save can write *over* the file rather than
 * rebuild it from what the app happens to understand. See
 * [com.jupydroid.app.domain.model.PreservedJson].
 */
internal fun Json.readNotebook(file: File, id: String): Notebook {
  val root = parseToJsonElement(file.readText()) as? JsonObject
    ?: throw IllegalArgumentException("notebook file '$file' is not a JSON object")
  return decodeFromJsonElement(NotebookFile.serializer(), root).toDomain(id = id, preserved = root)
}

/**
 * [preserved] is the file's own JSON, threaded back onto the domain types so [toFileJson] can
 * write over it instead of rebuilding from what this app happens to model. Defaulted so tests
 * that only care about the modelled fields need not supply it; the repository always does.
 */
fun NotebookFile.toDomain(id: String, preserved: JsonObject = JsonObject(emptyMap())): Notebook {
  // The name *is* the file. There is no title inside a `.ipynb` in JupyterLab either - a document
  // is identified by its path, and `Context._updatePath` derives the displayed name with
  // `PathExt.basename(localPath)`. `metadata.jupydroid.title` is no longer read;
  // `NotebookWorkspaceMigration` moved every existing title onto its file name first.
  val title = id
  // By index, because a cell's id is optional in nbformat before 4.5 and duplicated ids happen.
  val preservedCells = preserved["cells"] as? JsonArray
  return Notebook(
    id = id,
    title = title,
    kernelId = metadata.kernelspec?.name ?: "python3",
    cells = cells.mapIndexed { at, cell -> cell.toDomain(preservedCells?.getOrNull(at) as? JsonObject) },
    preserved = preserved,
  )
}

fun CellFile.toDomain(preserved: JsonObject? = null): Cell = Cell(
  id = id.ifBlank { UUID.randomUUID().toString() },
  kind = cellKind(),
  source = source,
  outputs = outputs.mapNotNull { it.toDomainOutput() },
  executionCount = executionCount,
  sourceHidden = metadata.sourceHidden(),
  outputsHidden = metadata.outputsHidden(),
  headingCollapsed = metadata.headingCollapsed(),
  outputsScrolled = metadata.outputsScrolled(),
  preserved = preserved ?: JsonObject(emptyMap()),
)

/**
 * nbformat's `cell_type` as one of the three [CellKind]s, or a refusal naming what was found.
 *
 * Its own function because it is the one part of reading a cell that can fail, and the message has
 * to carry the offending value: a notebook holding a fourth type is a file this app cannot open,
 * and "unknown cell_type" without the value leaves nothing to act on.
 */
private fun CellFile.cellKind(): CellKind = when (cellType) {
  "code" -> CellKind.CODE
  "markdown" -> CellKind.MARKDOWN
  "raw" -> CellKind.RAW
  else ->
    throw IllegalArgumentException(
      "unknown cell_type '$cellType' in cell id '${id.ifBlank { "<missing>" }}'; " +
        "expected 'code', 'markdown' or 'raw'",
    )
}
