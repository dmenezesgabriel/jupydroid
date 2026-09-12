package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.DebugEvent
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelDebuggerContract
import com.jupydroid.app.domain.port.SteppingCommand
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The real debugger owes the same contract as the fake, plus the protocol's own rules. */
class RealKernelDebuggerTest : KernelDebuggerContract() {
  override fun debugger(): KernelDebugger {
    lateinit var opener: ScriptedKernelSocketOpener
    opener = ScriptedKernelSocketOpener(reply = { parent -> listOf(echoedDebugReply(opener, parent)) })
    return RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))
  }

  /** `DebuggerSession.start()`'s own order (`session.ts:180-204`) - `attach` alone is not enough to start debugpy. */
  @Test
  fun attach_sendsInitializeThenAttachInOrder() = runTest {
    lateinit var opener: ScriptedKernelSocketOpener
    opener = ScriptedKernelSocketOpener(reply = { parent -> listOf(echoedDebugReply(opener, parent)) })
    val debugger = RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))

    debugger.attach(SESSION)

    assertEquals(listOf("initialize", "attach"), debugCommandsSent(opener))
  }

  /** A rejected `initialize` must fail the call rather than proceed to attach onto nothing. */
  @Test
  fun attach_throwsAndStopsWhenInitializeFails() = runTest {
    val opener = ScriptedKernelSocketOpener(
      reply = { parent -> listOf(KernelFrames.debugReply(parent, "initialize", success = false, message = "boom")) },
    )
    val debugger = RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))

    val failure = runCatching { debugger.attach(SESSION) }

    assertTrue("expected attach to fail, got $failure", failure.exceptionOrNull() is DebugRequestFailedException)
    assertEquals("attach must never follow a failed initialize", listOf("initialize"), debugCommandsSent(opener))
  }

  @Test
  fun dumpCell_sendsTheCodeAsDapArguments() = runTest {
    lateinit var opener: ScriptedKernelSocketOpener
    opener = ScriptedKernelSocketOpener(reply = { parent -> listOf(echoedDebugReply(opener, parent)) })
    val debugger = RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))

    debugger.dumpCell(SESSION, "x = 1")

    val request = opener.sent.single { """"command":"dumpCell"""" in it }
    assertTrue("must carry the cell's code, got $request", """"code":"x = 1"""" in request)
  }

  /** `continue` is a Kotlin keyword - `step`'s own reason for existing - so its DAP mapping is worth pinning. */
  @Test
  fun step_sendsTheDapCommandNameForEachSteppingCommand() = runTest {
    lateinit var opener: ScriptedKernelSocketOpener
    opener = ScriptedKernelSocketOpener(reply = { parent -> listOf(echoedDebugReply(opener, parent)) })
    val debugger = RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))

    debugger.step(SESSION, threadId = 1, command = SteppingCommand.NEXT)
    debugger.step(SESSION, threadId = 1, command = SteppingCommand.STEP_IN)
    debugger.step(SESSION, threadId = 1, command = SteppingCommand.STEP_OUT)
    debugger.step(SESSION, threadId = 1, command = SteppingCommand.CONTINUE)

    assertEquals(listOf("next", "stepIn", "stepOut", "continue"), debugCommandsSent(opener))
  }

  @Test
  fun events_deliversAScriptedStoppedEvent() = runTest {
    val stoppedBody = buildJsonObject { put("threadId", 3) }
    val opener = ScriptedKernelSocketOpener(
      reply = { parent ->
        listOf(KernelFrames.debugReply(parent, "attach"), KernelFrames.debugEvent("stopped", stoppedBody))
      },
    )
    val debugger = RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))
    val collected = mutableListOf<DebugEvent>()
    val collecting = launch { collected += debugger.events(SESSION).take(1).toList() }
    advanceUntilIdle()

    debugger.attach(SESSION)
    collecting.join()

    assertEquals(listOf(DebugEvent.Stopped(threadId = 3)), collected)
  }

  @Test
  fun events_deliversAScriptedContinuedEvent() = runTest {
    val opener = ScriptedKernelSocketOpener(
      reply = { parent -> listOf(KernelFrames.debugReply(parent, "attach"), KernelFrames.debugEvent("continued")) },
    )
    val debugger = RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))
    val collected = mutableListOf<DebugEvent>()
    val collecting = launch { collected += debugger.events(SESSION).take(1).toList() }
    advanceUntilIdle()

    debugger.attach(SESSION)
    collecting.join()

    assertEquals(listOf(DebugEvent.Continued), collected)
  }

  private fun debugCommandsSent(opener: ScriptedKernelSocketOpener): List<String> =
    opener.sent.mapNotNull { it.debugCommandOrNull() }

  /**
   * A reply that echoes whatever the request just asked, the way a real debugpy session's own
   * `dumpCell`/`setBreakpoints` responses depend on what was sent - a fixed canned body cannot
   * satisfy [KernelDebuggerContract]'s idempotence and ordering assertions.
   */
  private fun echoedDebugReply(opener: ScriptedKernelSocketOpener, parent: String): String {
    val request = Json.parseToJsonElement(opener.sent.last()).jsonObject["content"]!!.jsonObject
    val command = request["command"]!!.jsonPrimitive.content
    val args = request["arguments"]?.jsonObject ?: JsonObject(emptyMap())
    return KernelFrames.debugReply(parent, command, body = echoedBody(command, args))
  }

  private fun echoedBody(command: String, args: JsonObject): JsonObject = when (command) {
    "dumpCell" -> buildJsonObject { put("sourcePath", "/fake/debug-cells/${args.codeArgument().hashCode()}.py") }
    "setBreakpoints" -> buildJsonObject { put("breakpoints", echoedBreakpoints(args)) }
    else -> buildJsonObject { }
  }

  private fun echoedBreakpoints(args: JsonObject) = buildJsonArray {
    args["breakpoints"]?.jsonArray.orEmpty().forEach { requested ->
      val line = requested.jsonObject["line"]!!.jsonPrimitive.content.toInt()
      add(
        buildJsonObject {
          put("line", line)
          put("verified", true)
        },
      )
    }
  }

  private fun JsonObject.codeArgument(): String = get("code")!!.jsonPrimitive.content

  /** Null for the `kernel_info_request` handshake frame every request's `askOnce` sends first. */
  private fun String.debugCommandOrNull(): String? =
    Json.parseToJsonElement(this).jsonObject["content"]!!.jsonObject["command"]?.jsonPrimitive?.content
}
