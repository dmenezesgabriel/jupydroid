package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.MimeBundle
import com.jupydroid.app.domain.model.StreamName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The [CellOutput] an iopub message of [msgType] carries in this content, or null when the type
 * carries none or the content is not one this app can read.
 *
 * ```kotlin
 * message.content()?.toCellOutput(message.msgType())
 * ```
 */
internal fun JsonObject.toCellOutput(msgType: String?): CellOutput? = when (msgType) {
  "stream" -> toStream()
  "execute_result" -> toExecuteResult()
  "display_data" -> toDisplayData()
  // With no id there is nothing to update, and JupyterLab's `_onIOPub` finds no target either.
  "update_display_data" -> toDisplayData().takeIf { it.displayId != null }?.let(CellOutput::DisplayUpdate)
  "error" -> toError()
  "clear_output" -> CellOutput.ClearOutput(get("wait")?.jsonPrimitive?.booleanOrNull == true)
  else -> null
}

private fun JsonObject.toStream(): CellOutput? {
  // Deliberately not defaulting to stdout: mislabelling stderr would render a warning as
  // ordinary output, and the protocol defines only these two names.
  val name = when (get("name")?.jsonPrimitive?.content) {
    "stdout" -> StreamName.STDOUT
    "stderr" -> StreamName.STDERR
    else -> return null
  }
  return CellOutput.Stream(name, get("text")?.jsonPrimitive?.content.orEmpty())
}

private fun JsonObject.toExecuteResult(): CellOutput? =
  executionCount()?.let { CellOutput.ExecuteResult(mimeBundle(), it, outputMetadata()) }

private fun JsonObject.toDisplayData(): CellOutput.DisplayData = CellOutput.DisplayData(
  mimeBundle = mimeBundle(),
  metadata = outputMetadata(),
  displayId = (get("transient") as? JsonObject)?.get("display_id")?.let { (it as? JsonPrimitive)?.content },
)

private fun JsonObject.toError(): CellOutput? {
  val name = get("ename")?.jsonPrimitive?.content ?: return null
  return CellOutput.Error(
    name = name,
    value = get("evalue")?.jsonPrimitive?.content.orEmpty(),
    traceback = get("traceback")?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
  )
}

/**
 * mime -> payload, each kept as the JSON the kernel sent. A text mime arrives as a string
 * and `application/json` and friends as objects; `toString()`-ing the latter used to turn a
 * widget view into a *string* of JSON, which is a shape no Jupyter tool can render back.
 *
 * The wire protocol never splits text into lines - that is a file-format convention - so
 * nothing needs rejoining here, only carrying through.
 */
private fun JsonObject.mimeBundle(): MimeBundle = get("data")?.jsonObject.orEmpty()

/** Carried unread, as JupyterLab's `OutputModel` keeps it for the renderers that spend it. */
private fun JsonObject.outputMetadata(): JsonObject = get("metadata") as? JsonObject ?: JsonObject(emptyMap())

internal fun JsonObject.executionCount(): Int? = get("execution_count")?.jsonPrimitive?.intOrNull
