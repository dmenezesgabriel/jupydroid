package com.jupydroid.extensions.ai.data.http

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * jupyterlab-commands-toolkit@7c87c5d's three events: the command `tools.py` `emit` puts on the bus, and the
 * ack and result its frontend (`src/index.ts`) posts back through `POST /api/events`.
 */
class LabCommandFramesTest {
  @Test
  fun aCommandOnTheBusCarriesItsNameArgumentsAndRequest() {
    val command = LabCommandFrames.parse(
      """{"schema_id": "$LAB_COMMAND", "name": "jupyterlab-ai-commands:add-cell",
        |"args": {"content": "print(42)"}, "requestId": "r1"}
      """.trimMargin(),
    )!!

    assertEquals("jupyterlab-ai-commands:add-cell", command.name)
    assertEquals(JsonObject(mapOf("content" to JsonPrimitive("print(42)"))), command.args)
    assertEquals("r1", command.requestId)
    assertNull(command.clientId)
  }

  @Test
  fun aCommandForOneWebClientNamesIt() {
    val command = LabCommandFrames.parse(
      """{"schema_id": "$LAB_COMMAND", "name": "n", "requestId": "r", "client_id": "c"}""",
    )!!

    assertEquals("c", command.clientId)
    assertEquals(JsonObject(emptyMap()), command.args)
  }

  @Test
  fun anyOtherEventIsNotACommand() {
    assertNull(LabCommandFrames.parse("""{"schema_id": "https://schema.jupyter.org/x/v1", "name": "n"}"""))
    assertNull(LabCommandFrames.parse("not json"))
  }

  @Test
  fun theAckNamesTheRequestAndThisClient() {
    val ack = Json.parseToJsonElement(LabCommandFrames.ack("r1", "me")).jsonObject

    assertEquals("https://events.jupyter.org/jupyterlab_command_toolkit/lab_command_ack/v1", ack.text("schema_id"))
    assertEquals("1", ack.text("version"))
    assertEquals("r1", ack.getValue("data").jsonObject.text("requestId"))
    assertEquals("me", ack.getValue("data").jsonObject.text("client_id"))
  }

  @Test
  fun aDoneCommandsResultCarriesWhatItReturned() {
    val returned = JsonObject(mapOf("cellId" to JsonPrimitive("new")))
    val result = Json.parseToJsonElement(LabCommandFrames.result("r1", LabCommandOutcome.Done(returned))).jsonObject

    assertEquals(
      "https://events.jupyter.org/jupyterlab_command_toolkit/lab_command_result/v1",
      result.text("schema_id"),
    )
    val data = result.getValue("data").jsonObject
    assertEquals("r1", data.text("requestId"))
    assertEquals(true, data.getValue("success").jsonPrimitive.boolean)
    assertEquals(returned, data.getValue("result"))
  }

  @Test
  fun aFailedCommandsResultCarriesItsError() {
    val data = Json.parseToJsonElement(LabCommandFrames.result("r1", LabCommandOutcome.Failed("no")))
      .jsonObject.getValue("data").jsonObject

    assertEquals(false, data.getValue("success").jsonPrimitive.boolean)
    assertEquals("no", data.text("error"))
  }

  private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

  private companion object {
    const val LAB_COMMAND = "https://events.jupyter.org/jupyterlab_command_toolkit/lab_command/v1"
  }
}
