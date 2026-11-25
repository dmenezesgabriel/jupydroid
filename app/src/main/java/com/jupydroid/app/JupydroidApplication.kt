package com.jupydroid.app

import android.app.ActivityManager
import android.app.Application
import android.app.UiModeManager
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jupydroid.app.data.asset.AssetHarnessScripts
import com.jupydroid.app.data.asset.AssetLicenceDocuments
import com.jupydroid.app.data.crash.ActivityManagerProcessExits
import com.jupydroid.app.data.crash.ErrorLogUncaughtExceptionHandler
import com.jupydroid.app.data.disk.DiskDocumentCheckpoints
import com.jupydroid.app.data.disk.DiskEditorSettingsRepository
import com.jupydroid.app.data.disk.DiskErrorLog
import com.jupydroid.app.data.disk.DiskExplorerSettingsRepository
import com.jupydroid.app.data.disk.DiskLauncherSettingsRepository
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskRecentDocumentsFile
import com.jupydroid.app.data.disk.DiskRecentDocumentsRepository
import com.jupydroid.app.data.disk.DiskSpaceEnvironments
import com.jupydroid.app.data.disk.DiskSpaceRepository
import com.jupydroid.app.data.disk.DiskTerminalSettingsRepository
import com.jupydroid.app.data.disk.DiskThemeSettingsRepository
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.disk.DiskWorkspaceSearcher
import com.jupydroid.app.data.disk.InotifyWorkspaceWatcher
import com.jupydroid.app.data.disk.NotebookWorkspaceMigration
import com.jupydroid.app.data.fake.FakeErrorLog
import com.jupydroid.app.data.fake.FakeErrorLogSharer
import com.jupydroid.app.data.fake.FakeKernelDebugger
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.data.fake.FakeNotebookExporter
import com.jupydroid.app.data.fake.FakeSpaceServers
import com.jupydroid.app.data.fake.FakeSpaceShell
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.data.http.OkHttpRemoteImageReader
import com.jupydroid.app.data.http.PyPiWheelIndex
import com.jupydroid.app.data.http.remoteImageClient
import com.jupydroid.app.data.intent.ContentResolverSharedDocumentReader
import com.jupydroid.app.data.intent.IntentDocumentSharer
import com.jupydroid.app.data.intent.IntentErrorLogSharer
import com.jupydroid.app.data.intent.IntentExternalLinkOpener
import com.jupydroid.app.data.packagemanager.PackageManagerAppIconRepository
import com.jupydroid.app.data.space.JupyterSpaceServers
import com.jupydroid.app.data.space.RunningSpaceServerEndpoint
import com.jupydroid.app.data.space.SpaceServer
import com.jupydroid.app.data.space.SpaceShellEnvironmentCreation
import com.jupydroid.app.data.space.baseSpaceDir
import com.jupydroid.app.data.space.baseSpaceKernelDebugger
import com.jupydroid.app.data.space.baseSpaceKernelGateway
import com.jupydroid.app.data.space.baseSpaceKernelSessions
import com.jupydroid.app.data.space.baseSpaceKernelSockets
import com.jupydroid.app.data.space.baseSpaceKernelSpecs
import com.jupydroid.app.data.space.baseSpaceLanguageServerPort
import com.jupydroid.app.data.space.baseSpaceNotebookExporter
import com.jupydroid.app.data.space.baseSpaceTerminalGateway
import com.jupydroid.app.data.space.notebookWorkspace
import com.jupydroid.app.data.space.notebookWorkspaceIn
import com.jupydroid.app.data.space.serverImportPreReader
import com.jupydroid.app.data.space.spaceDirIn
import com.jupydroid.app.data.space.spaceImageInstaller
import com.jupydroid.app.data.space.spaceKernelSpecsOnDisk
import com.jupydroid.app.data.space.spaceServers
import com.jupydroid.app.data.space.spaceShell
import com.jupydroid.app.data.space.spacesDir
import com.jupydroid.app.data.uimode.UiModeManagerPlatformNightMode
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.ActiveTerminals
import com.jupydroid.app.domain.model.ErrorLogOrigin
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.spaceWithId
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.SpaceRepository
import com.jupydroid.app.domain.port.SpaceShell
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.extension.api.SpaceServerEndpoint
import com.jupydroid.extensions.ai.AiExtension
import com.jupydroid.extensions.ai.data.disk.DiskQueuedQuestionsStore
import com.jupydroid.extensions.git.GitExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * The composition root's home: **one per process**, which is what "once" has to mean.
 *
 * It used to hang off `MainActivity`, and the platform relaunches that activity on every rotation
 * (`Checking to restart …: not-handles={CONFIG_ORIENTATION}`). Each relaunch built another
 * container with another `ServerLauncher`, so reopening a notebook started a **second
 * `jupyter_server`** beside the first - measured, both children of the app.
 * The observer that stops the server when the app goes away has
 * the same shape of problem from the other side: registered per activity it is never removed, and
 * the leaked one is what kept the backstop working at all.
 *
 * Nothing here may be slow: `onCreate` runs before the first frame. Building the container is
 * wiring plus one migration that reads a stamp file; the Space is installed and the server started
 * lazily, on the first cell (`BaseSpaceKernelGateway`).
 */
