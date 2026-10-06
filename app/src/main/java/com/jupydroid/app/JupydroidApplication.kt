package com.jupydroid.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jupydroid.app.data.disk.DiskEditorSettingsRepository
import com.jupydroid.app.data.disk.DiskExplorerSettingsRepository
import com.jupydroid.app.data.disk.DiskLauncherSettingsRepository
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskRecentDocumentsFile
import com.jupydroid.app.data.disk.DiskRecentDocumentsRepository
import com.jupydroid.app.data.disk.DiskSpaceRepository
import com.jupydroid.app.data.disk.DiskTerminalSettingsRepository
import com.jupydroid.app.data.disk.DiskThemeSettingsRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.disk.DiskWorkspaceSearcher
import com.jupydroid.app.data.disk.NotebookWorkspaceMigration
import com.jupydroid.app.data.fake.FakeKernelDebugger
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.data.fake.FakeNotebookExporter
import com.jupydroid.app.data.fake.FakeSpaceServers
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.data.intent.IntentDocumentSharer
import com.jupydroid.app.data.space.JupyterSpaceServers
import com.jupydroid.app.data.space.SpaceServer
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
import com.jupydroid.app.data.space.spaceDirIn
import com.jupydroid.app.data.space.spaceImageInstaller
import com.jupydroid.app.data.space.spaceServers
import com.jupydroid.app.data.space.spacesDir
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.ActiveTerminals
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.spaceWithId
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.TerminalGateway
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

  private fun buildContainer(): AppContainer {
    // Built here rather than defaulted inside the container, because the servers watch their
    // processes' exits on it and are built before the container is.
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // The servers are built once and shared: the gateways start base's and the idle backstop stops
    // it, and two entries for one Space would be two processes pretending to be one.
    val servers = spaceServers(this, applicationScope)
    migrateNotebooksOnce(notebookWorkspace(this))
    return containerFor(servers, applicationScope, DiskRecentDocumentsFile(filesDir))
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
   */
  private fun enteredSpacePortsOf(
    spaceId: String,
    servers: JupyterSpaceServers?,
    recentsFile: DiskRecentDocumentsFile,
    activeTerminals: ActiveTerminals,
  ): EnteredSpacePorts {
    val workspace = notebookWorkspaceIn(spaceDirIn(spacesDir(this), spaceId))
    val kernels = SpaceBackedPorts(servers?.server(spaceId), spaceWithId(spaceId))
    return EnteredSpacePorts(
      notebookRepository = DiskNotebookRepository(workspace),
      workspaceRepository = DiskWorkspaceRepository(workspace),
      workspaceSearcher = DiskWorkspaceSearcher(workspace),
      recentDocuments = DiskRecentDocumentsRepository(recentsFile, spaceId, workspace),
      documentSharer = IntentDocumentSharer(this, workspace),
      space = kernels.space,
      kernelGateway = kernels.kernels,
      kernelSessions = kernels.sessions,
      kernelSpecs = kernels.specs,
      kernelDebugger = kernels.debugger,
      notebookExporter = kernels.exporter,
      terminalGateway = kernels.terminals,
      activeTerminals = activeTerminals,
    )
  }

  // One named argument per port and no logic, so its length grows with the ports rather than
  // with what it decides - the reason `config/detekt/detekt.yml` exempts a dispatch-table `when`.
  @Suppress("LongMethod")
  private fun containerFor(
    servers: JupyterSpaceServers?,
    applicationScope: CoroutineScope,
    recentsFile: DiskRecentDocumentsFile,
  ) = AppContainer(
    enteredSpacePorts = { id, terminals -> enteredSpacePortsOf(id, servers, recentsFile, terminals) },
    // Off the servers, unlike the kernels: `pylsp` is a direct stdio process - it never passes
    // through `jupyter_server`, so it shares only the condition, not the launcher's life. One port
    // for every Space, since it already keeps one process per Space it is asked about.
    languageServer = baseSpaceLanguageServerPort(this) ?: FakeLanguageServerPort(),
    spaceServers = servers ?: FakeSpaceServers(),
    // Always real, like the searcher: listing and editing Space records needs only `filesDir`,
    // and a create copies the bundled image into the new Space's own directory.
    spaceRepository = DiskSpaceRepository(spacesDir(this), spaceImageInstaller(this)),
    // Beside recents' own file rather than inside it, in `filesDir` for the same reason: app
    // state, not a Contents API file.
    editorSettings = DiskEditorSettingsRepository(filesDir),
    terminalSettings = DiskTerminalSettingsRepository(filesDir),
    explorerSettings = DiskExplorerSettingsRepository(filesDir),
    themeSettings = DiskThemeSettingsRepository(filesDir),
    launcherSettings = DiskLauncherSettingsRepository(filesDir),
    applicationScope = applicationScope,
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
  private class SpaceBackedPorts(server: SpaceServer?, val space: Space) {
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

    /** `GET api/kernelspecs` while a server is up, the Space's declared list while it is not. */
    val specs: KernelSpecs =
      kernelSockets?.let { baseSpaceKernelSpecs(it, space) } ?: FakeKernelSpecs()

    val debugger: KernelDebugger = kernelSockets?.let(::baseSpaceKernelDebugger) ?: FakeKernelDebugger()

    val exporter: NotebookExporter = server?.let(::baseSpaceNotebookExporter) ?: FakeNotebookExporter()

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
