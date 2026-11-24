@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.editor

import androidx.lifecycle.ViewModelStore
import com.jupydroid.app.DocumentOpening
import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What a retained run owes the edges its screen no longer covers: the write it still has in
 * flight when it lets go, another notebook's run, a setting flipped while it is gone, its
 * session ending elsewhere, and its file moving or dying underneath it.
 *
 * The file-follow cases clear through a real [ViewModelStore] rather than `onCleared()` alone:
 * only the store's clear cancels `viewModelScope`, which is what makes the document's own
 * follow dead - without it the screen's follow would answer the announcement and the holder's
 * would be indistinguishable from it.
 */
class NotebookExecutionDetachedEdgesTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /**
   * `finishedRun` lands in the state before the checkpoint's write returns, so a run that just
   * ended can still owe the file a write - with `runInFlight` already false and `dirty` cleared
   * or never set. Leaving in that window used to close the kernel out from under the write.
   */
  @Test
  fun aRunWhoseCheckpointIsStillWritingIsRetained() = runTest {
    val stage = Stage()
    val repository = SlowToSaveNotebookRepository()
    val gateway = stage.gateway()
    val viewModel = openIn(stage, gateway = gateway, repository = repository)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    repository.holdSaves()
    viewModel.kernel.stopRunningCell()
    advanceUntilIdle()
    detach(viewModel)
    advanceUntilIdle()

    assertNotNull(stage.retained())
    assertTrue(gateway.disconnected.isEmpty())
    repository.releaseNewest()
    advanceUntilIdle()

    assertNull(stage.retained())
    assertEquals(1, gateway.disconnected.size)
  }

  /**
   * The drain waits on *this* notebook's run work: a second notebook's run in flight is its own
   * business, and must not hold this one's kernel open after its own run has ended.
   */
  @Test
  fun anotherNoteBooksRunDoesNotHoldThisOnesDrain() = runTest {
    val runs = ActiveCellRuns()
    val stageA = Stage(runs)
    val stageB = Stage(runs)
    val gatewayA = stageA.gateway()
    val gatewayB = stageB.gateway()
    val viewModelA = openIn(stageA, "a.ipynb", gatewayA, RecordingNotebookRepository())
    val viewModelB = openIn(stageB, "b.ipynb", gatewayB, RecordingNotebookRepository())
    viewModelA.kernel.runCell(codeCell)
    viewModelB.kernel.runCell(codeCell)
    advanceUntilIdle()
    detach(viewModelA)
    advanceUntilIdle()
    assertNotNull(stageA.retained("a.ipynb"))
    viewModelA.kernel.stopRunningCell()
    advanceUntilIdle()
    assertNull("A's own run ending is A's drain, whatever B is doing", stageA.retained("a.ipynb"))
    assertEquals(1, gatewayA.disconnected.size)
    assertEquals("B's run is still counted", 1, runs.inFlight.value)
  }

  /**
   * `kernelShutdown` is read when the session context disposes, not when the document opened -
   * and for a detached run that dispose is the drain. A setting flipped while the run was gone
   * is honoured by the close that finally comes.
   */
  @Test
  fun shutdownKernelOnCloseIsReadWhenTheDetachedRunEnds() = runTest {
    val stage = Stage()
    val gateway = stage.gateway()
    val (viewModel, _) = detachedIn(stage, repository = RecordingNotebookRepository(), gateway = gateway)

    stage.settings.update(EditorSettings(shutdownKernelOnClose = true))
    viewModel.kernel.stopRunningCell()
    advanceUntilIdle()

    assertEquals("the setting flipped while detached is honoured at the drain", 1, gateway.shutDown.size)
    assertTrue(gateway.disconnected.isEmpty())
    assertNull(stage.retained())
  }

  /**
   * `session.disposed`'s landing does not need a screen: the ended session is forgotten, the
   * indicator goes to No Kernel, and the run's drain still ends cleanly - a session already
   * gone is not disconnected a second time.
   */
  @Test
  fun aSessionEndedWhileDetachedLandsOnNoKernelAndStillDrains() = runTest {
    val stage = Stage()
    val gateway = stage.gateway()
    val (_, retained) = detachedIn(stage, repository = RecordingNotebookRepository(), gateway = gateway)
    val serverSessionId = stage.kernelSessions.running().single().serverSessionId

    stage.kernelSessions.shutdown(serverSessionId)
    advanceUntilIdle()

    assertEquals("the ended session lands as No Kernel while detached", false, retained.uiState.value.kernelConnected)
    assertNotNull("the run it was feeding is still in flight", stage.retained())
    gateway.endHeldRuns()
    advanceUntilIdle()

    assertNull(stage.retained())
    assertTrue("a session already ended is not disconnected again", gateway.disconnected.isEmpty())
    assertTrue(gateway.shutDown.isEmpty())
  }

  /**
   * `Context._onFileChanged` does not die with the screen: a rename announced while the run is
   * detached moves the document's path, the session's `setPath`, and where the run's checkpoint
   * writes - so the file is never written back under its old name.
   */
  @Test
  fun aRenameWhileDetachedMovesTheRunWithIt() = runTest {
    val stage = Stage()
    val gateway = stage.gateway()
    val repository = SlowToSaveNotebookRepository()
    val (_, retained) = detachedIn(stage, repository = repository, gateway = gateway)

    stage.files.announce("nb-1", "moved/nb.ipynb")
    advanceUntilIdle()

    assertEquals("moved/nb.ipynb", retained.uiState.value.notebookId)
    assertEquals("moved/nb.ipynb", stage.kernelSessions.running().single().path)
    val writesAtRename = repository.completedWrites.size
    gateway.endHeldRuns()
    advanceUntilIdle()

    val writes = repository.completedWrites
    assertEquals("the checkpoint lands at the moved path", "moved/nb.ipynb", writes.last().id)
    assertEquals("no write lands back on the old path", 0, writes.drop(writesAtRename).count { it.id == "nb-1" })
    assertNull(stage.retained())
  }

  /**
   * A delete announced while detached is `stopWriting` for the run that outlived its screen:
   * the checkpoint the run still owes must not put the file back - the reference's
   * `SaveHandler.dispose()` writes nothing after a delete either.
   */
  @Test
  fun aDeleteWhileDetachedStopsTheWriteBack() = runTest {
    val stage = Stage()
    val gateway = stage.gateway()
    val repository = RecordingNotebookRepository()
    val (_, retained) = detachedIn(stage, repository = repository, gateway = gateway)
    val writesBefore = repository.saveCount

    stage.files.announce(WorkspaceChange.Deleted("nb-1"))
    advanceUntilIdle()
    assertTrue(retained.uiState.value.gone)
    gateway.endHeldRuns()
    advanceUntilIdle()

    assertEquals("the deleted file is not written back", writesBefore, repository.saveCount)
    assertNull(stage.retained())
    assertEquals(1, gateway.disconnected.size)
  }

  /**
   * The run belongs to where the document lives *now*, not where its tab opened: a file
   * recreated at the old path is a new document and must not inherit the run, while the
   * renamed path is the one that adopts it.
   */
  @Test
  fun theOldPathIsNotTheRunsAndItsNewOneIs() = runTest {
    val stage = Stage()
    val gateway = stage.gateway()
    val repository = RecordingNotebookRepository()
    val (_, retained) = detachedIn(stage, repository = repository, gateway = gateway)

    stage.files.announce("nb-1", "moved.ipynb")
    advanceUntilIdle()
    val recreated = openIn(stage, "nb-1", gateway, repository)

    assertNotSame("a file recreated at the old path is a new document", retained.kernel, recreated.kernel)
    assertNull("and it does not show the run that is not its own", recreated.uiState.value.executions["c1"])
    assertNotNull("the run is still there for its real path", stage.retained())
    val moved = openIn(stage, "moved.ipynb", gateway, repository)

    assertSame("the renamed path adopts the run", retained.kernel, moved.kernel)
    assertEquals(ExecutionStatus.RUNNING, moved.uiState.value.executions["c1"]?.status)
    assertNull(stage.retained())
  }

  /**
   * The registry key is the opening, which a recreated file reuses: a gone holder replaced under
   * that key is no longer adoptable, but its drain still owes its own kernel a close - the
   * successor's registration must survive it.
   */
  @Test
  fun aHolderReplacedUnderItsKeyStillClosesItsKernel() = runTest {
    val stage = Stage()
    val gatewayA = stage.gateway()
    val gatewayB = stage.gateway()
    val repository = RecordingNotebookRepository()
    val (_, gone) = detachedIn(stage, repository = repository, gateway = gatewayA)

    stage.files.announce(WorkspaceChange.Deleted("nb-1"))
    advanceUntilIdle()
    val (_, successor) = detachedIn(stage, repository = repository, gateway = gatewayB)

    assertNotSame("the recreated file does not adopt a gone run", gone.kernel, successor.kernel)
    gatewayA.endHeldRuns()
    advanceUntilIdle()

    assertEquals("the replaced holder's kernel still closes on its own drain", 1, gatewayA.disconnected.size)
    assertSame("and the successor it was replaced by is untouched", successor, stage.retained())
    assertTrue(gatewayB.disconnected.isEmpty())
    gatewayB.endHeldRuns()
    advanceUntilIdle()

    assertEquals(1, gatewayB.disconnected.size)
    assertNull(stage.retained())
  }

  /**
   * The doubles every case shares: one registry, one session announce source, one settings
   * store, and a gateway factory whose sessions answer to the registry the way a real Space's
   * do - so a session ended or renamed elsewhere is heard the way production hears it.
   */
  private class Stage(val runs: ActiveCellRuns = ActiveCellRuns()) {
    val executions = NotebookExecutions()
    val registry = KernelSessionRegistry()
    val kernelSessions = FakeKernelSessions(registry)
    val files = AnnouncingWorkspaceFiles()
    val settings = FakeEditorSettingsRepository()

    fun gateway() = RecordingKernelGateway(runsUntilInterrupted = true, delegate = FakeKernelGateway(registry))

    /** The holder the registry answers for [notebookId]'s opening - null once it has drained. */
    fun retained(notebookId: String = "nb-1") =
      executions.at(NotebookExecutionKey(BASE_SPACE.id, DocumentOpening(notebookId)))
  }

  /** Opens [notebookId] on [stage]'s doubles - everything a case varies is a parameter. */
  private fun TestScope.openIn(
    stage: Stage,
    notebookId: String = "nb-1",
    gateway: KernelGateway,
    repository: NotebookRepository,
  ): EditorViewModel = loadedEditor(
    notebookId = notebookId,
    gateway = gateway,
    repository = repository,
    kernelSessions = stage.kernelSessions,
    files = stage.files,
    editorSettings = stage.settings,
    activeCellRuns = stage.runs,
    applicationScope = this,
    notebookExecutions = stage.executions,
  )

  /** Opens [notebookId], runs its cell and lets go of the screen - the detached run. */
  private fun TestScope.detachedIn(
    stage: Stage,
    repository: NotebookRepository,
    gateway: KernelGateway = stage.gateway(),
    notebookId: String = "nb-1",
  ): Pair<EditorViewModel, NotebookExecution> {
    val viewModel = openIn(stage, notebookId, gateway, repository)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    detach(viewModel)
    advanceUntilIdle()
    return viewModel to checkNotNull(stage.retained(notebookId))
  }

  /**
   * The teardown a real `ViewModelStore` clear performs - `onCleared` alone leaves `viewModelScope`
   * alive, and with it the screen's own file follow answering announcements the holder's should.
   */
  private fun detach(viewModel: EditorViewModel) {
    val store = ViewModelStore()
    store.put("editor", viewModel)
    store.clear()
  }
}
