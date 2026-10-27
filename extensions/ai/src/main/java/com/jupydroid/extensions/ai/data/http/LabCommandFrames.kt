package com.jupydroid.extensions.ai.data.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A JupyterLab command the server asks a web client to run.
 *
 * @param requestId null for a command emitted without waiting for its result, which gets no ack and
 *   no result.
 * @param clientId the one web client that should run it, or null for every one.
 */
internal data class LabCommand(val name: String, val args: JsonObject, val requestId: String?, val clientId: String?)

/**
 * jupyterlab-commands-toolkit@7c87c5d's events: `lab_command` as `tools.py` `emit` puts it on the bus,
 * and the `lab_command_ack` and `lab_command_result` bodies its frontend posts back through
 * `POST /api/events` (`src/index.ts`, `handleCommand`).
 *
 * ```
 * LabCommandFrames.parse(text)?.let { command -> post(LabCommandFrames.ack(command.requestId!!, clientId)) }
 * ```
 */
internal object LabCommandFrames {
  private const val LAB_COMMAND = "https://events.jupyter.org/jupyterlab_command_toolkit/lab_command/v1"
  private const val LAB_COMMAND_ACK = "https://events.jupyter.org/jupyterlab_command_toolkit/lab_command_ack/v1"
  private const val LAB_COMMAND_RESULT =
    "https://events.jupyter.org/jupyterlab_command_toolkit/lab_command_result/v1"

  private val lenient = Json { ignoreUnknownKeys = true }

  /** [text] as a command, or null for any other event: the bus is the whole server's. */
  fun parse(text: String): LabCommand? = runCatching { lenient.decodeFromString<WireLabCommand>(text) }
    .getOrNull()
    ?.takeIf { it.schemaId == LAB_COMMAND }
    ?.let { LabCommand(it.name, it.args, it.requestId, it.clientId) }

  /** That [clientId] received [requestId], which lets the server stop waiting for any other client. */
  fun ack(requestId: String, clientId: String): String = event(
    LAB_COMMAND_ACK,
    buildJsonObject {
      put("requestId", requestId)
      put("client_id", clientId)
    },
  )

  /** What [requestId] came to: `{requestId, success, result}` or `{requestId, success, error}`. */
  fun result(requestId: String, outcome: LabCommandOutcome): String = event(
    LAB_COMMAND_RESULT,
    buildJsonObject {
      put("requestId", requestId)
      put("success", outcome is LabCommandOutcome.Done)
      when (outcome) {
        is LabCommandOutcome.Done -> put("result", outcome.result)
        is LabCommandOutcome.Failed -> put("error", outcome.error)
      }
    },
  )

  private fun event(schemaId: String, body: JsonObject): String = buildJsonObject {
    put("schema_id", schemaId)
    put("version", "1")
    put("data", body)
  }.toString()
}

@Serializable
private data class WireLabCommand(
  @SerialName("schema_id") val schemaId: String? = null,
  val name: String,
  val args: JsonObject = JsonObject(emptyMap()),
  val requestId: String? = null,
  @SerialName("client_id") val clientId: String? = null,
)
