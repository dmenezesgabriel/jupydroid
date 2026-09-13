package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.BreakpointRequest
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

  /**
   * P6a-10's own gate: debugpy keeps a debug session attached for the life of the kernel process,
   * not this app's own session object, so a second `attach` after an earlier one already
   * succeeded is rejected with exactly this message - not a real failure, since the kernel already
   * has what `attach`'s own postcondition promises. Reproduced live on the tablet: disarming a
   * breakpoint and re-running the cell it was on threw this, uncaught, before this fix.
   */
  @Test
  fun attach_toleratesAnAlreadyStartedSession() = runTest {
    lateinit var opener: ScriptedKernelSocketOpener
    val alreadyStarted = "Invalid message: Session is already started"
    opener = ScriptedKernelSocketOpener(
      reply = { parent -> listOf(replyFailingOneCommand(opener, parent, "attach", alreadyStarted)) },
    )
    val debugger = RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))

    debugger.attach(SESSION)

    assertEquals(listOf("initialize", "attach"), debugCommandsSent(opener))
  }

  /** A third run's own re-`initialize` gets the same tolerant treatment `attach` does. */
  @Test
  fun attach_toleratesAnAlreadyInitializedSession() = runTest {
    lateinit var opener: ScriptedKernelSocketOpener
    val alreadyInitialized = "Invalid message: Session is already initialized"
    opener = ScriptedKernelSocketOpener(
      reply = { parent -> listOf(replyFailingOneCommand(opener, parent, "initialize", alreadyInitialized)) },
    )
    val debugger = RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))

    debugger.attach(SESSION)

    assertEquals(listOf("initialize", "attach"), debugCommandsSent(opener))
  }

  /** A genuinely different `attach` failure must still fail - only "already started" is tolerated. */
  @Test
  fun attach_stillThrowsForAnyOtherAttachFailure() = runTest {
    lateinit var opener: ScriptedKernelSocketOpener
    opener = ScriptedKernelSocketOpener(
      reply = { parent -> listOf(replyFailingOneCommand(opener, parent, "attach", "boom")) },
    )
    val debugger = RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))

    val failure = runCatching { debugger.attach(SESSION) }

    assertTrue(failure.exceptionOrNull() is DebugRequestFailedException)
  }

  /** P6a-16's own gate: DAP's `setBreakpoints` request carries a per-line `condition` when one is set. */
  @Test
  fun setBreakpoints_sendsAConditionWhenOneIsSet() = runTest {
    lateinit var opener: ScriptedKernelSocketOpener
    opener = ScriptedKernelSocketOpener(reply = { parent -> listOf(echoedDebugReply(opener, parent)) })
    val debugger = RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))
    val codeId = debugger.dumpCell(SESSION, "x = 1")

    debugger.setBreakpoints(SESSION, codeId, listOf(BreakpointRequest(line = 1, condition = "x > 10")))

    val request = opener.sent.single { """"command":"setBreakpoints"""" in it }
    assertTrue("must carry the condition, got $request", """"condition":"x > 10"""" in request)
  }

  /** An empty/blank condition must not reach the kernel as `condition: ""` - the same as none at all. */
  @Test
  fun setBreakpoints_omitsABlankCondition() = runTest {
    lateinit var opener: ScriptedKernelSocketOpener
    opener = ScriptedKernelSocketOpener(reply = { parent -> listOf(echoedDebugReply(opener, parent)) })
    val debugger = RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))
    val codeId = debugger.dumpCell(SESSION, "x = 1")

    debugger.setBreakpoints(SESSION, codeId, listOf(BreakpointRequest(line = 1, condition = "  ")))

    val request = opener.sent.single { """"command":"setBreakpoints"""" in it }
    assertTrue("must not carry a blank condition, got $request", """"condition"""" !in request)
  }

  @Test
  fun setBreakpoints_omitsConditionWhenNoneIsSet() = runTest {
    lateinit var opener: ScriptedKernelSocketOpener
    opener = ScriptedKernelSocketOpener(reply = { parent -> listOf(echoedDebugReply(opener, parent)) })
    val debugger = RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))
    val codeId = debugger.dumpCell(SESSION, "x = 1")

    debugger.setBreakpoints(SESSION, codeId, listOf(BreakpointRequest(line = 1)))

    val request = opener.sent.single { """"command":"setBreakpoints"""" in it }
    assertTrue("must not carry any condition, got $request", """"condition"""" !in request)
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

  /** P6a-5's own gate: predicting the codeId locally (`CellCodeId`) means a repeat needs no round trip. */
  @Test
  fun dumpCell_doesNotResendForUnchangedText() = runTest {
    lateinit var opener: ScriptedKernelSocketOpener
    opener = ScriptedKernelSocketOpener(reply = { parent -> listOf(echoedDebugReply(opener, parent)) })
    val debugger = RealKernelDebugger(KernelSockets(opener, FakeKernelLifecycle()))

    val first = debugger.dumpCell(SESSION, "x = 1")
    val second = debugger.dumpCell(SESSION, "x = 1")

    assertEquals(first, second)
    assertEquals(listOf("debugInfo", "dumpCell"), debugCommandsSent(opener))
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

  /** Succeeds every command except `attach`, which fails with [attachFailureMessage] instead. */
  /** Succeeds every command except [failingCommand], which fails with [failureMessage] instead. */
  private fun replyFailingOneCommand(
    opener: ScriptedKernelSocketOpener,
    parent: String,
    failingCommand: String,
    failureMessage: String,
  ): String {
    val command = Json.parseToJsonElement(opener.sent.last()).jsonObject["content"]!!.jsonObject["command"]!!
      .jsonPrimitive.content
    return if (command == failingCommand) {
      KernelFrames.debugReply(parent, command, success = false, message = failureMessage)
    } else {
      KernelFrames.debugReply(parent, command)
    }
  }

  private fun JsonObject.codeArgument(): String = get("code")!!.jsonPrimitive.content

  /** Null for the `kernel_info_request` handshake frame every request's `askOnce` sends first. */
  private fun String.debugCommandOrNull(): String? =
    Json.parseToJsonElement(this).jsonObject["content"]!!.jsonObject["command"]?.jsonPrimitive?.content
}
