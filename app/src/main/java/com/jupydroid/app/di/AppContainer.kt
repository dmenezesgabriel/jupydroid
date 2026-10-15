package com.jupydroid.app.di

import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeExplorerSettingsRepository
import com.jupydroid.app.data.fake.FakeExternalLinkOpener
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakePlatformNightMode
import com.jupydroid.app.data.fake.FakeRemoteImageReader
import com.jupydroid.app.data.fake.FakeSharedDocumentReader
import com.jupydroid.app.data.fake.FakeSpaceEnvironments
import com.jupydroid.app.data.fake.FakeSpaceRepository
import com.jupydroid.app.data.fake.FakeSpaceServers
import com.jupydroid.app.data.fake.FakeTerminalSettingsRepository
import com.jupydroid.app.data.fake.FakeThemeSettingsRepository
import com.jupydroid.app.data.space.IdleServerStopper
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.ActiveTerminals
import com.jupydroid.app.domain.model.DeletedDocumentSessions
import com.jupydroid.app.domain.model.NotebookBreakpoints
import com.jupydroid.app.domain.model.spaceWithId
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.ExplorerSettingsRepository
import com.jupydroid.app.domain.port.ExternalLinkOpener
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.LauncherSettingsRepository
import com.jupydroid.app.domain.port.PlatformNightMode
import com.jupydroid.app.domain.port.RemoteImageReader
import com.jupydroid.app.domain.port.SharedDocumentReader
import com.jupydroid.app.domain.port.SpaceEnvironments
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.domain.port.SpaceServers
import com.jupydroid.app.domain.port.TerminalSettingsRepository
import com.jupydroid.app.domain.port.ThemeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Manual composition root (single :app module). Constructed once in MainActivity and passed
 * down. Every port defaults to its fake, which is what tests get for free and what a device
 * with no runnable Space keeps, so the UI works where the runtime cannot.
 */
