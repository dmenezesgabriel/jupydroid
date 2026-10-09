package com.jupydroid.app.ui.console

import androidx.compose.foundation.text.input.insert
import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.space.KernelLifecycleException
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.KernelInputRequest
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.SessionType
import com.jupydroid.app.ui.editor.AskingUntilAnsweredKernelGateway
import com.jupydroid.app.ui.editor.ExecutionStatus
import com.jupydroid.app.ui.editor.NotebookKernelSession
import com.jupydroid.app.ui.editor.RecordingKernelGateway
import com.jupydroid.app.ui.editor.StaleSessionKernelGateway
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Submitting input and running it through the notebook's shared kernel session.
 *
 * Against [FakeKernelGateway]/[RecordingKernelGateway], the same doubles `EditorViewModelTest`
 * runs a cell's own execution against - a Console entry runs exactly the way a cell does, through
 * [NotebookKernelSession.reopeningIfStale].
 */
class ConsoleViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun submit_appendsARunningEntryImmediately() = runTest {
    val gateway = RecordingKernelGateway(runsUntilInterrupted = true)
    val viewModel = consoleViewModel(gateway)

    viewModel.input.edit { insert(0, "1 + 1") }
    viewModel.submit()
    advanceUntilIdle()

    val entry = viewModel.entries.value.single()
    assertEquals("1 + 1", entry.input)
    assertEquals(ExecutionStatus.RUNNING, entry.status)

