package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.DebugScope
import com.jupydroid.app.domain.port.DebugStackFrame
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.domain.port.VerifiedBreakpoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelDebugRepliesTest {
  @Test
  fun of_readsTheBodyOfASuccessfulReply() {
    val body = buildJsonObject { put("sourcePath", "/tmp/cell.py") }

    assertEquals(body, KernelDebugReplies.of(KernelFrames.debugReply("m1", "dumpCell", body = body), "m1"))
  }

  /** A DAP-level failure is a defect the caller must see, not traffic a total reader skips. */
  @Test
  fun of_throwsOnADapLevelFailure() {
    val frame = KernelFrames.debugReply("m1", "attach", success = false, message = "not started")

    val failure = runCatching { KernelDebugReplies.of(frame, "m1") }

    val error = failure.exceptionOrNull()
    assertTrue("expected a DebugRequestFailedException, got $failure", error is DebugRequestFailedException)
    assertTrue("must name the command, got ${error?.message}", error?.message?.contains("attach") == true)
    assertTrue("must carry the DAP message, got ${error?.message}", error?.message?.contains("not started") == true)
  }

  /** The control channel is shared the way shell is; a reply to another client must not be taken as ours. */
  @Test
  fun of_ignoresAReplyToSomebodyElsesRequest() {
    assertNull(KernelDebugReplies.of(KernelFrames.debugReply("other", "attach"), "m1"))
  }

  @Test
  fun of_ignoresFramesThatAreNotADebugReply() {
    assertNull(KernelDebugReplies.of(KernelFrames.executeReply("m1"), "m1"))
  }

  @Test
  fun of_ignoresAFrameItCannotParse() {
    assertNull(KernelDebugReplies.of("not json at all", "m1"))
  }

  /** DAP's own guarantee: the response array corresponds element-for-element to the request's. */
  @Test
  fun verifiedBreakpoints_preservesTheReplysOwnOrder() {
    val body = fixture("""{"breakpoints":[{"line":5,"verified":true},{"line":2,"verified":false}]}""")

    assertEquals(
      listOf(VerifiedBreakpoint(line = 5, verified = true), VerifiedBreakpoint(line = 2, verified = false)),
      KernelDebugReplies.verifiedBreakpoints(body),
    )
  }

  @Test
  fun stackFrames_readsIdNameLineAndColumn() {
    val body = fixture("""{"stackFrames":[{"id":1,"name":"<module>","line":3,"column":1}]}""")

    val expected = listOf(DebugStackFrame(id = 1, name = "<module>", line = 3, column = 1))
    assertEquals(expected, KernelDebugReplies.stackFrames(body))
  }

  @Test
  fun scopes_readsNameReferenceAndExpensive() {
    val body = fixture("""{"scopes":[{"name":"Locals","variablesReference":7,"expensive":false}]}""")

    val expected = listOf(DebugScope(name = "Locals", variablesReference = 7, expensive = false))
    assertEquals(expected, KernelDebugReplies.scopes(body))
  }

  /** [DebugVariable.type] is nullable - debugpy omits it for a variable with none, not every reply carries one. */
  @Test
  fun variables_readsAnExpandableAndALeafVariable() {
    val body = fixture(
      """{"variables":[
         {"name":"x","value":"1","type":"int","variablesReference":0},
         {"name":"df","value":"DataFrame","variablesReference":12}]}""",
    )

    assertEquals(
      listOf(
        DebugVariable(name = "x", value = "1", type = "int", variablesReference = 0),
        DebugVariable(name = "df", value = "DataFrame", type = null, variablesReference = 12),
      ),
      KernelDebugReplies.variables(body),
    )
  }

  /**
   * `hashSeed` arrives as a JSON number, not the string the reference's own type declares
   * (`evidence/p6a-5-cell-code-id/`).
   */
  @Test
  fun debugInfoHashParams_readsHashAndTmpFileFields() {
    val body = fixture(
      """{"hashMethod":"Murmur2","hashSeed":3339675911,
         "tmpFilePrefix":"/tmp/ipykernel_1/","tmpFileSuffix":".py"}""",
    )

    val expected = DebugInfoHashParams(
      hashMethod = "Murmur2",
      hashSeed = 3339675911L,
      tmpFilePrefix = "/tmp/ipykernel_1/",
      tmpFileSuffix = ".py",
    )
    assertEquals(expected, debugInfoHashParams(body))
  }

  private fun fixture(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject
}
