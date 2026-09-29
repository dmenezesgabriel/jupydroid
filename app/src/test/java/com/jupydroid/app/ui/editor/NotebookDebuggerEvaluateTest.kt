package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelDebugger
import com.jupydroid.app.domain.port.DebugEvent
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * An ad hoc expression, run against the top frame while stopped - split out of
 * [NotebookDebuggerTest] once it crossed the file-length gate, [NotebookDebuggerConditionTest]'s
 * own reason. Still [NotebookDebugger.evaluate], not a different collaborator.
 */
class NotebookDebuggerEvaluateTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val session = KernelSession(
    id = "s1",
    spaceId = "base",
    kernelId = "k1",
    kernelName = "python3",
    serverSessionId = "ss1",
  )

  @Test
  fun evaluateReturnsNothingWhenNothingIsStopped() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell))

    assertNull(viewModel.debugger.evaluate("1 + 1"))
  }

  /** Runs against the **top** frame's own id - this app tracks no other "current frame" of its own. */
  @Test
  fun evaluateSendsTheExpressionAgainstTheTopFrame() = runTest {
    val fake = FakeKernelDebugger().apply { evaluateResult = DebugVariable("", "42", "int", 0) }
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = fake)
    val watcher = launch { viewModel.debugger.watchForStop(session, codeCell) }
    advanceUntilIdle()
    fake.emit(session, DebugEvent.Stopped(threadId = 7))
    advanceUntilIdle()

    val result = viewModel.debugger.evaluate("x + 1")

    assertEquals(listOf("x + 1" to 1), fake.evaluateRequests)
    assertEquals(DebugVariable("x + 1", "42", "int", 0), result)
    watcher.cancel()
  }
}
