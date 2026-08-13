package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * The doubles and the seeded notebook every editor test opens.
 *
 * They live here rather than inside one test class because more than one suite needs them, and a
 * copy per suite is how two of them end up disagreeing about what the fake does.
 */

internal val codeCell = Cell(id = "c1", kind = CellKind.CODE, source = "print(1)")
internal val markdownCell = Cell(id = "c2", kind = CellKind.MARKDOWN, source = "# heading")
internal val secondCodeCell = Cell(id = "c3", kind = CellKind.CODE, source = "print(2)")

/** Records what the editor asked of a kernel, and can make a cell raise on demand. */
internal class RecordingKernelGateway(
  private val raiseOn: String? = null,
  private val runsUntilInterrupted: Boolean = false,
) : KernelGateway by FakeKernelGateway() {
  val executed = mutableListOf<String>()
  var interruptCount = 0
    private set

  private val interrupted = CompletableDeferred<Unit>()

  val executedSource = mutableListOf<String>()

  /** Sessions the editor closed on its way out; empty means it never opened one. */
  val disconnected = mutableListOf<KernelSession>()

  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> {
    executed += cellId
    executedSource += code
    return flow {
      if (runsUntilInterrupted) interrupted.await()
      if (cellId == raiseOn) {
        emit(KernelEvent.Output(cellId, CellOutput.Error("ValueError", "boom", emptyList())))
      }
      emit(KernelEvent.Idle)
    }
  }

  override suspend fun interrupt(session: KernelSession) {
    interruptCount++
    interrupted.complete(Unit)
  }

  override suspend fun disconnect(session: KernelSession) {
    disconnected += session
  }
}

/** Named fake for the failure path: connecting is fine, executing never works. */
internal class UnreachableKernelGateway : KernelGateway by FakeKernelGateway() {
  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> =
    flow { throw IllegalStateException("closed early: 1006") }
}

/** Counts writes, so the debounce can be asserted rather than assumed. */
internal class RecordingNotebookRepository(
  private val delegate: FakeNotebookRepository = FakeNotebookRepository(),
) : NotebookRepository by delegate {
  var saveCount = 0
    private set

  override suspend fun saveNotebook(notebook: Notebook) {
    saveCount++
    delegate.saveNotebook(notebook)
  }
}

/**
 * A workspace that moves nothing and can say something moved.
 *
 * The editor only ever *listens* to this port - a rename it did not make is announced by the
 * contents layer, the way `Contents.fileChanged` is - so a double that can announce is the whole
 * of what these tests need.
 */
internal class AnnouncingWorkspaceRepository : WorkspaceRepository by FakeWorkspaceRepository() {
  private val _changes = MutableSharedFlow<WorkspaceChange>()
  override val changes: Flow<WorkspaceChange> = _changes

  /** Emits the way the real repository does, so a test drives the collector and not a setter. */
  suspend fun announce(from: String, to: String) = announce(WorkspaceChange.Renamed(from, to))

  suspend fun announce(change: WorkspaceChange) = _changes.emit(change)
}

internal fun TestScope.loadedEditor(
  gateway: KernelGateway = FakeKernelGateway(),
  cells: List<Cell> = listOf(codeCell),
  repository: NotebookRepository = FakeNotebookRepository(),
  workspace: WorkspaceRepository = FakeWorkspaceRepository(),
  // The path the notebook lives at, which is its id since X1b. Overridden by the cases about
  // folders; "nb-1"/"Data" is kept as the default so the cases that predate them read unchanged.
  notebookId: String = "nb-1",
  title: String = "Data",
  // The test scope stands in for the application scope, so work that outlives the ViewModel is
  // driven by advanceUntilIdle like everything else.
  applicationScope: CoroutineScope = this,
): EditorViewModel {
  launch {
    repository.saveNotebook(Notebook(notebookId, title, "python3", cells))
  }
  advanceUntilIdle()
  // No collector: uiState is a StateFlow, so .value is current without one - and a
  // collector that is never cancelled leaves runTest waiting on it.
  val viewModel = EditorViewModel(notebookId, repository, workspace, gateway, applicationScope)
  advanceUntilIdle()
  return viewModel
}
