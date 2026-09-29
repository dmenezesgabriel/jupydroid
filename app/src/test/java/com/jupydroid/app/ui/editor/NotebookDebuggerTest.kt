package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelDebugger
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.Breakpoint
import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.model.DebugStop
import com.jupydroid.app.domain.model.DebugVariableGroup
import com.jupydroid.app.domain.model.NotebookBreakpoints
import com.jupydroid.app.domain.port.BreakpointRequest
import com.jupydroid.app.domain.port.DebugEvent
import com.jupydroid.app.domain.port.DebugScope
import com.jupydroid.app.domain.port.DebugStackFrame
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.SteppingCommand
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The debugger's own first visible slice: a breakpoint toggled in the gutter, kept for as
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
   * The handshake itself: what a cell about to run under the debugger sends, in the
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
   * Stop detection: a `Stopped` debug event resolves to the kernel's own top stack
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
    assertNull(viewModel.uiState.value.debugStack)
    watcher.cancel()
  }

  /**
   * P6a-8a's own gate: a `Stopped` event chains `stackTrace -> scopes -> variables` **for the top
   * frame only** (JupyterLab's own default selection on a fresh stop,
   * `packages/debugger/src/panels/callstack/model.ts:31-44`), grouping variables by the scope
   * they came from rather than flattening them into one list.
   */
  @Test
  fun watchForStopChainsStackTraceThenScopesThenVariablesForTheTopFrame() = runTest {
    val fake =
      FakeKernelDebugger().apply {
        stackFramesToReturn = listOf(topFrame, innerFrame)
        scopesToReturn = listOf(localsScope)
        variablesByReference = mapOf(201 to localsVariables)
      }
    val recorder = RecordingKernelDebugger(fake)
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = recorder)
    val watcher = launch { viewModel.debugger.watchForStop(session, codeCell) }
    advanceUntilIdle()

    fake.emit(session, DebugEvent.Stopped(threadId = 9))
    advanceUntilIdle()

    assertEquals(listOf("stackTrace", "scopes", "variables"), recorder.calls)
    assertEquals(listOf(5), recorder.scopesRequestedForFrame)
    assertEquals(listOf(201), recorder.variablesRequestedForReference)
    assertEquals(expectedDebugStack, viewModel.uiState.value.debugStack)
    watcher.cancel()
  }

  /**
   * Wired into Run: a cell's debug session is armed **before** it executes - proved by
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

  /**
   * The crash a tablet run of P6a-8's own Maestro flow found: `startSession` used to answer
   * `Unit`, so [NotebookCellRuns] launched [NotebookDebugger.watchForStop] for *every* run
   * regardless of whether a debug session ever attached - and the real adapter's `events` throws
   * for a session that was never `attach`ed, crashing the app on the ordinary case, a cell with
   * no breakpoint at all. `startSession` now reports whether it attached, so the caller can skip
   * watching when it did not.
   */
  @Test
  fun startSessionReportsFalseWhenTheCellCarriesNoBreakpoint() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell))

    assertFalse(viewModel.debugger.startSession(session, codeCell))
  }

  @Test
  fun startSessionReportsTrueWhenTheCellCarriesABreakpoint() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = RecordingKernelDebugger())
    viewModel.debugger.toggle(codeCell.id, line = 2)

    assertTrue(viewModel.debugger.startSession(session, codeCell))
  }

  /**
   * [ThrowsIfUnattachedKernelDebugger] mirrors the real adapter's contract - `events` throws for a
   * session [NotebookDebugger.startSession] never attached. A cell with no breakpoint must never
   * reach it: this is the same regression the two tests above cover from `startSession`'s own
   * side, proved here from [NotebookCellRuns]'s - the run must still complete, not merely return a
   * boolean nobody acted on.
   */
  @Test
  fun runCellNeverWatchesForStopWhenTheCellCarriesNoBreakpoint() = runTest {
    val gateway = RecordingKernelGateway()
    val debugger = ThrowsIfUnattachedKernelDebugger()
    val viewModel = loadedEditor(gateway = gateway, cells = listOf(codeCell), debugger = debugger)

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(listOf(codeCell.id), gateway.executed)
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

  /**
   * P6a-9's own gate: nothing is stopped, so there is no thread to send a step/continue command
   * for - mirrors [NotebookCellRuns.stopRunningCell]'s own "no-op when the precondition isn't
   * met" shape for Interrupt.
   */
  @Test
  fun stepDoesNothingWhenNothingIsStopped() = runTest {
    val fake = FakeKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = fake)

    viewModel.debugger.step(SteppingCommand.NEXT)

    assertTrue(fake.stepRequests.isEmpty())
  }

  /** Sends the stopped thread's own id, for whichever command the toolbar asked for. */
  @Test
  fun stepSendsTheStoppedThreadsCommandToTheKernel() = runTest {
    val fake = FakeKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = fake)
    val watcher = launch { viewModel.debugger.watchForStop(session, codeCell) }
    advanceUntilIdle()
    fake.emit(session, DebugEvent.Stopped(threadId = 7))
    advanceUntilIdle()

    viewModel.debugger.step(SteppingCommand.STEP_IN)

    assertEquals(listOf(7 to SteppingCommand.STEP_IN), fake.stepRequests)
    watcher.cancel()
  }

  /**
   * Disables the toolbar for the length of the round trip - a second tap before the kernel
   * reports where it landed would send a stale command for a stop it has already left.
   */
  @Test
  fun stepMarksSteppingUntilTheNextEventResolves() = runTest {
    val fake = FakeKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = fake)
    val watcher = launch { viewModel.debugger.watchForStop(session, codeCell) }
    advanceUntilIdle()
    fake.emit(session, DebugEvent.Stopped(threadId = 7))
    advanceUntilIdle()

    viewModel.debugger.step(SteppingCommand.CONTINUE)
    assertTrue(viewModel.uiState.value.stepping)

    fake.emit(session, DebugEvent.Continued)
    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.stepping)
    watcher.cancel()
  }

  /**
   * P6a-10's own gate: debugpy keeps a breakpoint paused in its own memory until told otherwise,
   * so disarming the only breakpoint a cell ever had must still send a **clearing**
   * `setBreakpoints` next run - skipping the handshake entirely (as an always-empty cell does)
   * would leave a real kernel's own stale breakpoint armed forever, hanging every future run of
   * that cell with nothing in this app's own state saying why. Reproduced live on the tablet
   * before this test was written: disarming a verified breakpoint and re-running the cell threw
   * `DebugRequestFailedException: … "Session is already started"` on a kernel that had debugged it
   * before, and hung indefinitely (a real, unobserved `stopped` event) on one that hadn't yet
   * re-attached this session.
   */
  @Test
  fun startSessionStillAttachesOnceDisarmedAfterBeingArmedBefore() = runTest {
    val recorder = RecordingKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = recorder)
    viewModel.debugger.toggle(codeCell.id, line = 2)
    viewModel.debugger.startSession(session, codeCell)
    viewModel.debugger.toggle(codeCell.id, line = 2)

    val attached = viewModel.debugger.startSession(session, codeCell)

    assertTrue("a cell that ever had a breakpoint must still attach to clear it", attached)
    assertEquals(2, recorder.calls.count { it == "attach" })
  }

  @Test
  fun startSessionSendsAnEmptySetBreakpointsOnceDisarmed() = runTest {
    val fake = FakeKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = fake)
    viewModel.debugger.toggle(codeCell.id, line = 2)
    viewModel.debugger.startSession(session, codeCell)
    viewModel.debugger.toggle(codeCell.id, line = 2)

    viewModel.debugger.startSession(session, codeCell)

    assertEquals(emptyList<BreakpointRequest>(), fake.breakpointsByCodeId.values.last())
  }

  /**
   * P6a-11's own gate: Terminate restarts the kernel, which tears debugpy's own state down with
   * it - the debugger's own idea of "stopped" must go with it too, or the toolbar and the
   * stopped-line wash would keep naming a stop that no longer exists on a fresh kernel.
   */
  @Test
  fun clearStopResetsStoppedAtDebugStackAndStepping() = runTest {
    val fake = FakeKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = fake)
    val watcher = launch { viewModel.debugger.watchForStop(session, codeCell) }
    advanceUntilIdle()
    fake.emit(session, DebugEvent.Stopped(threadId = 7))
    advanceUntilIdle()

    viewModel.debugger.clearStop()

    assertNull(viewModel.uiState.value.stoppedAt)
    assertNull(viewModel.uiState.value.debugStack)
    assertFalse(viewModel.uiState.value.stepping)
    watcher.cancel()
  }

  /**
   * P6a-14's own gate: a compound variable's `variablesReference` fetches its children off the
   * same stopped session [step] already resumes on - the debugger's own bookkeeping, not a fresh
   * argument the panel would otherwise have no way to supply.
   */
  @Test
  fun expandFetchesAVariablesReferencesChildrenOnTheStoppedSession() = runTest {
    val fake = FakeKernelDebugger().apply { variablesByReference = mapOf(9 to listOf(childVariable)) }
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = fake)
    val watcher = launch { viewModel.debugger.watchForStop(session, codeCell) }
    advanceUntilIdle()
    fake.emit(session, DebugEvent.Stopped(threadId = 7))
    advanceUntilIdle()

    val children = viewModel.debugger.expand(9)

    assertEquals(listOf(childVariable), children)
    watcher.cancel()
  }

  @Test
  fun expandReturnsNothingWhenNothingIsStopped() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell))

    assertEquals(emptyList<DebugVariable>(), viewModel.debugger.expand(9))
  }

  /**
   * P6a-15's own gate: tapping a frame other than the top one re-chains `scopes -> variables` for
   * *that* frame's own id - the named Open risks gap [DebugStack]'s own doc pointed at since P6a-8.
   */
  @Test
  fun selectFrameAsksTheKernelForTheTappedFramesOwnScopes() = runTest {
    val fake = twoFrameFake()
    val recorder = RecordingKernelDebugger(fake)
    val (viewModel, watcher) = stoppedAtInnerFrame(fake, recorder)
    recorder.scopesRequestedForFrame.clear()

    viewModel.debugger.selectFrame(innerFrame.id)

    assertEquals(listOf(innerFrame.id), recorder.scopesRequestedForFrame)
    watcher.cancel()
  }

  /** The stopped-line wash and the panel's variables both move to the tapped frame's own answer. */
  @Test
  fun selectFrameMovesTheStoppedLineAndVariablesToTheTappedFrame() = runTest {
    val (viewModel, watcher) = stoppedAtInnerFrame(twoFrameFake())

    viewModel.debugger.selectFrame(innerFrame.id)

    val expectedStop = DebugStop(cellId = codeCell.id, line = innerFrame.line, threadId = 9)
    assertEquals(expectedStop, viewModel.uiState.value.stoppedAt)
    assertEquals(expectedDebugStack, viewModel.uiState.value.debugStack)
    watcher.cancel()
  }

  @Test
  fun selectFrameDoesNothingWhenNothingIsStopped() = runTest {
    val fake = FakeKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = fake)

    viewModel.debugger.selectFrame(5)

    assertNull(viewModel.uiState.value.stoppedAt)
    assertNull(viewModel.uiState.value.debugStack)
  }

  /** A tap naming a frame outside the current stack is ignored rather than clearing the stop. */
  @Test
  fun selectFrameDoesNothingForAFrameIdOutsideTheCurrentStack() = runTest {
    val fake = FakeKernelDebugger()
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = fake)
    val watcher = launch { viewModel.debugger.watchForStop(session, codeCell) }
    advanceUntilIdle()
    fake.emit(session, DebugEvent.Stopped(threadId = 7))
    advanceUntilIdle()
    val before = viewModel.uiState.value

    viewModel.debugger.selectFrame(999)

    assertEquals(before.stoppedAt, viewModel.uiState.value.stoppedAt)
    assertEquals(before.debugStack, viewModel.uiState.value.debugStack)
    watcher.cancel()
  }

  /**
   * The toolbar's own entry point: [NotebookKernel.step] (through [NotebookCellRuns.step]) reaches
   * the same [NotebookDebugger.step] the tests above cover directly - proving the wiring, not the
   * behavior a second time.
   */
  @Test
  fun kernelStepReachesTheDebuggerForARunThatIsStoppedMidExecution() = runTest {
    val fake = FakeKernelDebugger()
    val gateway = RecordingKernelGateway(runsUntilInterrupted = true)
    val viewModel = loadedEditor(gateway = gateway, cells = listOf(codeCell), debugger = fake)
    viewModel.debugger.toggle(codeCell.id, line = 1)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    fake.emit(runningSession, DebugEvent.Stopped(threadId = 3))
    advanceUntilIdle()

    viewModel.kernel.step(SteppingCommand.NEXT)
    advanceUntilIdle()

    assertEquals(listOf(3 to SteppingCommand.NEXT), fake.stepRequests)
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

  /**
   * [RecordingKernelGateway] delegates `connect` to a fresh `FakeKernelGateway` per test, so the
   * session under test is always its first mint.
   */
  private val runningSession = FakeKernelGateway.mints(sessionNumber = 1)

  private val session = KernelSession(
    id = "s1",
    spaceId = "base",
    kernelId = "k1",
    kernelName = "python3",
    serverSessionId = "ss1",
  )

  /**
   * A two-frame stack, scripted for [watchForStopChainsStackTraceThenScopesThenVariablesForTheTopFrame]
   * and for [selectFrame]'s own tests below.
   */
  private val topFrame = DebugStackFrame(id = 5, name = "<module>", line = 3, column = 1)
  private val innerFrame = DebugStackFrame(id = 6, name = "helper", line = 10, column = 1)
  private val localsScope = DebugScope(name = "Locals", variablesReference = 201, expensive = false)
  private val localsVariables = listOf(DebugVariable(name = "x", value = "41", type = "int", variablesReference = 0))

  private fun twoFrameFake() = FakeKernelDebugger().apply {
    stackFramesToReturn = listOf(topFrame, innerFrame)
    scopesToReturn = listOf(localsScope)
    variablesByReference = mapOf(201 to localsVariables)
  }

  /** Stops [codeCell] on [session] against [twoFrameFake]'s own stack, for [selectFrame]'s own tests. */
  private suspend fun TestScope.stoppedAtInnerFrame(
    fake: FakeKernelDebugger,
    debugger: KernelDebugger = fake,
  ): Pair<EditorViewModel, Job> {
    val viewModel = loadedEditor(cells = listOf(codeCell), debugger = debugger)
    val watcher = launch { viewModel.debugger.watchForStop(session, codeCell) }
    advanceUntilIdle()
    fake.emit(session, DebugEvent.Stopped(threadId = 9))
    advanceUntilIdle()
    return viewModel to watcher
  }

  /** A leaf inside some compound variable's own `variablesReference` - [expand]'s own fixture. */
  private val childVariable = DebugVariable(name = "0", value = "1", type = "int", variablesReference = 0)
  private val expectedDebugStack =
    DebugStack(frames = listOf(topFrame, innerFrame), groups = listOf(DebugVariableGroup("Locals", localsVariables)))
}
