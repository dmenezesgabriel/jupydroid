package com.jupydroid.app.ui.editor

import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeKernelDebugger
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.data.fake.FakeNotebookExporter
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.NotebookBreakpoints
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.port.BreakpointRequest
import com.jupydroid.app.domain.port.DebugEvent
import com.jupydroid.app.domain.port.DebugScope
import com.jupydroid.app.domain.port.DebugStackFrame
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.StaleKernelSessionException
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import java.io.FileNotFoundException

/**
 * The doubles and the seeded notebook every editor test opens.
 *
 * They live here rather than inside one test class because more than one suite needs them, and a
 * copy per suite is how two of them end up disagreeing about what the fake does.
 */

internal val codeCell = Cell(id = "c1", kind = CellKind.CODE, source = "print(1)")
internal val markdownCell = Cell(id = "c2", kind = CellKind.MARKDOWN, source = "# heading")
internal val secondCodeCell = Cell(id = "c3", kind = CellKind.CODE, source = "print(2)")
internal val rawCell = Cell(id = "r1", kind = CellKind.RAW, source = "\\begin{equation}")

/** Records what the editor asked of a kernel, and can make a cell raise on demand. */
internal class RecordingKernelGateway(
  private val raiseOn: String? = null,
  private val runsUntilInterrupted: Boolean = false,
  private val delegate: KernelGateway = FakeKernelGateway(),
) : KernelGateway by delegate {
  val executed = mutableListOf<String>()
  var interruptCount = 0
    private set

  /** How many times a fresh kernel was actually asked for - what tells attaching apart from connecting. */
  var connectCount = 0
    private set

  /** The document paths each connect was asked for, in order - the cwd a kernel was born in. */
  val connectedPaths = mutableListOf<String>()

  override suspend fun connect(space: Space, documentPath: String, kernel: KernelChoice?): KernelSession {
    connectCount++
    connectedPaths += documentPath
    return delegate.connect(space, documentPath, kernel)
  }

  private val interrupted = CompletableDeferred<Unit>()

  val executedSource = mutableListOf<String>()

  /** Sessions the editor closed on its way out; empty means it never opened one. */
  val disconnected = mutableListOf<KernelSession>()

  /**
   * Sessions the editor ended outright. Recorded apart from [disconnected] because the two are
   * opposite claims about the kernel - one leaves it running, the other does not.
   */
  val shutDown = mutableListOf<KernelSession>()

  /** Sessions the editor replaced. Ordered against [executed], which is what a compound restart needs. */
  val restarted = mutableListOf<KernelSession>()

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

  override suspend fun shutdown(session: KernelSession) {
    shutDown += session
  }

  override suspend fun restart(session: KernelSession) {
    restarted += session
  }
}

/**
 * A kernel that takes its time coming up, which on a device is the ordinary case: installing the
 * Space, starting `jupyter_server` and starting the kernel is the slow part of a run, and the
 * tablet measured fourteen seconds of it.
 */
internal class SlowToConnectKernelGateway : KernelGateway by FakeKernelGateway() {
  private val connectable = CompletableDeferred<Unit>()

  override suspend fun connect(space: Space, documentPath: String, kernel: KernelChoice?): KernelSession {
    connectable.await()
    return KernelSession(
      id = "slow-${space.id}",
      spaceId = space.id,
      kernelId = "kernel-1",
      kernelName = "python3",
      serverSessionId = "server-session-1",
    )
  }

  fun letItConnect() = connectable.complete(Unit)
}

/** A PATCH that holds until released, the way [SlowToConnectKernelGateway] gates a connect. */
internal class SlowToChangeKernelGateway : KernelGateway by FakeKernelGateway() {
  private val changeable = CompletableDeferred<Unit>()

  override suspend fun changeKernel(session: KernelSession, kernel: KernelChoice): KernelSession {
    changeable.await()
    return session.copy(kernelName = (kernel as? KernelChoice.Spec)?.name ?: session.kernelName)
  }

  fun letItChange() = changeable.complete(Unit)
}

/**
 * A gateway that refuses the first [refusals] sessions handed back to it, the way
 * `BaseSpaceKernelGateway` refuses one issued by a server that has since been stopped.
 */
