package com.jupydroid.app.data.space

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

  @Test
  fun resultOf_readsTextPlainFromOurOwnExecuteResult() {
    val frame = iopubFrame("execute_result", parent = "m1", text = "4")

    assertEquals("4", JupyterMessage.resultOf(frame, requestMsgId = "m1"))
  }

  /** Two notebooks share a kernel; another cell's output must not be mistaken for ours. */
  @Test
  fun resultOf_ignoresResultsBelongingToAnotherRequest() {
    val frame = iopubFrame("execute_result", parent = "someone-else", text = "99")

    assertNull(JupyterMessage.resultOf(frame, requestMsgId = "m1"))
  }

  @Test
  fun resultOf_ignoresTheOtherIopubTrafficThatArrivesFirst() {
    assertNull(JupyterMessage.resultOf(iopubFrame("status", "m1", "irrelevant"), "m1"))
    assertNull(JupyterMessage.resultOf(iopubFrame("execute_input", "m1", "irrelevant"), "m1"))
  }

  /** A kernel-side exception must surface, not read as "no result yet" and hang. */
  @Test
  fun errorOf_reportsATracebackFromTheKernel() {
    val frame = """
      {"channel":"iopub","parent_header":{"msg_id":"m1"},"header":{"msg_type":"error"},
       "content":{"ename":"ZeroDivisionError","evalue":"division by zero",
                  "traceback":["Traceback...","ZeroDivisionError: division by zero"]}}
    """.trimIndent()

    val error = JupyterMessage.errorOf(frame, requestMsgId = "m1")

    assertEquals("ZeroDivisionError: division by zero", error)
  }

  @Test
  fun malformedFrames_areIgnoredRatherThanCrashingTheChannel() {
    assertNull(JupyterMessage.resultOf("not json at all", "m1"))
    assertNull(JupyterMessage.errorOf("{\"truncated\":", "m1"))
  }

  private fun iopubFrame(msgType: String, parent: String, text: String) = """
    {"channel":"iopub","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"$msgType"},
     "content":{"execution_count":1,"data":{"text/plain":"$text"}}}
  """.trimIndent()
}
