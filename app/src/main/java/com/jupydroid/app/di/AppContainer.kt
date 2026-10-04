package com.jupydroid.app.di

import com.jupydroid.app.data.fake.FakeDocumentSharer
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeExplorerSettingsRepository
import com.jupydroid.app.data.fake.FakeKernelDebugger
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.data.fake.FakeNotebookExporter
import com.jupydroid.app.data.fake.FakeRecentDocumentsRepository
import com.jupydroid.app.data.fake.FakeServerLauncher
import com.jupydroid.app.data.fake.FakeSpaceRepository
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.data.fake.FakeTerminalSettingsRepository
import com.jupydroid.app.data.fake.FakeThemeSettingsRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.data.fake.FakeWorkspaceSearcher
import com.jupydroid.app.data.space.ActivityTrackedTerminalGateway
import com.jupydroid.app.data.space.IdleServerStopper
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.ActiveTerminals
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.model.NotebookBreakpoints
import com.jupydroid.app.domain.port.DocumentSharer
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.ExplorerSettingsRepository
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.LauncherSettingsRepository
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.RecentDocumentsRepository
import com.jupydroid.app.domain.port.ServerLauncher
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSettingsRepository
import com.jupydroid.app.domain.port.ThemeSettingsRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.domain.port.WorkspaceSearcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Duration.Companion.seconds

/**
 * Manual composition root (single :app module). Constructed once in MainActivity and passed
 * down. Every port defaults to its fake, which is what tests get for free and what a device
 * with no runnable Space keeps, so the UI works where the runtime cannot.
 */