internal class StaleSessionKernelGateway(
  private val refusals: Int,
  private val fake: FakeKernelGateway = FakeKernelGateway(),
) : KernelGateway by fake {
  var connectCount = 0
    private set

  private var refused = 0

  override suspend fun connect(space: Space, documentPath: String, kernel: KernelChoice?): KernelSession {
    connectCount++
    return KernelSession(
      id = "session-$connectCount",
      spaceId = space.id,
      kernelId = "kernel-$connectCount",
      kernelName = "python3",
      serverSessionId = "server-session-$connectCount",
    )
  }

  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> {
    if (refused < refusals) {
      refused++
      throw StaleKernelSessionException(session.id)
    }
    return fake.execute(session, cellId, code)
  }
}

/**
 * Blocks [attach] until let go, so a test can prove a cell's debug session is armed **before**
 * it executes - the reason [NotebookCellRuns.eventsFor] calls [NotebookDebugger.startSession]
 * inside the same block that resolves the session [KernelGateway.execute] itself uses.
 */
internal class BlockingAttachKernelDebugger(private val fake: FakeKernelDebugger = FakeKernelDebugger()) :
  KernelDebugger by fake {
  private val attachable = CompletableDeferred<Unit>()

  override suspend fun attach(session: KernelSession) {
    attachable.await()
    fake.attach(session)
  }

  fun letAttachProceed() = attachable.complete(Unit)
}

/**
 * Records the order [NotebookDebugger.startSession] sends its four port calls in - what
 * [FakeKernelDebugger]'s own per-method recorders can't prove, since each is its own list.
 */
internal class RecordingKernelDebugger(private val fake: FakeKernelDebugger = FakeKernelDebugger()) :
  KernelDebugger by fake {
  val calls = mutableListOf<String>()

  override suspend fun attach(session: KernelSession) {
    calls += "attach"
    fake.attach(session)
  }

  override suspend fun dumpCell(session: KernelSession, code: String): String {
    calls += "dumpCell"
    return fake.dumpCell(session, code)
  }

  override suspend fun setBreakpoints(session: KernelSession, codeId: String, breakpoints: List<BreakpointRequest>) =
    fake.setBreakpoints(session, codeId, breakpoints).also { calls += "setBreakpoints" }

  override suspend fun configurationDone(session: KernelSession) {
    calls += "configurationDone"
    fake.configurationDone(session)
  }

  /** Which frame id each `scopes` call named, in order - P6a-8a's own gate for "the top frame only". */
  val scopesRequestedForFrame = mutableListOf<Int>()

  /** Which `variablesReference` each `variables` call named, in order. */
  val variablesRequestedForReference = mutableListOf<Int>()

  override suspend fun stackTrace(session: KernelSession, threadId: Int): List<DebugStackFrame> {
    calls += "stackTrace"
    return fake.stackTrace(session, threadId)
  }

  override suspend fun scopes(session: KernelSession, frameId: Int): List<DebugScope> {
    calls += "scopes"
    scopesRequestedForFrame += frameId
    return fake.scopes(session, frameId)
  }

  override suspend fun variables(session: KernelSession, variablesReference: Int): List<DebugVariable> {
    calls += "variables"
    variablesRequestedForReference += variablesReference
    return fake.variables(session, variablesReference)
  }
}

/**
 * Mirrors `BaseSpaceKernelDebugger.known`'s real contract - `events` throws for a session that
 * was never `attach`ed - which [FakeKernelDebugger] alone does not, since its `events` answers
 * any session. Found this way, not guessed: a tablet run of P6a-8's own Maestro flow crashed the
 * app on an ordinary cell with no breakpoint, because [NotebookCellRuns] used to launch
 * [NotebookDebugger.watchForStop] for *every* run regardless of whether [NotebookDebugger.startSession]
 * ever attached.
 */
internal class ThrowsIfUnattachedKernelDebugger(private val fake: FakeKernelDebugger = FakeKernelDebugger()) :
  KernelDebugger by fake {
  private val attachedSessions = mutableSetOf<String>()

  override suspend fun attach(session: KernelSession) {
    attachedSessions += session.id
    fake.attach(session)
  }

  override fun events(session: KernelSession): Flow<DebugEvent> = if (session.id in attachedSessions) {
    fake.events(session)
  } else {
    flow { throw StaleKernelSessionException(session.id) }
  }
}

/** Remembers which document each session was opened for, which a rename changes. */
internal class RecordingConnectKernelGateway(private val fake: FakeKernelGateway = FakeKernelGateway()) :
  KernelGateway by fake {
  val paths = mutableListOf<String>()

  override suspend fun connect(space: Space, documentPath: String, kernel: KernelChoice?): KernelSession {
    paths += documentPath
    return fake.connect(space, documentPath, kernel)
  }
}

