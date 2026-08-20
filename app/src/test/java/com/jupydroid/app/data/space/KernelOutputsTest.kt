package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.mimeBundleOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelOutputsTest {
  @Test
  fun malformedFrames_areIgnoredRatherThanCrashingTheChannel() {
    assertNull(KernelOutputs.outputOf("not json at all", "m1"))
    assertNull(KernelOutputs.executionCountOf("{\"truncated\":", "m1"))
    assertFalse(KernelOutputs.isIdleFor("not json at all", "m1"))
  }

  @Test
  fun outputOf_readsStdoutAndStderrAsSeparateStreams() {
    val out = KernelOutputs.outputOf(streamFrame("stdout", "hello\n"), "m1")
    val err = KernelOutputs.outputOf(streamFrame("stderr", "warning\n"), "m1")

    assertEquals(CellOutput.Stream(StreamName.STDOUT, "hello\n"), out)
    assertEquals(CellOutput.Stream(StreamName.STDERR, "warning\n"), err)
  }

  /** An unknown stream name must not be silently reported as stdout. */
  @Test
  fun outputOf_ignoresAStreamItCannotName() {
    assertNull(KernelOutputs.outputOf(streamFrame("stdin", "?"), "m1"))
  }

  @Test
  fun outputOf_readsExecuteResultWithItsExecutionCount() {
    val frame = """
      {"channel":"iopub","parent_header":{"msg_id":"m1"},"header":{"msg_type":"execute_result"},
       "content":{"execution_count":7,"data":{"text/plain":"4"}}}
    """.trimIndent()

    assertEquals(CellOutput.ExecuteResult(mimeBundleOf("text/plain" to "4"), 7), KernelOutputs.outputOf(frame, "m1"))
  }

  /** A figure arrives as display_data, and its payload is base64 that must survive intact. */
  @Test
  fun outputOf_readsDisplayDataKeepingEveryMimeType() {
    val frame = """
      {"channel":"iopub","parent_header":{"msg_id":"m1"},"header":{"msg_type":"display_data"},
       "content":{"data":{"text/plain":"<Figure>","image/png":"iVBORw0KGgo="}}}
    """.trimIndent()

    val output = KernelOutputs.outputOf(frame, "m1") as CellOutput.DisplayData

    assertEquals(JsonPrimitive("iVBORw0KGgo="), output.mimeBundle["image/png"])
    assertEquals(JsonPrimitive("<Figure>"), output.mimeBundle["text/plain"])
  }

  /**
   * An `application/json` payload stays an **object**. It used to be `toString()`-ed into a
   * string of JSON, which is the shape no Jupyter tool can render back - and the same flaw
   * that would have stored every widget view as text.
   */
  @Test
  fun outputOf_keepsNonTextMimePayloadsAsTheirJson() {
    val frame = """
      {"channel":"iopub","parent_header":{"msg_id":"m1"},"header":{"msg_type":"display_data"},
       "content":{"data":{"application/json":{"a":1}}}}
    """.trimIndent()

    val output = KernelOutputs.outputOf(frame, "m1") as CellOutput.DisplayData

    assertEquals(Json.parseToJsonElement("""{"a":1}"""), output.mimeBundle["application/json"])
  }

  @Test
  fun outputOf_readsErrorWithItsTraceback() {
    val frame = """
      {"channel":"iopub","parent_header":{"msg_id":"m1"},"header":{"msg_type":"error"},
       "content":{"ename":"ValueError","evalue":"bad","traceback":["line one","line two"]}}
    """.trimIndent()

    val error = KernelOutputs.outputOf(frame, "m1") as CellOutput.Error

    assertEquals("ValueError", error.name)
    assertEquals("bad", error.value)
    assertEquals(listOf("line one", "line two"), error.traceback)
  }

  @Test
  fun outputOf_readsClearOutput() {
    val frame = """
      {"channel":"iopub","parent_header":{"msg_id":"m1"},
       "header":{"msg_type":"clear_output"},"content":{}}
    """.trimIndent()

    assertEquals(CellOutput.ClearOutput, KernelOutputs.outputOf(frame, "m1"))
  }

  @Test
  fun outputOf_ignoresOutputsBelongingToAnotherRequest() {
    assertNull(KernelOutputs.outputOf(streamFrame("stdout", "theirs", parent = "someone-else"), "m1"))
  }

  /**
   * execute_input is what carries the execution count for the cell, and it arrives before
   * any output. status and other iopub chatter carry none.
   */
  @Test
  fun executionCountOf_readsTheCountFromOurExecuteInput() {
    val frame = """
      {"channel":"iopub","parent_header":{"msg_id":"m1"},"header":{"msg_type":"execute_input"},
       "content":{"code":"2 + 2","execution_count":12}}
    """.trimIndent()

    assertEquals(12, KernelOutputs.executionCountOf(frame, "m1"))
    assertNull(KernelOutputs.executionCountOf(frame, "another-request"))
    assertNull(KernelOutputs.executionCountOf(streamFrame("stdout", "x"), "m1"))
  }

  /**
   * Completion is driven by status:idle for *our* request. Getting this wrong either hangs
   * the cell forever or ends it on somebody else's idle, truncating our own output.
   */
  @Test
  fun isIdleFor_endsOnOurOwnIdleAndNoOneElses() {
    assertTrue(KernelOutputs.isIdleFor(statusFrame("idle", parent = "m1"), "m1"))
    assertFalse(KernelOutputs.isIdleFor(statusFrame("busy", parent = "m1"), "m1"))
    assertFalse(KernelOutputs.isIdleFor(statusFrame("idle", parent = "someone-else"), "m1"))
    assertFalse(KernelOutputs.isIdleFor(streamFrame("stdout", "x"), "m1"))
  }

  /**
   * The other half of completion. JupyterLab's KernelFutureHandler is only done once *both*
   * the shell execute_reply and the iopub idle have arrived, in either order
   * (reference/research_repos/jupyterlab/packages/services/src/kernel/future.ts:14-17).
   * The two travel on separate channels with no ordering guarantee between them, so idle
   * alone would truncate output still in flight and reply alone would end the cell early.
   */
  @Test
  fun isReplyFor_recognisesOurExecuteReplyOnTheShellChannel() {
    assertTrue(KernelOutputs.isReplyFor(replyFrame(parent = "m1"), "m1"))
    assertFalse(KernelOutputs.isReplyFor(replyFrame(parent = "someone-else"), "m1"))
    assertFalse(KernelOutputs.isReplyFor(statusFrame("idle", parent = "m1"), "m1"))
    assertFalse(KernelOutputs.isReplyFor("not json at all", "m1"))
  }

  private fun replyFrame(parent: String) = """
    {"channel":"shell","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"execute_reply"},
     "content":{"status":"ok","execution_count":3}}
  """.trimIndent()

  private fun streamFrame(name: String, text: String, parent: String = "m1") = """
    {"channel":"iopub","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"stream"},
     "content":{"name":"$name","text":"$text"}}
  """.trimIndent()

  private fun statusFrame(state: String, parent: String) = """
    {"channel":"iopub","parent_header":{"msg_id":"$parent"},"header":{"msg_type":"status"},
     "content":{"execution_state":"$state"}}
  """.trimIndent()
}
