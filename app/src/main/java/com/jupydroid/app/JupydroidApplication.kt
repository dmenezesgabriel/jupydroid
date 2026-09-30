package com.jupydroid.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jupydroid.app.data.disk.DiskEditorSettingsRepository
import com.jupydroid.app.data.disk.DiskExplorerSettingsRepository
import com.jupydroid.app.data.disk.DiskLauncherSettingsRepository
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskRecentDocumentsRepository
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
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.data.intent.IntentDocumentSharer
import com.jupydroid.app.data.space.BaseSpaceServer
import com.jupydroid.app.data.space.JupyterServerLauncher
import com.jupydroid.app.data.space.baseSpaceDir
import com.jupydroid.app.data.space.baseSpaceKernelDebugger
import com.jupydroid.app.data.space.baseSpaceKernelGateway
import com.jupydroid.app.data.space.baseSpaceKernelSessions
import com.jupydroid.app.data.space.baseSpaceKernelSockets
import com.jupydroid.app.data.space.baseSpaceKernelSpecs
import com.jupydroid.app.data.space.baseSpaceLanguageServerPort
import com.jupydroid.app.data.space.baseSpaceNotebookExporter
import com.jupydroid.app.data.space.baseSpaceServer
import com.jupydroid.app.data.space.baseSpaceServerLauncher
import com.jupydroid.app.data.space.baseSpaceTerminalGateway
import com.jupydroid.app.data.space.notebookWorkspace
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.TerminalGateway
import java.io.File

/**
 * The composition root's home: **one per process**, which is what "once" has to mean.
 *
 * It used to hang off `MainActivity`, and the platform relaunches that activity on every rotation
 * (`Checking to restart …: not-handles={CONFIG_ORIENTATION}`). Each relaunch built another
 * container with another `ServerLauncher`, so reopening a notebook started a **second
 * `jupyter_server`** beside the first - measured, both children of the app.
 * The process-wide lifecycle observer P1c needs has
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
    // The launcher is built once and shared: the gateways start it and P1c's backstop stops it,
    // and two launchers would be two processes pretending to be one.
    val serverLauncher = baseSpaceServerLauncher(this)
    val space = SpaceBackedPorts(serverLauncher, this)
    val workspace = notebookWorkspace(this)
    migrateNotebooksOnce(workspace)
    return containerFor(serverLauncher, space, workspace)
  }

  private fun containerFor(serverLauncher: JupyterServerLauncher, space: SpaceBackedPorts, workspace: File) =
    AppContainer(
      notebookRepository = DiskNotebookRepository(workspace),
      workspaceRepository = DiskWorkspaceRepository(workspace),
      // The same tree, walked rather than listed. Always real, like the sharer: searching needs no
      // Space, so it works on a device that cannot run one.
      workspaceSearcher = DiskWorkspaceSearcher(workspace),
      // `filesDir`, deliberately not `workspace`: the workspace is the kernel's HOME and the
      // server's root_dir, so bookkeeping there would show up in the explorer and in an `ls` from
      // a cell. The reference keeps recents in `IStateDB`, not in the Contents API.
      recentDocuments = DiskRecentDocumentsRepository(filesDir, workspace),
      kernelGateway = space.kernels,
      kernelSessions = space.sessions,
      kernelSpecs = space.specs,
      kernelDebugger = space.debugger,
      languageServer = space.languageServer,
      notebookExporter = space.exporter,
      terminalGateway = space.terminals,
      // Always real, unlike the Space-backed three: sharing needs no Space, so it works on a device
      // that cannot run one - there is nothing to export there, but a file put in the workspace by
      // any other means can still be sent.
      documentSharer = IntentDocumentSharer(this, workspace),
      serverLauncher = serverLauncher,
      // Beside recents' own file rather than inside it, in `filesDir` for the same reason: app
      // state, not a Contents API file.
      editorSettings = DiskEditorSettingsRepository(filesDir),
      terminalSettings = DiskTerminalSettingsRepository(filesDir),
      explorerSettings = DiskExplorerSettingsRepository(filesDir),
      themeSettings = DiskThemeSettingsRepository(filesDir),
      launcherSettings = DiskLauncherSettingsRepository(filesDir),
    )

  /**
   * The four ports a running Space backs, or the fakes where none can run.
   *
   * A group rather than unrelated fields, and named once it became more than one: what they share
   * is the *condition* - a device with no runnable Space - and stating it in one place is what
   * stops that having four chances to be got wrong. On an x86_64 emulator the runtime cannot work
   * at all, and the UI is better served by fakes than
   * by controls that always fail.
   *
   * **One server object for all four**, because `JupyterServerLauncher.start` begins with `stop()`
   * - see [BaseSpaceServer], whose KDoc has named an export and a terminal as its clients since P1.
   * [kernels] and [debugger] go one step further, sharing one `PerRunningServer<KernelSockets>`
   * so a `debug_request` reuses the identical per-session socket `execute`/`complete`/`history`
   * already share, rather than opening a second one.
   */
  private class SpaceBackedPorts(launcher: JupyterServerLauncher, application: Application) {
    private val space: BaseSpaceServer? = baseSpaceServer(application, launcher)
    private val kernelSockets = space?.let(::baseSpaceKernelSockets)

    /**
     * The sessions this process mints - shared by the gateway that mints them and the panel
     * port that lists/ends them, real or fake, so a Running-panel shutdown reaches an open
     * notebook on an emulator exactly as on the tablet.
     */
    private val sessionRegistry = KernelSessionRegistry()

    val kernels: KernelGateway =
      kernelSockets?.let { baseSpaceKernelGateway(it, sessionRegistry) } ?: FakeKernelGateway(sessionRegistry)

    /** `GET`/`DELETE api/sessions`, over the same sockets and registry the gateway uses. */
    val sessions: KernelSessions =
      kernelSockets?.let { baseSpaceKernelSessions(it, sessionRegistry) } ?: FakeKernelSessions(sessionRegistry)

    /** `GET api/kernelspecs` while a server is up, the Space's declared list while it is not. */
    val specs: KernelSpecs =
      kernelSockets?.let { baseSpaceKernelSpecs(it, BASE_SPACE) } ?: FakeKernelSpecs()

    val debugger: KernelDebugger = kernelSockets?.let(::baseSpaceKernelDebugger) ?: FakeKernelDebugger()

    // Off the space object, unlike the others: `pylsp` is a direct stdio process - it never
    // passes through `jupyter_server`, so it shares only the condition, not the launcher's life.
    val languageServer: LanguageServerPort =
      baseSpaceLanguageServerPort(application) ?: FakeLanguageServerPort()
    val exporter: NotebookExporter = space?.let(::baseSpaceNotebookExporter) ?: FakeNotebookExporter()
    val terminals: TerminalGateway = space?.let(::baseSpaceTerminalGateway) ?: FakeTerminalGateway()
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