class AppContainer(
  val notebookRepository: NotebookRepository,
  /**
   * The same tree [notebookRepository] writes into, read as files rather than as notebooks - the
   * explorer shows the folders and the data files a notebook puts there, which a list of
   * notebooks cannot.
   */
  val workspaceRepository: WorkspaceRepository = FakeWorkspaceRepository(),
  /**
   * The same tree again, asked which files hold something rather than what is at a path - a
   * different question with a different shape, so a port of its own.
   */
  val workspaceSearcher: WorkspaceSearcher = FakeWorkspaceSearcher(),
  /**
   * What was opened, which no listing of the tree can answer - a recent is a record of an act.
   * Kept apart from [notebookRepository] for that reason, and because it remembers plain files
   * and folders too.
   */
  val recentDocuments: RecentDocumentsRepository = FakeRecentDocumentsRepository(),
  /**
   * One registry for [kernelGateway] and [kernelSessions], so the pair share their session
   * bookkeeping by construction rather than by a call site remembering to pass it twice - the
   * panel's shutdown is `session.disposed` to an open notebook only when the two halves see the
   * same registry. Not a `val`: it is the pair's joint plumbing, not a port.
   */
  kernelSessionRegistry: KernelSessionRegistry = KernelSessionRegistry(),
  val kernelGateway: KernelGateway = FakeKernelGateway(kernelSessionRegistry),
  /**
   * The Running panel's port - what sessions the server is holding, and `ended` for the ones
   * that stop. Shares its registry with [kernelGateway] at the composition root, so a
   * shutdown here is `session.disposed` to every notebook holding the session.
   */
  val kernelSessions: KernelSessions = FakeKernelSessions(kernelSessionRegistry),
  /**
   * The kernelspec catalogue the picker draws from - `GET api/kernelspecs` while a server
   * runs, the Space's own declared list while it does not. Answering never boots a runtime.
   */
  val kernelSpecs: KernelSpecs = FakeKernelSpecs(),
  /** DAP over the same kernel session's control channel - breakpoints, step/continue, variables. */
  val kernelDebugger: KernelDebugger = FakeKernelDebugger(),
  /**
   * The Space's `pylsp` over stdio - one process per Space, started on the port's first call and
   * stopped beside the server. Its fake answers canned completions so the popup composes
   * on a device with no Space.
   */
  val languageServer: LanguageServerPort = FakeLanguageServerPort(),
  /**
   * The Space's shells - `terminal:create-new`, which JupyterLab adds to `'main'` as a
   * `MainAreaWidget` rather than to a panel (`packages/terminal-extension/src/index.ts:523-524`).
   *
   * Its fake echoes what is typed, so the terminal screen composes and takes input on a device
   * with no Space; only the shell is missing. Wrapped below in [ActivityTrackedTerminalGateway]
   * so [idleServerStopper] can see a terminal it opened without touching this port at all.
   */
  terminalGateway: TerminalGateway = FakeTerminalGateway(),
  /**
   * `notebook:export-to-format`, over the Space's own nbconvert - a **server-side algorithm**,
   * which this app may not re-implement.
   *
   * Its fake offers the six formats the shipped rootfs actually converts, so a device with no
   * Space draws the same menu and only the conversion is missing.
   */
  val notebookExporter: NotebookExporter = FakeNotebookExporter(),
  /**
   * How an exported file leaves the app, which the reference has no equivalent of - it exports by
   * browser download. See [DocumentSharer].
   */
  val documentSharer: DocumentSharer = FakeDocumentSharer(),
  val serverLauncher: ServerLauncher = FakeServerLauncher(),
  /**
   * The Spaces on this device - their records, not their processes. Needs no runtime, so the app
   * wires it real everywhere; the fake here holds base alone, as an installed device does.
   */
  val spaceRepository: SpaceRepository = FakeSpaceRepository(),
  /**
   * The app-wide editor preferences - `notebook:toggle-autoclosing-brackets` today, grown
   * rather than replaced. Read by every notebook and file editor alike, which is what "universal"
   * means (`fileeditor:toggle-autoclosing-brackets-universal`).
   */
  val editorSettings: EditorSettingsRepository = FakeEditorSettingsRepository(),
  /** The app-wide terminal preferences - the font size 3c adds. */
  val terminalSettings: TerminalSettingsRepository = FakeTerminalSettingsRepository(),
  /**
   * The app-wide explorer preferences - whether the Recents section is shut, which the reference
   * persists on every change (`viewContainerModel.ts:456-461`).
   */
  val explorerSettings: ExplorerSettingsRepository = FakeExplorerSettingsRepository(),
  /** The app-wide theme preference - System/Light/Dark, 3. */
  val themeSettings: ThemeSettingsRepository = FakeThemeSettingsRepository(),
  /**
   * The launcher bookkeeping - whether it has ever presented itself, which is what keeps the
   * one-pane first-run push a once-only act. At two panes the reference's
   * `maybeCreate` fills an empty main area unconditionally and needs no flag.
   */
  val launcherSettings: LauncherSettingsRepository = FakeLauncherSettingsRepository(),
  /**
   * For work that must finish after the screen that asked for it is gone - closing a notebook
   * flushes its last edit and closes its socket from here, because `viewModelScope` is already
   * cancelled by then. It lives on the container because an external scope belongs to something
   * longer-lived than the screen, which is what the composition root is.
   */
  val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  /**
   * Every run the app has in flight, counted from the tap - one object, because the question
   * "is anything running" is asked of the app and not of one notebook. Handed to every editor and
   * read by [idleServerStopper].
   */
  val activeCellRuns = ActiveCellRuns()

  /** How many terminals this process has opened and not yet closed. Read by [idleServerStopper]. */
  val activeTerminals = ActiveTerminals()

  /**
   * Breakpoints set on any open notebook's cells, kept for the app's life rather than one
   * notebook's `EditorViewModel` - see [NotebookBreakpoints]'s own doc for why.
   */
  val notebookBreakpoints = NotebookBreakpoints()

  val terminalGateway: TerminalGateway = ActivityTrackedTerminalGateway(terminalGateway, activeTerminals)

  /**
   * Stop the server when the app goes away - but only once nothing is still running,
   * a live terminal included. `JupydroidApplication` drives it from `ProcessLifecycleOwner`'s
   * ON_STOP/ON_START; the counters the stopper reads are moved by every run for the whole of its
   * life, kernel start included, and by every terminal for the whole of its life, so a
   * backgrounded computation or an open shell keeps its server until it is done.
   */
  val idleServerStopper = IdleServerStopper(
    scope = applicationScope,
    cellsInFlight = activeCellRuns.inFlight,
    terminalsOpen = activeTerminals.open,
    // The rule for a subprocess the Space owns: the language server dies with the Space too -
    // backgrounded with nothing running, it must not keep ~60 MB of interpreter alive alone.
    stopServer = {
      serverLauncher.stop()
      languageServer.stop(BASE_SPACE)
    },
    grace = IDLE_STOP_GRACE,
  )

  private companion object {
    /**
     * Half a minute of being left alone, then the server goes. Chosen on the measurement rather
     * than on taste: bringing it back costs ~1-3 s of server + kernel start and ~7 s tap to
     * answer on the tablet, against ~130 MB held by a server nobody is using.
     * The server's own idle timeouts already cover the app that is *open* and idle; this one is
     * for the app that is left.
     */
    val IDLE_STOP_GRACE = 30.seconds
  }
}