class JupydroidApplication : Application() {
  val container: AppContainer by lazy { buildContainer() }

  override fun onCreate() {
    super.onCreate()
    installCrashRecorder()
    // When the app is left - the screen lock, a swipe away, a covering app - the server
    // follows, unless something is still running. `ProcessLifecycleOwner` is the one lifecycle
    // that tracks the *process*, so a covered activity counts as stopped and a rotation does not.
    ProcessLifecycleOwner.get().lifecycle.addObserver(
      object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = container.idleServerStopper.onAppStopped()

        override fun onStart(owner: LifecycleOwner) = container.idleServerStopper.onAppStarted()
      },
    )
  }

  /**
   * Once per process, here: `onCreate` runs once, so the previous default - the platform's - is chained to
   * exactly once. The container is asked for lazily, inside the handler, so this adds nothing to start-up.
   */
  private fun installCrashRecorder() {
    Thread.setDefaultUncaughtExceptionHandler(
      ErrorLogUncaughtExceptionHandler(
        { container.errorLog },
        errorLogOrigin(),
        Thread.getDefaultUncaughtExceptionHandler(),
      ),
    )
  }

  /** The build every error-log entry is written for, the crash handler's and the exit reasons' alike. */
  private fun errorLogOrigin() = ErrorLogOrigin(
    appVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty(),
    deviceModel = Build.MODEL,
    androidVersion = Build.VERSION.RELEASE,
  )

  private fun buildContainer(): AppContainer {
    // Built here rather than defaulted inside the container, because the servers watch their
    // processes' exits on it and are built before the container is.
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Always real, like the searcher: listing and editing Space records needs only `filesDir`,
    // and a create copies the bundled image into the new Space's own directory. One instance,
    // shared by the home that writes a Space's settings and the servers that read them at start.
    val spaceRepository = DiskSpaceRepository(spacesDir(this), spaceImageInstaller(this))
    // One catalogue for the home's Settings, a stopped Space's picker and each server start's
    // default, so the three cannot name different kernels.
    val kernelSpecsOnDisk = spaceKernelSpecsOnDisk(this, spaceRepository)
    // The servers are built once and shared: the gateways start base's and the idle backstop stops
    // it, and two entries for one Space would be two processes pretending to be one.
    val servers = spaceServers(this, applicationScope, spaceRepository, kernelSpecsOnDisk)
    migrateNotebooksOnce(notebookWorkspace(this))
    return containerFor(
      servers,
      spaceRepository,
      kernelSpecsOnDisk,
      applicationScope,
      DiskRecentDocumentsFile(filesDir),
      spaceShell(this, spaceRepository) ?: FakeSpaceShell(),
    )
  }

  /**
   * The ports of one entered Space: its files over its own `home/jupydroid`, its kernels over its
   * own server. The files are always real, unlike the [SpaceBackedPorts]: a tree on disk needs no
   * running Space, so a device that cannot run one still browses, searches and shares.
   *
   * [recentsFile] is the one shared file every Space's recents are tagged into. It lives in
   * `filesDir`, deliberately not in a workspace: the workspace is the kernel's HOME and the
   * server's root_dir, so bookkeeping there would show up in the explorer and in an `ls` from a
   * cell. The reference keeps recents in `IStateDB`, not in the Contents API.
   *
   * One named argument per port and no logic, so its length grows with the ports - [containerFor]'s
   * reason for the same suppression.
   */
  @Suppress("LongMethod")
  private fun enteredSpacePortsOf(
    spaceId: String,
    servers: JupyterSpaceServers?,
    kernelSpecsOnDisk: KernelSpecs,
    recentsFile: DiskRecentDocumentsFile,
    activeTerminals: ActiveTerminals,
  ): EnteredSpacePorts {
    val spaceDir = spaceDirIn(spacesDir(this), spaceId)
    val workspace = notebookWorkspaceIn(spaceDir)
    val kernels = SpaceBackedPorts(servers?.server(spaceId), spaceWithId(spaceId), kernelSpecsOnDisk)
    return EnteredSpacePorts(
      notebookRepository = DiskNotebookRepository(workspace),
      workspaceRepository = DiskWorkspaceRepository(workspace),
      workspaceFiles = DiskWorkspaceFiles(workspace),
      workspaceSearcher = DiskWorkspaceSearcher(workspace),
      workspaceWatcher = InotifyWorkspaceWatcher(workspace),
      documentCheckpoints = DiskDocumentCheckpoints(workspace),
      recentDocuments = DiskRecentDocumentsRepository(recentsFile, spaceId, workspace),
      documentSharer = IntentDocumentSharer(this, workspace),
      space = kernels.space,
      kernelGateway = kernels.kernels,
      kernelSessions = kernels.sessions,
      kernelSpecs = kernels.specs,
      kernelDebugger = kernels.debugger,
      notebookExporter = kernels.exporter,
      extensionServer = kernels.extensionServer,
      terminalGateway = kernels.terminals,
      activeTerminals = activeTerminals,
    )
  }

  // One named argument per port and no logic, so its length grows with the ports rather than
  // with what it decides - the reason `config/detekt/detekt.yml` exempts a dispatch-table `when`.
  @Suppress("LongMethod")
  private fun containerFor(
    servers: JupyterSpaceServers?,
    spaceRepository: SpaceRepository,
    kernelSpecsOnDisk: KernelSpecs,
    applicationScope: CoroutineScope,
    recentsFile: DiskRecentDocumentsFile,
    shell: SpaceShell,
  ) = AppContainer(
    enteredSpacePorts = { id, terminals ->
      enteredSpacePortsOf(id, servers, kernelSpecsOnDisk, recentsFile, terminals)
    },
    // Off the servers, unlike the kernels: `pylsp` is a direct stdio process - it never passes
    // through `jupyter_server`, so it shares only the condition, not the launcher's life. One port
    // for every Space, since it already keeps one process per Space it is asked about.
    languageServer = baseSpaceLanguageServerPort(this, spaceRepository) ?: FakeLanguageServerPort(),
    spaceServers = servers ?: FakeSpaceServers(),
    spaceRepository = spaceRepository,
    spaceEnvironments = DiskSpaceEnvironments(spacesDir(this)),
    stoppedSpaceKernelSpecs = kernelSpecsOnDisk,
    // Null where no Space can run, as the servers are - the fake then answers the section's
    // probes and installs the way every other Space-backed surface does on such a device.
    spaceShell = shell,
    spaceEnvironmentCreation = SpaceShellEnvironmentCreation(shell),
    harnessScripts = AssetHarnessScripts(assets::open),
    // Beside recents' own file rather than inside it, in `filesDir` for the same reason: app
    // state, not a Contents API file.
    editorSettings = DiskEditorSettingsRepository(filesDir),
    terminalSettings = DiskTerminalSettingsRepository(filesDir),
    explorerSettings = DiskExplorerSettingsRepository(filesDir),
    themeSettings = DiskThemeSettingsRepository(filesDir),
    platformNightMode = UiModeManagerPlatformNightMode(getSystemService(UiModeManager::class.java)),
    launcherSettings = DiskLauncherSettingsRepository(filesDir),
    appIcon = PackageManagerAppIconRepository(this),
    // A chat's queued questions are app state too, one file per Space, as VS Code keeps a session's
    // pending requests in its workspace storage (vscode@3f3380c
    // `src/vs/workbench/contrib/chat/common/model/chatSessionStore.ts`).
    extensions = listOf(
      GitExtension(),
      AiExtension(queuesFor = { spaceId -> DiskQueuedQuestionsStore(File(filesDir, "chat-queues/$spaceId.json")) }),
    ),
    licenceDocuments = AssetLicenceDocuments(
      openAsset = assets::open,
      listAssets = { path -> assets.list(path).orEmpty().asList() },
    ),
    externalLinkOpener = IntentExternalLinkOpener(this),
    sharedDocumentReader = ContentResolverSharedDocumentReader(contentResolver),
    // In `cacheDir`, not `filesDir`: a fetched image is a copy the web still holds.
    remoteImageReader = OkHttpRemoteImageReader(
      remoteImageClient(File(cacheDir, "remote-images")),
      appVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty(),
    ),
    // PyPI's own release files, asked once per failed install - what a `%pip` miss pip cannot
    // explain is explained from (ADR-0165).
    wheelIndex = PyPiWheelIndex(),
    // The app-specific external directory, so a person can `adb pull` it on a release build with no
    // `run-as`; null where the device has no external storage, which the fake covers.
    errorLog = getExternalFilesDir(null)?.let(::DiskErrorLog) ?: FakeErrorLog(),
    errorLogSharer = getExternalFilesDir(null)?.let { IntentErrorLogSharer(this, it) } ?: FakeErrorLogSharer(),
    // A file of its own in `filesDir`, not in the log's directory: clearing the log must not make the
    // platform's memory of old deaths read as new ones.
    processExits = ActivityManagerProcessExits(
      getSystemService(ActivityManager::class.java),
      File(filesDir, "exit-reasons-mark"),
    ),
    errorLogOrigin = errorLogOrigin(),
    applicationScope = applicationScope,
    // Where no Space can run there is no server import to read, so the default reads nothing.
    preReadServerImports = servers?.let { { serverImportPreReader(this, spaceRepository, it).preReadAll() } } ?: {},
  )

  /**
   * The kernel-side ports one Space's server backs, or the fakes where none can run.
   *
   * A group rather than unrelated fields, and named once it became more than one: what they share
   * is the *condition* - a device with no runnable Space - and stating it in one place is what
   * stops that having five chances to be got wrong. On an x86_64 emulator the runtime cannot work
   * at all, and the UI is better served by fakes than
   * by controls that always fail.
   *
   * **One server object for all of them** - [space]'s entry in the Space servers - because
   * `JupyterServerLauncher.start` begins with `stop()`; see [SpaceServer], whose KDoc names an export
   * and a terminal as its clients. Null where no Space can run.
   * [kernels] and [debugger] go one step further, sharing one `PerRunningServer<KernelSockets>`
   * so a `debug_request` reuses the identical per-session socket `execute`/`complete`/`history`
   * already share, rather than opening a second one.
   */
  private class SpaceBackedPorts(server: SpaceServer?, val space: Space, kernelSpecsOnDisk: KernelSpecs) {
    private val kernelSockets = server?.let(::baseSpaceKernelSockets)

    /**
     * The sessions this Space's gateway mints - shared by the gateway that mints them and the panel
     * port that lists/ends them, real or fake, so a Running-panel shutdown reaches an open
     * notebook on an emulator exactly as on the tablet. One per Space: see [EnteredSpacePorts].
     */
    private val sessionRegistry = KernelSessionRegistry()

    val kernels: KernelGateway =
      kernelSockets?.let { baseSpaceKernelGateway(it, sessionRegistry) } ?: FakeKernelGateway(sessionRegistry)

    /** `GET`/`DELETE api/sessions`, over the same sockets and registry the gateway uses. */
    val sessions: KernelSessions =
      kernelSockets?.let { baseSpaceKernelSessions(it, sessionRegistry) } ?: FakeKernelSessions(sessionRegistry)

    /** `GET api/kernelspecs` while a server is up, the kernelspecs on the Space's disk while it is not. */
    val specs: KernelSpecs =
      kernelSockets?.let { baseSpaceKernelSpecs(it, kernelSpecsOnDisk) } ?: FakeKernelSpecs()

    val debugger: KernelDebugger = kernelSockets?.let(::baseSpaceKernelDebugger) ?: FakeKernelDebugger()

    val exporter: NotebookExporter = server?.let(::baseSpaceNotebookExporter) ?: FakeNotebookExporter()

    /** The same server, as an extension's panel asks it (ADR-0104). */
    val extensionServer: SpaceServerEndpoint? = server?.let(::RunningSpaceServerEndpoint)

    /** `api/terminals` and their sockets, on the same server - so a shell runs inside this Space. */
    val terminals: TerminalGateway = server?.let(::baseSpaceTerminalGateway) ?: FakeTerminalGateway()
  }

  /**
   * Before the repository reads, never after: `DiskNotebookRepository` scans its root in `init`,
   * so a migration running second would leave the explorer empty until the next launch.
   */
  private fun migrateNotebooksOnce(workspace: File) {
    NotebookWorkspaceMigration(
      legacyDir = File(filesDir, "notebooks"),
      workspaceDir = workspace,
      stampFile = File(baseSpaceDir(this), ".jupydroid/notebooks-migrated"),
    ).runOnce()
  }
}
