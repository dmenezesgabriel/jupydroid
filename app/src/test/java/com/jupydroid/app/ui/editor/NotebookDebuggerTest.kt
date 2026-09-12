package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.domain.model.NotebookBreakpoints
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The debugger's own first visible slice (P6a-6): a breakpoint toggled in the gutter, kept for as
 * long as the app runs rather than for as long as the ViewModel that toggled it does. No kernel
 * behaviour here yet - that starts at P6a-7a.
 */
class NotebookDebuggerTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun aCellStartsWithNoBreakpoints() = runTest {
    val viewModel = loadedEditor()

    assertEquals(emptyMap<String, List<Breakpoint>>(), viewModel.uiState.value.breakpointsByCellId)
  }

  @Test
  fun toggleArmsALineNotYetMarked() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell))

    viewModel.debugger.toggle(codeCell.id, line = 2)

    assertEquals(
      listOf(Breakpoint(line = 2)),
      viewModel.uiState.value.breakpointsByCellId[codeCell.id],
    )
  }

  @Test
  fun toggleOnAnArmedLineDisarmsIt() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell))
    viewModel.debugger.toggle(codeCell.id, line = 2)

    viewModel.debugger.toggle(codeCell.id, line = 2)

    assertEquals(emptyList<Breakpoint>(), viewModel.uiState.value.breakpointsByCellId[codeCell.id])
  }

  /**
   * The gate's own question: does a breakpoint survive the `EditorViewModel` that set it being
   * torn down and a new one built for the same notebook - which is what re-opening a notebook
   * after navigating away actually does (`NotebookBackStack` reveals rather than reopens).
   */
  @Test
  fun aBreakpointSurvivesTheViewModelBeingRebuiltForTheSameNotebook() = runTest {
    val shared = NotebookBreakpoints()
    val first = loadedEditor(cells = listOf(codeCell), notebookBreakpoints = shared)
    first.debugger.toggle(codeCell.id, line = 4)

    val second = loadedEditor(cells = listOf(codeCell), notebookBreakpoints = shared)

    assertEquals(
      listOf(Breakpoint(line = 4)),
      second.uiState.value.breakpointsByCellId[codeCell.id],
    )
  }

  @Test
  fun eachCellsBreakpointsAreKeptApart() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell))

    viewModel.debugger.toggle(codeCell.id, line = 1)

    assertEquals(null, viewModel.uiState.value.breakpointsByCellId[secondCodeCell.id])
  }

  /**
   * The handshake itself (P6a-7a): what a cell about to run under the debugger sends, in the
   * order DAP requires - attach before dumpCell, dumpCell before the setBreakpoints it names a
   * source for, setBreakpoints before configurationDone tells the kernel setup is finished.
   */
  @Test
  fun startSessionSendsTheHandshakeInOrderWhenTheCellCarriesABreakpoint() = runTest {
    val recorder = RecordingKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = recorder)
    viewModel.debugger.toggle(codeCell.id, line = 2)

    viewModel.debugger.startSession(session, codeCell)

    assertEquals(listOf("attach", "dumpCell", "setBreakpoints", "configurationDone"), recorder.calls)
  }

  @Test
  fun startSessionSendsNothingWhenTheCellCarriesNoBreakpoint() = runTest {
    val recorder = RecordingKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = recorder)

    viewModel.debugger.startSession(session, codeCell)

    assertTrue(recorder.calls.isEmpty())
  }

  @Test
  fun startSessionMarksTheBreakpointVerifiedFromTheKernelsReply() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = RecordingKernelDebugger())
    viewModel.debugger.toggle(codeCell.id, line = 2)

    viewModel.debugger.startSession(session, codeCell)

    assertEquals(
      listOf(Breakpoint(line = 2, verified = true, codeId = "/fake/debug-cells/cell-0.py")),
      viewModel.uiState.value.breakpointsByCellId[codeCell.id],
    )
  }

  private val session = KernelSession(id = "s1", spaceId = "base", kernelId = "k1", serverSessionId = "ss1")
}
