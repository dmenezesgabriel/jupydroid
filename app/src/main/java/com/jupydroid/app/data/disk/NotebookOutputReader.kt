package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.MimeBundle
import com.jupydroid.app.domain.model.StreamName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Reads nbformat's stored output shapes, which are not the ones the kernel sends over the
 * wire: a saved `stream` writes `text` as an array of lines, and `execute_result` keeps its
 * `execution_count` beside the data.
 *
 * Total by design: an `output_type` this app does not model is dropped rather than throwing,
 * because a notebook written by a newer tool must still open. The same holds *inside* an
 * output, at member granularity - `as?` rather than `jsonPrimitive`, which throws on a
 * non-primitive and used to fail the whole notebook's open on one malformed member.
 */
fun JsonElement.toDomainOutput(): CellOutput? {
  val output = this as? JsonObject ?: return null
  return when (output["output_type"].stringOrNull()) {
    "stream" -> output.toStream()
    "execute_result" -> output.toExecuteResult()
    "display_data" -> CellOutput.DisplayData(output.mimeBundle())
    "error" -> output.toError()
    else -> null
  }
}

private fun JsonObject.toStream(): CellOutput? {
  val name = when (get("name").stringOrNull()) {
    "stdout" -> StreamName.STDOUT
    "stderr" -> StreamName.STDERR
    // JupyterLab keeps an `input()` answer as a `stdin` stream
    // (jupyterlab@7f0e690 packages/outputarea/src/widget.ts, `onInputRequest`).
    "stdin" -> StreamName.STDIN
    else -> return null
  }
  return CellOutput.Stream(name, get("text").joinedText())
}

private fun JsonObject.toExecuteResult(): CellOutput =
  CellOutput.ExecuteResult(mimeBundle(), get("execution_count").intOrZero())

private fun JsonObject.toError(): CellOutput? {
  val name = get("ename").stringOrNull() ?: return null
  return CellOutput.Error(
    name = name,
    value = get("evalue").stringOrNull().orEmpty(),
    traceback = (get("traceback") as? JsonArray).orEmpty().mapNotNull { it.stringOrNull() },
  )
}

/** nbformat's `_rejoin_mimebundle`; see [rejoinedBundle] for why a JSON mime is exempt. */
private fun JsonObject.mimeBundle(): MimeBundle = (get("data") as? JsonObject)?.rejoinedBundle().orEmpty()

/** The content of a JSON string, or null where the value is not a primitive at all. */
private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.content

private fun JsonElement?.intOrZero(): Int = (this as? JsonPrimitive)?.intOrNull ?: 0

/** Jupyter writes long text as an array of lines and short text as a string; accept both. */
private fun JsonElement?.joinedText(): String = when (this) {
  is JsonArray -> mapNotNull { it.stringOrNull() }.joinToString("")
  is JsonPrimitive -> content
  else -> ""
}