/** Named fake for the failure path: connecting is fine, executing never works. */
internal class UnreachableKernelGateway : KernelGateway by FakeKernelGateway() {
  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> =
    flow { throw IllegalStateException("closed early: 1006") }
}

/** Counts writes, so the debounce can be asserted rather than assumed. */
internal class RecordingNotebookRepository(private val delegate: FakeNotebookRepository = FakeNotebookRepository()) :
  NotebookRepository by delegate {
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
  // The six formats the shipped rootfs converts, which is what the fake offers by default - so a
  // case about the export picker sees the same rows a device does.
  exporter: NotebookExporter = FakeNotebookExporter(),
  // The path the notebook lives at, which is its id since X1b. Overridden by the cases about
  // folders; "nb-1"/"Data" is kept as the default so the cases that predate them read unchanged.
  notebookId: String = "nb-1",
  title: String = "Data",
  // The test scope stands in for the application scope, so work that outlives the ViewModel is
  // driven by advanceUntilIdle like everything else.
  applicationScope: CoroutineScope = this,
  // Only the cases about P1c pass one; every other case ignores what it counts.
  activeCellRuns: ActiveCellRuns = ActiveCellRuns(),
  // Only the cases about the open-document registry pass one; the rest never read it.
  openDocuments: OpenDocuments = OpenDocuments(),
  // Only the cases about a breakpoint surviving a rebuilt ViewModel pass a shared one.
  notebookBreakpoints: NotebookBreakpoints = NotebookBreakpoints(),
  // Only the cases about the debug-session handshake pass a RecordingKernelDebugger.
  debugger: KernelDebugger = FakeKernelDebugger(),
  // Only the autosave cases pass one; every other case reads the defaults a settings file ships.
  editorSettings: EditorSettingsRepository = FakeEditorSettingsRepository(),
  // Only the cases about a language-server ask pass one; the rest spawn nothing anyway.
  languageServer: LanguageServerPort = FakeLanguageServerPort(),
  // Only the "a session ended elsewhere" case passes one sharing the gateway's registry.
  kernelSessions: KernelSessions = FakeKernelSessions(),
  // Only the kernel-choice cases pass one; the default answers the Space's own list.
  kernelSpecs: KernelSpecs = FakeKernelSpecs(),
  // The notebook as it was saved - kernel metadata included. Default builds a bare one so the
  // cases that predate kernelspec persistence read unchanged.
  notebook: Notebook? = null,
): EditorViewModel {
  launch { repository.saveNotebook(notebook ?: Notebook(notebookId, title, "python3", cells)) }
  advanceUntilIdle()
  // No collector: uiState is a StateFlow, so .value is current without one - and a
  // collector that is never cancelled leaves runTest waiting on it.
  val viewModel =
    EditorViewModel(
      notebookId, repository, workspace, gateway, exporter, applicationScope, activeCellRuns,
      openDocuments, notebookBreakpoints, debugger, editorSettings, languageServer, kernelSessions, kernelSpecs,
    )
  advanceUntilIdle()
  return viewModel
}

/** Reads like the tree it was, refuses every write - the directory went while this was open. */
internal class VanishedNotebookRepository(private val stillReadable: NotebookRepository = FakeNotebookRepository()) :
  NotebookRepository by stillReadable {
  override suspend fun saveNotebook(notebook: Notebook): Unit =
    throw FileNotFoundException("${notebook.id}: open failed: ENOENT (No such file or directory)")
}

/**
 * A notebook that loaded from a tree that then refused every write.
 *
 * Seeded through the plain repository and only *then* wrapped, because [loadedEditor] writes the
 * fixture with the repository it is handed - a refusing one would fail the seed rather than the
 * case.
 */
internal fun TestScope.editorWhoseWorkspaceWentAway(
  editorSettings: EditorSettingsRepository = FakeEditorSettingsRepository(),
): EditorViewModel {
  val readable = FakeNotebookRepository()
  launch { readable.saveNotebook(Notebook("nb-1", "Data", "python3", listOf(codeCell))) }
  advanceUntilIdle()
  val viewModel =
    EditorViewModel(
      notebookId = "nb-1",
      notebookRepository = VanishedNotebookRepository(readable),
      workspaceRepository = FakeWorkspaceRepository(),
      kernelGateway = FakeKernelGateway(),
      notebookExporter = FakeNotebookExporter(),
      applicationScope = this,
      activeCellRuns = ActiveCellRuns(),
      editorSettings = editorSettings,
    )
  advanceUntilIdle()
  return viewModel
}
