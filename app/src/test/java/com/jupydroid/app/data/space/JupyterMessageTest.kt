package com.jupydroid.app.data.space

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class JupyterMessageTest {
  @Test
  fun executeRequest_carriesTheCodeAndRoutesToTheShellChannel() {
    val frame = JupyterMessage.executeRequest("2 + 2", sessionId = "s1", msgId = "m1")

    val json = Json.parseToJsonElement(frame).jsonObject
    assertEquals("shell", json["channel"]!!.jsonPrimitive.content)
    assertEquals("execute_request", json["header"]!!.jsonObject["msg_type"]!!.jsonPrimitive.content)
    assertEquals("m1", json["header"]!!.jsonObject["msg_id"]!!.jsonPrimitive.content)
    assertEquals("2 + 2", json["content"]!!.jsonObject["code"]!!.jsonPrimitive.content)
  }

  /** A kernel refusing stdin matters: there is no terminal behind this one to answer it. */
  @Test
  fun executeRequest_disablesStdinAndStopsOnError() {
    val content = Json.parseToJsonElement(
      JupyterMessage.executeRequest("2 + 2", sessionId = "s1", msgId = "m1"),
    ).jsonObject["content"]!!.jsonObject

    assertEquals("false", content["allow_stdin"]!!.jsonPrimitive.content)
    assertEquals("true", content["stop_on_error"]!!.jsonPrimitive.content)
  }

  /**
   * `cursor_pos` is an absolute offset in characters, not a line and column - the protocol's
   * own shape, and the reason the port carries an offset too.
   */
  @Test
  fun completeRequest_carriesTheCodeAndAnAbsoluteCursorOffset() {
    val frame = JupyterMessage.completeRequest("import nu", cursorOffset = 9, sessionId = "s1", msgId = "m2")

    val json = Json.parseToJsonElement(frame).jsonObject
    assertEquals("shell", json["channel"]!!.jsonPrimitive.content)
    assertEquals("complete_request", json["header"]!!.jsonObject["msg_type"]!!.jsonPrimitive.content)
    assertEquals("m2", json["header"]!!.jsonObject["msg_id"]!!.jsonPrimitive.content)
    assertEquals("import nu", json["content"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    assertEquals("9", json["content"]!!.jsonObject["cursor_pos"]!!.jsonPrimitive.content)
  }

  @Test
  fun debugRequest_framesADapRequestOnTheControlChannel() {
    val args = buildJsonObject { put("clientID", "jupydroid") }
    val frame = JupyterMessage.debugRequest("attach", args, seq = 3, sessionId = "s1", msgId = "m3")

    val json = Json.parseToJsonElement(frame).jsonObject
    assertEquals("control", json["channel"]!!.jsonPrimitive.content)
    assertEquals("debug_request", json["header"]!!.jsonObject["msg_type"]!!.jsonPrimitive.content)
    assertEquals("m3", json["header"]!!.jsonObject["msg_id"]!!.jsonPrimitive.content)

    val content = json["content"]!!.jsonObject
    assertEquals("3", content["seq"]!!.jsonPrimitive.content)
    assertEquals("request", content["type"]!!.jsonPrimitive.content)
    assertEquals("attach", content["command"]!!.jsonPrimitive.content)
    assertEquals("jupydroid", content["arguments"]!!.jsonObject["clientID"]!!.jsonPrimitive.content)
  }
}
