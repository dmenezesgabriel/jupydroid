package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelDebugger
import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.domain.port.BreakpointRequest
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * A breakpoint's own condition (P6a-16), split out of [NotebookDebuggerTest] once it crossed the
 * file-length gate - a file-length split, not a change in responsibility. Still
 * [NotebookDebugger]'s own [NotebookDebugger.setCondition] and [NotebookDebugger.startSession]
 * behavior, not a different collaborator.
 */
class NotebookDebuggerConditionTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val session = KernelSession(
    id = "s1",
    spaceId = "base",
    kernelId = "k1",
    kernelName = "python3",
    serverSessionId = "ss1",
  )

  /** A condition set on an armed line reaches `EditorUiState` immediately. */
  @Test
  fun setConditionUpdatesTheBreakpointInUiState() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell))
    viewModel.debugger.toggle(codeCell.id, line = 2)

    viewModel.debugger.setCondition(codeCell.id, line = 2, condition = "x > 10")

    assertEquals(
      listOf(Breakpoint(line = 2, condition = "x > 10")),
      viewModel.uiState.value.breakpointsByCellId[codeCell.id],
    )
  }

  /** The handshake's own `setBreakpoints` call carries each armed line's own condition, not just its number. */
  @Test
  fun startSessionSendsEachBreakpointsOwnCondition() = runTest {
    val fake = FakeKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = fake)
    viewModel.debugger.toggle(codeCell.id, line = 2)
    viewModel.debugger.setCondition(codeCell.id, line = 2, condition = "x > 10")

    viewModel.debugger.startSession(session, codeCell)

    assertEquals(
      listOf(BreakpointRequest(line = 2, condition = "x > 10")),
      fake.breakpointsByCodeId.values.single(),
    )
  }

  /** The verified reply carries no condition of its own - `startSession` must not drop the one asked for. */
  @Test
  fun startSessionPreservesEachBreakpointsConditionInTheVerifiedResult() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = FakeKernelDebugger())
    viewModel.debugger.toggle(codeCell.id, line = 2)
    viewModel.debugger.setCondition(codeCell.id, line = 2, condition = "x > 10")

    viewModel.debugger.startSession(session, codeCell)

    assertEquals(
      listOf(Breakpoint(line = 2, verified = true, codeId = "/fake/debug-cells/cell-0.py", condition = "x > 10")),
      viewModel.uiState.value.breakpointsByCellId[codeCell.id],
    )
  }
}
