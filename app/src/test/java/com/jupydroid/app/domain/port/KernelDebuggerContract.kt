package com.jupydroid.app.domain.port

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What every [KernelDebugger] must do, whoever implements it.
 *
 * Deliberately narrow, the way [KernelGatewayContract] is: assertions live only where any honest
 * implementation can agree regardless of kernel state - a real debugpy session only has a
 * meaningful call stack or variables *while stopped*, which nothing here can force without a
 * running kernel, so stepping and inspection are left to each implementation's own tests
 * ([FakeKernelDebuggerContractTest] covers the fake's).
 */
abstract class KernelDebuggerContract {
  protected abstract fun debugger(): KernelDebugger

  @Test
  fun attach_isAcceptedForASession() = runTest {
    debugger().attach(SESSION)
  }

  @Test
  fun configurationDone_isAcceptedAfterAttaching() = runTest {
    val debugger = debugger()
    debugger.attach(SESSION)

    debugger.configurationDone(SESSION)
  }

  /** ipykernel names the dumped file from a hash of the code - the same text must land at the same place. */
  @Test
  fun dumpCell_isIdempotentForTheSameCode() = runTest {
    val debugger = debugger()

    val first = debugger.dumpCell(SESSION, "x = 1")
    val second = debugger.dumpCell(SESSION, "x = 1")

    assertEquals(first, second)
  }

  @Test
  fun dumpCell_differsForDifferentCode() = runTest {
    val debugger = debugger()

    val a = debugger.dumpCell(SESSION, "x = 1")
    val b = debugger.dumpCell(SESSION, "y = 2")

    assertEquals(false, a == b)
  }

  /**
   * DAP's own guarantee: `setBreakpoints`'s response array corresponds element-for-element to the
   * request's - not a detail this port may drop, since [VerifiedBreakpoint.verified] is exactly
   * what a gutter dot (P6a-6) reads per line.
   */
  @Test
  fun setBreakpoints_returnsOneResultPerRequestedLineInOrder() = runTest {
    val debugger = debugger()
    val codeId = debugger.dumpCell(SESSION, "x = 1\ny = 2\n")

    val result = debugger.setBreakpoints(SESSION, codeId, listOf(BreakpointRequest(1), BreakpointRequest(2)))

    assertEquals(listOf(1, 2), result.map { it.line })
  }

  /**
   * An ad hoc expression's own answer has no variable name of its own - [DebugVariable.name] holds
   * the expression itself, DAP's own `EvaluateResponse` having none (P6a-17).
   */
  @Test
  fun evaluate_answersTheExpressionAsItsOwnName() = runTest {
    val result = debugger().evaluate(SESSION, expression = "1 + 1", frameId = 1)

    assertEquals("1 + 1", result.name)
  }

  protected companion object {
    val SESSION = KernelSession(
      id = "dbg",
      spaceId = "base",
      kernelId = "python3",
      kernelName = "python3",
      serverSessionId = "dbg-session",
    )
  }
}