    gateway.interrupt(ATTACHED_SESSION)
    advanceUntilIdle()
  }

  @Test
  fun submit_clearsTheInputField() = runTest {
    val viewModel = consoleViewModel(FakeKernelGateway())

    viewModel.input.edit { insert(0, "1 + 1") }
    viewModel.submit()
    advanceUntilIdle()

    assertEquals("", viewModel.input.text.toString())
  }

  @Test
  fun submit_foldsTheGatewaysOutputAndCountIntoTheEntry() = runTest {
    val viewModel = consoleViewModel(FakeKernelGateway())

    viewModel.input.edit { insert(0, "1 + 1") }
    viewModel.submit()
    advanceUntilIdle()

    val entry = viewModel.entries.value.single()
    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "1 + 1")), entry.outputs)
    assertEquals(1, entry.executionCount)
    assertEquals(ExecutionStatus.STOPPED_OK, entry.status)
  }

  @Test
  fun submit_resolvesToStoppedErrorWhenTheKernelRaises() = runTest {
    val viewModel = consoleViewModel(AlwaysRaisingKernelGateway())

    viewModel.input.edit { insert(0, "raise ValueError('boom')") }
    viewModel.submit()
    advanceUntilIdle()

    assertEquals(ExecutionStatus.STOPPED_ERROR, viewModel.entries.value.single().status)
  }

  @Test
  fun submit_doesNothingOnBlankInput() = runTest {
    val viewModel = consoleViewModel(FakeKernelGateway())

    viewModel.input.edit { insert(0, "   ") }
    viewModel.submit()
    advanceUntilIdle()

    assertTrue(viewModel.entries.value.isEmpty())
  }

  @Test
  fun submit_doesNothingWhileTheLastEntryIsStillRunning() = runTest {
    val gateway = RecordingKernelGateway(runsUntilInterrupted = true)
    val viewModel = consoleViewModel(gateway)
    viewModel.input.edit { insert(0, "1 + 1") }
    viewModel.submit()
    advanceUntilIdle()

    viewModel.input.edit { insert(0, "2 + 2") }
    viewModel.submit()
    advanceUntilIdle()

    assertEquals(1, viewModel.entries.value.size)
    assertEquals("2 + 2", viewModel.input.text.toString())

    gateway.interrupt(ATTACHED_SESSION)
    advanceUntilIdle()
  }

  @Test
  fun recallPreviousEntry_writesTheLastRunSourceIntoTheField() = runTest {
    val viewModel = consoleViewModel(FakeKernelGateway())
    viewModel.input.edit { insert(0, "1 + 1") }
    viewModel.submit()
    advanceUntilIdle()

    viewModel.recallPreviousEntry()
    advanceUntilIdle()

    assertEquals("1 + 1", viewModel.input.text.toString())
  }

  @Test
  fun recallNextEntry_returnsToWhatWasBeingTypedAtTheWalksEnd() = runTest {
    val viewModel = consoleViewModel(FakeKernelGateway())
    viewModel.input.edit { insert(0, "1 + 1") }
    viewModel.submit()
    advanceUntilIdle()

    viewModel.input.edit { insert(0, "typing") }
    viewModel.recallPreviousEntry()
    advanceUntilIdle()
    viewModel.recallNextEntry()
    advanceUntilIdle()

    assertEquals("typing", viewModel.input.text.toString())
  }

  @Test
  fun inputEdited_endsTheWalkSoTheNextRecallStartsOverAtTheNewestEntry() = runTest {
    val viewModel = consoleViewModel(FakeKernelGateway())
    viewModel.input.edit { insert(0, "1 + 1") }
    viewModel.submit()
    advanceUntilIdle()
    viewModel.input.edit { insert(0, "2 + 2") }
    viewModel.submit()
    advanceUntilIdle()

    viewModel.recallPreviousEntry()
    advanceUntilIdle()
    // A genuine user edit, not the recall's own write - `sourceReported` ignores the latter.
    viewModel.input.edit { insert(length, " # edited") }
    viewModel.inputEdited(viewModel.input.text.toString())
    viewModel.recallPreviousEntry()
    advanceUntilIdle()

    assertEquals("2 + 2", viewModel.input.text.toString())
  }

  /**
   * `console:interrupt-kernel`'s mobile stand-in - `NotebookCellRuns.stopRunningCell`'s own
   * pattern, applied to the entry this Console is running rather than to `runningCellId`: the
   * kernel reports a `KeyboardInterrupt` either way, so [ExecutionStatus.CANCELLED] is recorded
   * locally rather than read back off the output, exactly as the notebook's own Stop does.
   */
  @Test
  fun interrupt_marksTheRunningEntryCancelled() = runTest {
    val gateway = RecordingKernelGateway(runsUntilInterrupted = true)
    val viewModel = consoleViewModel(gateway)
    viewModel.input.edit { insert(0, "1 + 1") }
    viewModel.submit()
    advanceUntilIdle()

    viewModel.interrupt()
    advanceUntilIdle()

    assertEquals(ExecutionStatus.CANCELLED, viewModel.entries.value.single().status)
    assertEquals(1, gateway.interruptCount)
  }

  /**
   * The refusal lands *before* the flow exists - `reopeningIfStale` resolves the session ahead
   * of `execute`, so a `connect` that fails (a refused pick, a server that never came up) has to
   * reach the same error output a refused `execute` does rather than escape the launch.
   */
  @Test
  fun submit_resolvesToStoppedErrorWhenConnectingIsRefused() = runTest {
    val gateway = RefusingToConnectKernelGateway()
    val session = NotebookKernelSession(gateway, SPACE) { "Analysis.ipynb" }
    val viewModel = ConsoleViewModel(gateway, session)
    viewModel.input.edit { insert(0, "1 + 1") }

    viewModel.submit()
    advanceUntilIdle()

    val entry = viewModel.entries.value.single()
    assertEquals(ExecutionStatus.STOPPED_ERROR, entry.status)
    assertTrue("the refusal is an output, not a crash", entry.outputs.any { it is CellOutput.Error })
  }

  /**
   * `interrupt` on a session whose server is already gone: there is no kernel left to interrupt,
   * and the refusal must not escape the launch - the dead session is forgotten so the next
   * [submit] reconnects, the landing `session.disposed` already owns.
   */
  @Test
  fun interrupt_onASessionWhoseServerWasReplaced_forgetsIt() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 0).also { it.holdRuns() }
    val session = NotebookKernelSession(gateway, SPACE) { "Analysis.ipynb" }
    session.open()
    val viewModel = ConsoleViewModel(gateway, session)
    viewModel.input.edit { insert(0, "1 + 1") }
    viewModel.submit()
    advanceUntilIdle()
    gateway.serverWasReplaced()

    viewModel.interrupt()
    advanceUntilIdle()

    assertNull("the dead session is forgotten - the next run reconnects", session.opened)

    gateway.releaseRuns()
    advanceUntilIdle()
  }

  @Test
  fun interrupt_doesNothingWhenNothingIsRunning() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = consoleViewModel(gateway)

    viewModel.interrupt()
    advanceUntilIdle()

    assertEquals(0, gateway.interruptCount)
  }

  @Test
  fun clear_dropsEveryEntry() = runTest {
    val viewModel = consoleViewModel(FakeKernelGateway())
    viewModel.input.edit { insert(0, "1 + 1") }
    viewModel.submit()
    advanceUntilIdle()

    viewModel.clear()

    assertTrue(viewModel.entries.value.isEmpty())
  }

  @Test
  fun clear_keepsTheSessionSoTheNextSubmitRunsWithoutReconnecting() = runTest {
    val gateway = FakeKernelGateway()
    val viewModel = consoleViewModel(gateway)
    viewModel.input.edit { insert(0, "1 + 1") }
    viewModel.submit()
    advanceUntilIdle()
    viewModel.clear()

    viewModel.input.edit { insert(0, "2 + 2") }
    viewModel.submit()
    advanceUntilIdle()

    assertEquals("2 + 2", viewModel.entries.value.single().input)
  }

  /**
   * The Running panel's shutdown ends the session the Console shares with its notebook, and
   * the Console's own holder has to forget it too - kept, the next submit would run against a
   * session the server already deleted rather than connecting a fresh one.
   */
  @Test
  fun aSessionEndedFromTheRunningPanelIsForgotten() = runTest {
    val registry = KernelSessionRegistry()
    val gateway = FakeKernelGateway(registry)
    val minted = gateway.connect(SPACE, "Analysis.ipynb")
    val session = NotebookKernelSession(gateway, SPACE, attachedTo = minted) { "Analysis.ipynb" }
    ConsoleViewModel(gateway, session, FakeKernelSessions(registry))
    advanceUntilIdle() // the collector subscribes before the emit - `ended` replays nothing

    FakeKernelSessions(registry).shutdown(minted.serverSessionId)
    advanceUntilIdle()

    assertNull("the shared session is gone - the next run reconnects", session.opened)
  }

  /**
   * `kernelChanged` beside `disposed`: the notebook whose session this Console shares
   * can swap the kernel under it (`PATCH api/sessions` keeps the session id), and the copy held
   * here has to follow - the old kernel id points at a process the server already shut down.
   */
  @Test
  fun aKernelChangeOnTheSharedSessionIsTakenUp() = runTest {
    val registry = KernelSessionRegistry()
    val gateway = FakeKernelGateway(registry)
    val minted = gateway.connect(SPACE, "Analysis.ipynb")
    val session = NotebookKernelSession(gateway, SPACE, attachedTo = minted) { "Analysis.ipynb" }
    ConsoleViewModel(gateway, session, FakeKernelSessions(registry))
    advanceUntilIdle()

    val changed = gateway.changeKernel(minted, KernelChoice.Spec("p8g"))
    advanceUntilIdle()

    assertEquals("the console now holds the rebound session", changed, session.opened)
  }

  /** An entry blocked in `input()` holds the prompt and keeps running until it is answered. */
  @Test
  fun submit_holdsTheInputRequestWhileTheEntryRuns() = runTest {
    val viewModel = consoleViewModel(AskingUntilAnsweredKernelGateway())

    submitted(viewModel, "name = input('Name?')")

    val entry = viewModel.entries.value.single()
    assertEquals(AskingUntilAnsweredKernelGateway.ASKED, entry.inputRequest)
    assertEquals(ExecutionStatus.RUNNING, entry.status)
  }

  /** The answer reaches the kernel, and the entry keeps the echo ahead of what printed with it. */
  @Test
  fun answerInput_repliesAndEchoesTheAnswer() = runTest {
    val gateway = AskingUntilAnsweredKernelGateway()
    val viewModel = consoleViewModel(gateway)
    submitted(viewModel, "name = input('Name?')")

    viewModel.answerInput(viewModel.entries.value.single().id, "Ada")
    advanceUntilIdle()

    val entry = viewModel.entries.value.single()
    assertEquals(listOf(AskingUntilAnsweredKernelGateway.ASKED to "Ada"), gateway.replies)
    assertEquals(
      listOf(CellOutput.Stream(StreamName.STDIN, "Name? Ada\n"), CellOutput.Stream(StreamName.STDOUT, "hi Ada\n")),
      entry.outputs,
    )
    assertNull(entry.inputRequest)
    assertEquals(ExecutionStatus.STOPPED_OK, entry.status)
  }

  /** Interrupting an entry that is asking ends it, and nothing is left to answer. */
  @Test
  fun interrupt_closesThePromptOfTheEntryItStops() = runTest {
    val viewModel = consoleViewModel(AskingUntilAnsweredKernelGateway())
    submitted(viewModel, "input('Wait?')")

    viewModel.interrupt()
    advanceUntilIdle()

    val entry = viewModel.entries.value.single()
    assertNull(entry.inputRequest)
    assertEquals(ExecutionStatus.CANCELLED, entry.status)
  }

  /** A `\r` progress bar leaves its last frame, and the print after it joins the same block. */
  @Test
  fun submit_aProgressBarLeavesOnlyItsLastFrame() = runTest {
    val viewModel = consoleViewModel(ProgressBarKernelGateway())

    submitted(viewModel, "progress()")

    assertEquals(
      listOf(CellOutput.Stream(StreamName.STDOUT, "100% done\na\n")),
      viewModel.entries.value.single().outputs,
    )
  }

  /** Two prompts answered in a row leave one echo, as JupyterLab's stdin widget adds it through `_add`. */
  @Test
  fun answerInput_twoPromptsInARowFoldIntoOneEcho() = runTest {
    val viewModel = consoleViewModel(AskingTwiceKernelGateway())
    submitted(viewModel, "input('Name?'); input('Age?')")

    viewModel.answerInput(viewModel.entries.value.single().id, "Ada")
    advanceUntilIdle()
    viewModel.answerInput(viewModel.entries.value.single().id, "36")
    advanceUntilIdle()

    assertEquals(
      listOf(CellOutput.Stream(StreamName.STDIN, "Name? Ada\nAge? 36\n")),
      viewModel.entries.value.single().outputs,
    )
  }

  private fun TestScope.submitted(viewModel: ConsoleViewModel, source: String) {
    viewModel.input.edit { insert(0, source) }
    viewModel.submit()
    advanceUntilIdle()
  }

  private fun consoleViewModel(gateway: KernelGateway): ConsoleViewModel {
    val session = NotebookKernelSession(gateway, SPACE, attachedTo = ATTACHED_SESSION) { "Analysis.ipynb" }
    return ConsoleViewModel(gateway, session)
  }

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
    val ATTACHED_SESSION = KernelSession(
      id = "s1",
      spaceId = "base",
      kernelId = "k1",
      kernelName = "python3",
      serverSessionId = "ss1",
    )
  }
}

