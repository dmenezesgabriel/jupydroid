package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Writes a [CellOutput] back in the nbformat shape Jupyter itself would have written. */
fun CellOutput.toFileOutput(): JsonElement = when (this) {
  is CellOutput.Stream -> toStreamJson()
  is CellOutput.ExecuteResult -> toExecuteResultJson()
  is CellOutput.DisplayData -> toDisplayDataJson()
  is CellOutput.Error -> toErrorJson()
  // An instruction to the front end rather than a result, so nbformat has no shape for it.
  CellOutput.ClearOutput -> JsonObject(emptyMap())
}

private fun CellOutput.Stream.toStreamJson() = buildJsonObject {
  put("output_type", "stream")
  put("name", if (name == StreamName.STDOUT) "stdout" else "stderr")
  put("text", text.toJsonLines())
}

private fun CellOutput.ExecuteResult.toExecuteResultJson() = buildJsonObject {
  put("output_type", "execute_result")
  put("execution_count", executionCount)
  put("data", mimeBundle.toJsonData())
  putJsonObject("metadata") { }
}

private fun CellOutput.DisplayData.toDisplayDataJson() = buildJsonObject {
  put("output_type", "display_data")
  put("data", mimeBundle.toJsonData())
  putJsonObject("metadata") { }
}

private fun CellOutput.Error.toErrorJson() = buildJsonObject {
  put("output_type", "error")
  put("ename", name)
  put("evalue", value)
  put("traceback", buildJsonArray { traceback.forEach { add(JsonPrimitive(it)) } })
}

/**
 * Splits into lines that keep their newlines, the way Jupyter writes them - so `"42\n"`
 * becomes `["42\n"]` and not `["42\n", ""]`. Joining the result returns the original
 * exactly, which is what makes a round trip lossless.
 */
private fun String.toJsonLines(): JsonArray = buildJsonArray {
  val split = split("\n")
  split
    .mapIndexed { index, line -> if (index == split.lastIndex) line else "$line\n" }
    // Whatever is left empty is the artefact of a final newline, not a line of its own.
    .filter { it.isNotEmpty() }
    .forEach { add(JsonPrimitive(it)) }
}

private fun Map<String, String>.toJsonData(): JsonObject =
  JsonObject(mapValues { (_, payload) -> JsonPrimitive(payload) })