class AppContainer(
  /**
   * Builds the ports of the Space named by its first argument - called once per Space, through
   * [portsOf] - and hands its terminals the app's one count of open shells, the second.
   * Its default hands every Space the fakes, which is what a test that never enters one needs.
   */
  private val enteredSpacePorts: (String, ActiveTerminals) -> EnteredSpacePorts = { spaceId, terminals ->
    EnteredSpacePorts(FakeNotebookRepository(), space = spaceWithId(spaceId), activeTerminals = terminals)
  },
  /**
   * The Space's `pylsp` over stdio - one process per Space, started on the port's first call and
   * stopped beside the server. Its fake answers canned completions so the popup composes
   * on a device with no Space.
   */
  val languageServer: LanguageServerPort = FakeLanguageServerPort(),
  /**
   * Every Space's server, several at once, and which are running - what the home's Run and Stop
   * act on and its Running/Stopped reads.
   */
  val spaceServers: SpaceServers = FakeSpaceServers(),
  /**
   * The Spaces on this device - their records, not their processes. Needs no runtime, so the app
   * wires it real everywhere; the fake here holds base alone, as an installed device does.
   */
  val spaceRepository: SpaceRepository = FakeSpaceRepository(),
  /**
   * The Python environments in each Space's workspace, which its Settings lists. Read off disk, so
   * the app wires it real everywhere, as it does [spaceRepository].
   */
  val spaceEnvironments: SpaceEnvironments = FakeSpaceEnvironments(),
  /**
   * Any Space's kernelspecs as its disk holds them, and the default its Settings chose - what the
   * home's Settings picks that default among. Read off disk, so the app wires it real everywhere.
   */
  val stoppedSpaceKernelSpecs: KernelSpecs = FakeKernelSpecs(),
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
  /** Where [themeSettings]' choice is handed down, so what the platform draws follows it too. */
  private val platformNightMode: PlatformNightMode = FakePlatformNightMode(),
  /**
   * The launcher bookkeeping - whether it has ever presented itself, which is what keeps the
   * one-pane first-run push a once-only act. At two panes the reference's
   * `maybeCreate` fills an empty main area unconditionally and needs no flag.
   */
  val launcherSettings: LauncherSettingsRepository = FakeLauncherSettingsRepository(),
  /** Where a rendered link's web address goes when it is tapped - see [ExternalLinkOpener]. */
  val externalLinkOpener: ExternalLinkOpener = FakeExternalLinkOpener(),
  /** Where a notebook another app hands over is read from - see [SharedDocumentReader]. */
  val sharedDocumentReader: SharedDocumentReader = FakeSharedDocumentReader(),
  /** Where a rendered image's web address is fetched from - see [RemoteImageReader]. */
  val remoteImageReader: RemoteImageReader = FakeRemoteImageReader(),
  /**
   * For work that must finish after the screen that asked for it is gone - closing a notebook
   * flushes its last edit and closes its socket from here, because `viewModelScope` is already
   * cancelled by then. It lives on the container because an external scope belongs to something
   * longer-lived than the screen, which is what the composition root is.
   */
  val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
  /**
   * Reads each installed Space's server import into the page cache, once, as the process starts
   * (ADR-0081) - so it is launched here, where the container is built once per process. Its default
   * reads nothing, which is what a device with no runnable Space and every test get.
   */
  preReadServerImports: suspend () -> Unit = {},
) {
  init {
    applicationScope.launch { preReadServerImports() }
    applicationScope.launch { pushThemeToPlatform() }
  }

  /**
   * For the process's life, because the platform keeps what it is told across launches: only a
   * changed choice is pushed, so a rewrite of the same one costs no configuration change.
   */
  private suspend fun pushThemeToPlatform() {
    themeSettings.observeSettings().map { it.override }.distinctUntilChanged().collect(platformNightMode::follow)
  }

  /**
   * Every run the app has in flight, counted from the tap - one object, because the question
   * "is anything running" is asked of the app and not of one notebook. Handed to every editor and
   * read by [idleServerStopper].
   */
  val activeCellRuns = ActiveCellRuns()

  /**
   * How many terminals this process has opened and not yet closed, in every Space. Moved by each
   * Space's [EnteredSpacePorts.terminalGateway], read by [idleServerStopper].
   */
  val activeTerminals = ActiveTerminals()

  /**
   * Breakpoints set on any open notebook's cells, kept for the app's life rather than one
   * notebook's `EditorViewModel` - see [NotebookBreakpoints]'s own doc for why.
   */
  val notebookBreakpoints = NotebookBreakpoints()

  private val portsBySpace = ConcurrentHashMap<String, EnteredSpacePorts>()

  /**
   * The ports [spaceId] binds, built on its first entry and the same object on every one after:
   * two sets for one Space would be two recents lists over one file and two notebook caches over
   * one tree.
   *
   * ```
   * appContainer.portsOf(backStack.enteredSpaceId()).notebookRepository
   * ```
   */
  fun portsOf(spaceId: String): EnteredSpacePorts =
    portsBySpace.computeIfAbsent(spaceId) { enteredSpacePorts(it, activeTerminals).also(::stopSessionsOfDeletedFiles) }

  /**
   * Started with the ports and not by a screen: the explorer and the tab strip both remove files,
   * a notebook's kernel outlives its tab, and the ports live for the process - so the watch that
   * ends a deleted file's session has to as well.
   */
  private fun stopSessionsOfDeletedFiles(ports: EnteredSpacePorts) {
    DeletedDocumentSessions(ports.workspaceFiles.changes, ports.kernelSessions).watchIn(applicationScope)
  }

  /**
   * Stop every running Space's server when the app goes away - but only once nothing is still running,
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
    // every Space's, as every server goes - so backgrounded with nothing running, none of them
    // keeps ~60 MB of interpreter alive alone.
    stopServer = {
      spaceServers.stopAll()
      languageServer.stopAll()
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
