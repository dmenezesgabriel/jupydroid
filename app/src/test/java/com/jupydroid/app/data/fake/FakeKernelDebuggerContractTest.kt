@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.BreakpointRequest
import com.jupydroid.app.domain.port.DebugEvent
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelDebuggerContract
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.SteppingCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The fake is what the UI runs against where no Space can run, so it owes the same contract. */
class FakeKernelDebuggerContractTest : KernelDebuggerContract() {
  override fun debugger(): KernelDebugger = FakeKernelDebugger()

  @Test
  fun emittedStoppedAndContinuedReachEventsInOrder() = runTest {
    val fake = FakeKernelDebugger()
    val collected = mutableListOf<DebugEvent>()
    val collecting = launch { collected += fake.events(SESSION).take(2).toList() }
    advanceUntilIdle()

    fake.emit(SESSION, DebugEvent.Stopped(threadId = 3))
    fake.emit(SESSION, DebugEvent.Continued)
    collecting.join()

    assertEquals(listOf(DebugEvent.Stopped(threadId = 3), DebugEvent.Continued), collected)
  }

  @Test
  fun eventsAreKeptPerSessionRatherThanShared() = runTest {
    val fake = FakeKernelDebugger()
    val other = KernelSession(
      id = "other",
      spaceId = "base",
      kernelId = "python3",
      kernelName = "python3",
      serverSessionId = "other-session",
    )
    val collected = mutableListOf<DebugEvent>()
    val collecting = launch { collected += fake.events(SESSION).take(1).toList() }
    advanceUntilIdle()

    fake.emit(other, DebugEvent.Stopped(threadId = 9))
    fake.emit(SESSION, DebugEvent.Stopped(threadId = 1))
    collecting.join()

    assertEquals(listOf(DebugEvent.Stopped(threadId = 1)), collected)
  }

  @Test
  fun stepRecordsEachCommandByThreadIdAndKind() = runTest {
    val fake = FakeKernelDebugger()

    fake.step(SESSION, threadId = 1, command = SteppingCommand.NEXT)
    fake.step(SESSION, threadId = 2, command = SteppingCommand.STEP_IN)
    fake.step(SESSION, threadId = 3, command = SteppingCommand.STEP_OUT)
    fake.step(SESSION, threadId = 4, command = SteppingCommand.CONTINUE)

    assertEquals(
      listOf(
        1 to SteppingCommand.NEXT,
        2 to SteppingCommand.STEP_IN,
        3 to SteppingCommand.STEP_OUT,
        4 to SteppingCommand.CONTINUE,
      ),
      fake.stepRequests,
    )
  }

  @Test
  fun setBreakpointsRecordsTheLinesRequestedPerCodeId() = runTest {
    val fake = FakeKernelDebugger()
    val codeId = fake.dumpCell(SESSION, "x = 1\ny = 2\n")

    fake.setBreakpoints(SESSION, codeId, listOf(BreakpointRequest(1), BreakpointRequest(2)))

    assertEquals(listOf(BreakpointRequest(1), BreakpointRequest(2)), fake.breakpointsByCodeId[codeId])
  }
}
