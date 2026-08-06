package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.NotebookSummary
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * nbformat 4 JSON shape for one notebook file. App-only fields (title, updatedAt)
 * live under `metadata.jupydroid` so other tools can read the file unchanged;
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
data class NotebookFileMetadata(
  val kernelspec: KernelSpecMeta? = null,
  val jupydroid: JupyterDroidMeta? = null,
)

@Serializable
data class KernelSpecMeta(
  val name: String,
  @SerialName("display_name") val displayName: String,
)

@Serializable
data class JupyterDroidMeta(
  val title: String,
  @SerialName("updated_at") val updatedAt: String,
)

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

fun NotebookFile.toDomain(id: String, updatedAtFallback: Instant): Notebook {
  val appMeta = metadata.jupydroid
  val title = appMeta?.title?.takeIf { it.isNotBlank() } ?: id
  val updatedAt =
    appMeta?.updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: updatedAtFallback
  return Notebook(
    id = id,
    title = title,
    kernelId = metadata.kernelspec?.name ?: "python3",
    cells = cells.map { it.toDomain() },
    updatedAt = updatedAt,
  )
}

fun CellFile.toDomain(): Cell =
  Cell(
    id = id.ifBlank { UUID.randomUUID().toString() },
    kind =
      when (cellType) {
        "code" -> CellKind.CODE
        "markdown" -> CellKind.MARKDOWN
        else ->
          throw IllegalArgumentException(
            "unknown cell_type '$cellType' in cell id '${id.ifBlank { "<missing>" }}'; " +
              "expected 'code' or 'markdown'",
          )
      },
    source = source,
    outputs = outputs.mapNotNull { it.toDomainOutput() },
    executionCount = executionCount,
    sourceHidden = metadata.sourceHidden(),
  )

fun Notebook.toFile(): NotebookFile =
  NotebookFile(
    cells = cells.map { it.toFile() },
    metadata =
      NotebookFileMetadata(
        kernelspec = KernelSpecMeta(name = kernelId, displayName = "Python 3"),
        jupydroid = JupyterDroidMeta(title = title, updatedAt = updatedAt.toString()),
      ),
  )

fun Cell.toFile(): CellFile =
  CellFile(
    id = id,
    cellType =
      when (kind) {
        CellKind.CODE -> "code"
        CellKind.MARKDOWN -> "markdown"
      },
    source = source,
    metadata = collapseMetadata(sourceHidden),
    outputs = outputs.map { it.toFileOutput() },
    executionCount = executionCount,
  )

fun Notebook.toSummary(): NotebookSummary =
  NotebookSummary(id = id, title = title, updatedAt = updatedAt)
