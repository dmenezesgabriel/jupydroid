package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads nbformat's stored output shapes, which are not the ones the kernel sends over the
 * wire: a saved `stream` writes `text` as an array of lines, and `execute_result` keeps its
 * `execution_count` beside the data.
 *
 * Total by design: an `output_type` this app does not model is dropped rather than throwing,
 * because a notebook written by a newer tool must still open.
 */
fun JsonElement.toDomainOutput(): CellOutput? {
  val output = this as? JsonObject ?: return null
  return when (output["output_type"]?.jsonPrimitive?.content) {
    "stream" -> output.toStream()
    "execute_result" -> output.toExecuteResult()
    "display_data" -> CellOutput.DisplayData(output.mimeBundle())
    "error" -> output.toError()
    else -> null
  }
}

private fun JsonObject.toStream(): CellOutput? {
  val name = when (get("name")?.jsonPrimitive?.content) {
    "stdout" -> StreamName.STDOUT
    "stderr" -> StreamName.STDERR
    else -> return null
  }
  return CellOutput.Stream(name, get("text").joinedText())
}

private fun JsonObject.toExecuteResult(): CellOutput =
  CellOutput.ExecuteResult(mimeBundle(), get("execution_count")?.jsonPrimitive?.intOrNull ?: 0)

private fun JsonObject.toError(): CellOutput? {
  val name = get("ename")?.jsonPrimitive?.content ?: return null
  return CellOutput.Error(
    name = name,
    value = get("evalue")?.jsonPrimitive?.content.orEmpty(),
    traceback = (get("traceback") as? JsonArray).orEmpty().map { it.jsonPrimitive.content },
  )
}

private fun JsonObject.mimeBundle(): Map<String, String> =
  (get("data") as? JsonObject).orEmpty().mapValues { (_, payload) -> payload.joinedText() }

/** Jupyter writes long text as an array of lines and short text as a string; accept both. */
private fun JsonElement?.joinedText(): String = when (this) {
  is JsonArray -> joinToString("") { it.jsonPrimitive.content }
  is JsonPrimitive -> content
  else -> ""
}
