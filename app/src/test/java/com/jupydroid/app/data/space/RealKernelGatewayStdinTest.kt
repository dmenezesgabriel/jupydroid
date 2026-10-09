package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stdin half of an execution: the kernel asks mid-run, and the app answers on the same socket.
 *
 * JupyterLab's frontend is the shape copied - `onStdin` hears the request and `sendInputReply`
 * answers it with the request's header as `parent_header`
 * (`jupyterlab@7f0e690 packages/services/src/kernel/future.ts`, `default.ts`).
 */
class RealKernelGatewayStdinTest {
  /** A prompt does not end the run: ipykernel is blocked inside `input()` until it is answered. */
  @Test
  fun execute_emitsTheInputRequestWithoutEndingTheExecution() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = {
      listOf(KernelFrames.inputRequest(it, "Name?")) + KernelFrames.successfulExecution(it)
    })
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))

    val events = gateway.execute(gateway.connect(SPACE, "Analysis.ipynb"), "cell-1", "input()").toList()

    val asked = events.filterIsInstance<KernelEvent.InputRequested>().single()
    assertEquals("cell-1", asked.cellId)
    assertEquals("Name?", asked.request.prompt)
    assertTrue("the run must go on to Idle, got $events", events.indexOf(asked) < events.indexOf(KernelEvent.Idle))
  }

  @Test
  fun replyInput_sendsTheValueOnStdinParentedToTheRequest() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { listOf(KernelFrames.inputRequest(it, "Name?")) })
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))
    val session = gateway.connect(SPACE, "Analysis.ipynb")
    val asked = gateway.execute(session, "cell-1", "input()").first() as KernelEvent.InputRequested

    gateway.replyInput(session, asked.request, "Ada")

    val reply = Json.parseToJsonElement(opener.sent.single { """"msg_type":"input_reply"""" in it }).jsonObject
    assertEquals("stdin", reply["channel"]!!.jsonPrimitive.content)
    assertEquals(Json.parseToJsonElement(asked.request.requestHeader), reply["parent_header"])
    assertEquals("Ada", reply["content"]!!.jsonObject["value"]!!.jsonPrimitive.content)
  }

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
