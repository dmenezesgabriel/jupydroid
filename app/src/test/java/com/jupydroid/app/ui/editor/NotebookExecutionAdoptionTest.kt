@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.editor

import com.jupydroid.app.DocumentOpening
import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A screen that comes back to a notebook whose run was retained takes the run over: same kernel,
 * same state flow, fresh disk state underneath it, and the run's own teardown rules from then on.
 */
class NotebookExecutionAdoptionTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val key = NotebookExecutionKey(BASE_SPACE.id, DocumentOpening("nb-1"))

  @Test
  fun reopeningMidRunAdoptsTheRetainedRun() = runTest {
    val executions = NotebookExecutions()
    val runs = ActiveCellRuns()
    val documents = OpenDocuments()
    val gateway = RecordingKernelGateway(runsUntilInterrupted = true)
    val repository = RecordingNotebookRepository()
    val retained = detachMidRun(gateway, repository, executions, runs, documents)

    val second = reopenedEditor(gateway, repository, executions, runs, documents)

    assertSame("the kernel is the retained one", retained.kernel, second.kernel)
    assertNull("the holder was taken, not left to drain underneath the screen", executions.at(key))
    assertTrue(second.uiState.value.loaded)
    assertEquals(listOf("c1"), second.uiState.value.cells.map { it.id })
    assertEquals(ExecutionStatus.RUNNING, second.uiState.value.executions["c1"]?.status)
    assertTrue(second.uiState.value.kernelConnected)
    assertEquals(KernelStatus.BUSY, second.uiState.value.kernelStatus)
  }

  /**
   * The point of adopting rather than copying: an event emitted after the screen is back lands in
   * the screen that is showing it, Stop ends the run through the retained kernel, and the run's
   * checkpoint is written by the *reopened* document - proven by it landing in the second
   * repository while the cleared screen's own stays at its seed.
   */
  @Test
  fun liveEventsStopAndCheckpointReachTheReopenedScreen() = runTest {
    val executions = NotebookExecutions()
    val runs = ActiveCellRuns()
    val documents = OpenDocuments()
    val firstRepository = RecordingNotebookRepository()
    val secondRepository = RecordingNotebookRepository()
    val gateway = StreamingUntilInterruptedKernelGateway()
    detachMidRun(gateway, firstRepository, executions, runs, documents)

    val second = reopenedEditor(gateway, secondRepository, executions, runs, documents)
    assertTrue(outputsOf(second).any { it == "one\n" })
    gateway.letSecondOutputArrive()
    advanceUntilIdle()
    assertTrue("the live event reached the reopened screen", outputsOf(second).any { "two" in it })

    stopAndAssertCheckpoint(second, gateway, firstRepository, secondRepository)

    second.onCleared()
    advanceUntilIdle()
    assertEquals(1, gateway.disconnected.size)
  }

  /** `answerInput` reads the request off the adopted state and replies through the retained session. */
  @Test
  fun answerInputWorksAfterAdoption() = runTest {
    val executions = NotebookExecutions()
    val runs = ActiveCellRuns()
    val documents = OpenDocuments()
    val gateway = AskingUntilAnsweredKernelGateway()
    val repository = RecordingNotebookRepository()
    detachMidRun(gateway, repository, executions, runs, documents)

    val second = reopenedEditor(gateway, repository, executions, runs, documents)

    assertEquals(AskingUntilAnsweredKernelGateway.ASKED, second.uiState.value.inputRequests["c1"])
    second.kernel.answerInput("c1", "Ada")
    advanceUntilIdle()

    assertEquals(listOf(AskingUntilAnsweredKernelGateway.ASKED to "Ada"), gateway.replies)
    assertTrue(outputsOf(second).any { it == "hi Ada\n" })
    assertEquals(ExecutionStatus.STOPPED_OK, second.uiState.value.executions["c1"]?.status)
  }

  /** Fresh disk state is the document; the retained flow contributes only what the run owns. */
  @Test
  fun adoptionSeedsFreshDiskCellsAndKeepsTheRun() = runTest {
    val executions = NotebookExecutions()
    val runs = ActiveCellRuns()
    val documents = OpenDocuments()
    val gateway = RecordingKernelGateway(runsUntilInterrupted = true)
    val repository = RecordingNotebookRepository()
    detachMidRun(gateway, repository, executions, runs, documents)
    val grown = Notebook("nb-1", "Data", "python3", listOf(codeCell, secondCodeCell))

    val second = reopenedEditor(gateway, repository, executions, runs, documents, notebook = grown)

    assertEquals(listOf("c1", "c3"), second.uiState.value.cells.map { it.id })
    assertEquals(ExecutionStatus.RUNNING, second.uiState.value.executions["c1"]?.status)
  }

  /** An editor whose screen has just cleared mid-run, reduced to the holder it left behind. */
  private fun TestScope.detachMidRun(
    gateway: KernelGateway,
    repository: NotebookRepository,
    executions: NotebookExecutions,
    runs: ActiveCellRuns,
    documents: OpenDocuments,
  ): NotebookExecution {
    val first =
      loadedEditor(
        gateway = gateway,
        repository = repository,
        activeCellRuns = runs,
        applicationScope = this,
        openDocuments = documents,
        notebookExecutions = executions,
      )
    first.kernel.runCell(codeCell)
    advanceUntilIdle()
    first.onCleared()
    advanceUntilIdle()
    return checkNotNull(executions.at(key)) { "the run was handed to the registry" }
  }

  /** The screen coming back for the same `(Space, DocumentOpening)`. */
  private fun TestScope.reopenedEditor(
    gateway: KernelGateway,
    repository: NotebookRepository,
    executions: NotebookExecutions,
    runs: ActiveCellRuns,
    documents: OpenDocuments,
    notebook: Notebook? = null,
  ): EditorViewModel = loadedEditor(
    gateway = gateway,
    repository = repository,
    notebook = notebook,
    activeCellRuns = runs,
    applicationScope = this,
    openDocuments = documents,
    notebookExecutions = executions,
  )

  /** Stop ends the adopted run, and its checkpoint is the reopened document's rather than the cleared one's. */
  private fun TestScope.stopAndAssertCheckpoint(
    second: EditorViewModel,
    gateway: StreamingUntilInterruptedKernelGateway,
    firstRepository: RecordingNotebookRepository,
    secondRepository: RecordingNotebookRepository,
  ) {
    val writesBeforeStop = secondRepository.saveCount
    val firstWritesBeforeStop = firstRepository.saveCount
    second.kernel.stopRunningCell()
    advanceUntilIdle()

    assertEquals(ExecutionStatus.CANCELLED, second.uiState.value.executions["c1"]?.status)
    assertTrue("the run's checkpoint is the reopened document's", secondRepository.saveCount > writesBeforeStop)
    assertEquals("the cleared screen's repository is not written", firstWritesBeforeStop, firstRepository.saveCount)
    assertTrue("the adopted screen still owns the kernel", gateway.disconnected.isEmpty())
  }

  private fun outputsOf(viewModel: EditorViewModel): List<String> =
    viewModel.uiState.value.outputs["c1"].orEmpty().mapNotNull { (it as? CellOutput.Stream)?.text }

  /**
   * `one` lands, the cell then waits for a second emission and for the interrupt - the shape a
   * streaming cell has while it is still the screen's to watch.
   */
  private class StreamingUntilInterruptedKernelGateway : KernelGateway by FakeKernelGateway() {
    private val secondOutput = CompletableDeferred<Unit>()
    private val interrupted = CompletableDeferred<Unit>()
    val disconnected = mutableListOf<KernelSession>()

    override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> = flow {
      emit(KernelEvent.Output(cellId, CellOutput.Stream(StreamName.STDOUT, "one\n")))
      secondOutput.await()
      emit(KernelEvent.Output(cellId, CellOutput.Stream(StreamName.STDOUT, "two\n")))
      interrupted.await()
      emit(KernelEvent.Idle)
    }

    fun letSecondOutputArrive() = secondOutput.complete(Unit)

    override suspend fun interrupt(session: KernelSession) {
      interrupted.complete(Unit)
    }

    override suspend fun disconnect(session: KernelSession) {
      disconnected += session
    }
  }
}
