package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelDebugger
import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.domain.model.DebugStop
import com.jupydroid.app.domain.model.NotebookBreakpoints
import com.jupydroid.app.domain.port.DebugEvent
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The debugger's own first visible slice (P6a-6): a breakpoint toggled in the gutter, kept for as
 * long as the app runs rather than for as long as the ViewModel that toggled it does. The
 * handshake itself is P6a-7a, and wiring it into Run plus reflecting a stop is P6a-7b.
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

  /**
   * Stop detection (P6a-7b): a `Stopped` debug event resolves to the kernel's own top stack
   * frame's line - `Stopped` alone carries only a thread id, not a line - and reaches
   * [EditorUiState.stoppedAt] for as long as [NotebookDebugger.watchForStop] is being collected.
   */
  @Test
  fun watchForStopReflectsAStoppedEventsLineFromTheTopStackFrame() = runTest {
    val fake = FakeKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = fake)
    val watcher = launch { viewModel.debugger.watchForStop(session, codeCell) }
    advanceUntilIdle()

    fake.emit(session, DebugEvent.Stopped(threadId = 7))
    advanceUntilIdle()

    assertEquals(
      DebugStop(cellId = codeCell.id, line = 1, threadId = 7),
      viewModel.uiState.value.stoppedAt,
    )
    watcher.cancel()
  }

  @Test
  fun watchForStopClearsOnAContinuedEvent() = runTest {
    val fake = FakeKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = fake)
    val watcher = launch { viewModel.debugger.watchForStop(session, codeCell) }
    advanceUntilIdle()
    fake.emit(session, DebugEvent.Stopped(threadId = 7))
    advanceUntilIdle()

    fake.emit(session, DebugEvent.Continued)
    advanceUntilIdle()

    assertNull(viewModel.uiState.value.stoppedAt)
    watcher.cancel()
  }

  /**
   * Wired into Run (P6a-7b): a cell's debug session is armed **before** it executes - proved by
   * blocking `attach` and finding the kernel has not been asked to run anything yet.
   */
  @Test
  fun runCellArmsBreakpointsBeforeItExecutes() = runTest {
    val gateway = RecordingKernelGateway()
    val debugger = BlockingAttachKernelDebugger()
    val viewModel = loadedEditor(gateway = gateway, cells = listOf(codeCell), debugger = debugger)
    viewModel.debugger.toggle(codeCell.id, line = 1)

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    assertTrue("execute must wait for the debug session to attach", gateway.executed.isEmpty())

    debugger.letAttachProceed()
    advanceUntilIdle()

    assertEquals(listOf(codeCell.id), gateway.executed)
  }

  /**
   * A cell with no breakpoint runs without ever attaching a debug session - [startSession]'s own
   * early return still holds once Run calls it, so an ordinary run pays no extra round trip.
   */
  @Test
  fun runCellNeverAttachesWhenTheCellCarriesNoBreakpoint() = runTest {
    val gateway = RecordingKernelGateway()
    val fake = FakeKernelDebugger()
    val viewModel = loadedEditor(gateway = gateway, cells = listOf(codeCell), debugger = fake)

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(listOf(codeCell.id), gateway.executed)
    assertTrue(fake.attachedSessions.isEmpty())
  }

  /** The stop reaches [EditorUiState.stoppedAt] while the run it belongs to is still in flight. */
  @Test
  fun aRunReflectsAStopThatArrivesWhileTheCellIsExecuting() = runTest {
    val fake = FakeKernelDebugger()
    val gateway = RecordingKernelGateway(runsUntilInterrupted = true)
    val viewModel = loadedEditor(gateway = gateway, cells = listOf(codeCell), debugger = fake)
    viewModel.debugger.toggle(codeCell.id, line = 1)

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    fake.emit(runningSession, DebugEvent.Stopped(threadId = 3))
    advanceUntilIdle()

    assertEquals(
      DebugStop(cellId = codeCell.id, line = 1, threadId = 3),
      viewModel.uiState.value.stoppedAt,
    )
  }

  /** [EditorUiState.stoppedLineOf] is what the gutter reads (P6a-7b-2) - scoped to its own cell. */
  @Test
  fun stoppedLineOfNamesOnlyTheStoppedCell() = runTest {
    val fake = FakeKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell), debugger = fake)
    val watcher = launch { viewModel.debugger.watchForStop(session, codeCell) }
    advanceUntilIdle()

    fake.emit(session, DebugEvent.Stopped(threadId = 1))
    advanceUntilIdle()

    assertEquals(1, viewModel.uiState.value.stoppedLineOf(codeCell.id))
    assertNull(viewModel.uiState.value.stoppedLineOf(secondCodeCell.id))
    watcher.cancel()
  }

  /** [RecordingKernelGateway] delegates `connect` to `FakeKernelGateway`, whose session is fixed. */
  private val runningSession =
    KernelSession(id = "fake-base", spaceId = "base", kernelId = "python3", serverSessionId = "fake-session-base")

  private val session = KernelSession(id = "s1", spaceId = "base", kernelId = "k1", serverSessionId = "ss1")
}
