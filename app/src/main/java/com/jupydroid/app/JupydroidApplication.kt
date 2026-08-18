package com.jupydroid.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jupydroid.app.data.disk.DiskEditorSettingsRepository
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskRecentDocumentsRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.disk.NotebookWorkspaceMigration
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.space.baseSpaceDir
import com.jupydroid.app.data.space.baseSpaceKernelGateway
import com.jupydroid.app.data.space.baseSpaceServerLauncher
import com.jupydroid.app.data.space.notebookWorkspace
import com.jupydroid.app.di.AppContainer
import java.io.File

/**
 * The composition root's home: **one per process**, which is what "once" has to mean.
 *
 * It used to hang off `MainActivity`, and the platform relaunches that activity on every rotation
 * (`Checking to restart …: not-handles={CONFIG_ORIENTATION}`). Each relaunch built another
 * container with another `ServerLauncher`, so reopening a notebook started a **second
 * `jupyter_server`** beside the first - measured, both children of the app
 * (`evidence/p1g-one-container-per-process/`). The process-wide lifecycle observer P1c needs has
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
    // P1c: when the app is left - the screen lock, a swipe away, a covering app - the server
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
    // A device with no runnable Space keeps the fake, so the UI still works on an x86_64
    // emulator where the runtime cannot (evidence/step8.3/musl-is-arm64-only.txt).
    // The launcher is built once and shared: the gateway starts it and P1c's backstop stops it,
    // and two launchers would be two processes pretending to be one.
    val serverLauncher = baseSpaceServerLauncher(this)
    val kernelGateway = baseSpaceKernelGateway(this, serverLauncher)
    val workspace = notebookWorkspace(this)
    // Before the repository reads, never after: `DiskNotebookRepository` scans its root in `init`,
    // so a migration running second would leave the explorer empty until the next launch.
    NotebookWorkspaceMigration(
      legacyDir = File(filesDir, "notebooks"),
      workspaceDir = workspace,
      stampFile = File(baseSpaceDir(this), ".jupydroid/notebooks-migrated"),
    ).runOnce()
    return AppContainer(
      notebookRepository = DiskNotebookRepository(workspace),
      workspaceRepository = DiskWorkspaceRepository(workspace),
      // `filesDir`, deliberately not `workspace`: the workspace is the kernel's HOME and the
      // server's root_dir, so bookkeeping there would show up in the explorer and in an `ls` from
      // a cell. The reference keeps recents in `IStateDB`, not in the Contents API.
      recentDocuments = DiskRecentDocumentsRepository(filesDir, workspace),
      kernelGateway = kernelGateway ?: FakeKernelGateway(),
      serverLauncher = serverLauncher,
      // Beside recents' own file rather than inside it, in `filesDir` for the same reason: app
      // state, not a Contents API file.
      editorSettings = DiskEditorSettingsRepository(filesDir),
    )
  }
}
