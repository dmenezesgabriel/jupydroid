package com.jupydroid.app.ui.console

import androidx.compose.foundation.text.input.insert
import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.ui.editor.ExecutionStatus
import com.jupydroid.app.ui.editor.NotebookKernelSession
import com.jupydroid.app.ui.editor.RecordingKernelGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Submitting input and running it through the notebook's shared kernel session - P6b-4a.
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

  private fun consoleViewModel(gateway: KernelGateway): ConsoleViewModel {
    val session = NotebookKernelSession(gateway, SPACE, attachedTo = ATTACHED_SESSION) { "Analysis.ipynb" }
    return ConsoleViewModel(gateway, session)
  }

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
    val ATTACHED_SESSION = KernelSession(id = "s1", spaceId = "base", kernelId = "k1", serverSessionId = "ss1")
  }
}

/** Raises on whatever it is asked to run, regardless of the id `ConsoleViewModel` mints for it. */
private class AlwaysRaisingKernelGateway : KernelGateway by FakeKernelGateway() {
  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> = flow {
    emit(KernelEvent.Output(cellId, CellOutput.Error("ValueError", "boom", emptyList())))
    emit(KernelEvent.Idle)
  }
}