/** Raises on whatever it is asked to run, regardless of the id `ConsoleViewModel` mints for it. */
private class AlwaysRaisingKernelGateway : KernelGateway by FakeKernelGateway() {
  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> = flow {
    emit(KernelEvent.Output(cellId, CellOutput.Error("ValueError", "boom", emptyList())))
    emit(KernelEvent.Idle)
  }
}

/** Never connects - the refusal a `connect` answer carries when the server never came up. */
private class RefusingToConnectKernelGateway : KernelGateway by FakeKernelGateway() {
  override suspend fun connect(
    space: Space,
    documentPath: String,
    kernel: KernelChoice?,
    sessionType: SessionType?,
  ): KernelSession = throw KernelLifecycleException("POST api/sessions refused for $documentPath")
}

/** Repaints a `\r` progress bar a chunk at a time, then prints a line after it. */
private class ProgressBarKernelGateway : KernelGateway by FakeKernelGateway() {
  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> = flow {
    listOf("\r0%", "\r50%", "\r100%", " done\n", "a\n").forEach { chunk ->
      emit(KernelEvent.Output(cellId, CellOutput.Stream(StreamName.STDOUT, chunk)))
    }
    emit(KernelEvent.Idle)
  }
}

/** Blocks in `input()` twice in a row, waiting for each answer before asking the next. */
private class AskingTwiceKernelGateway : KernelGateway by FakeKernelGateway() {
  private val answers = Channel<String>(Channel.UNLIMITED)

  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> = flow {
    listOf("Name?", "Age?").forEach { prompt ->
      emit(KernelEvent.InputRequested(cellId, KernelInputRequest(prompt, password = false, requestHeader = "{}")))
      answers.receive()
    }
    emit(KernelEvent.Idle)
  }

  override suspend fun replyInput(session: KernelSession, request: KernelInputRequest, value: String) {
    answers.send(value)
  }
}
