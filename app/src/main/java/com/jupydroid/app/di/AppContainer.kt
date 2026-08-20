package com.jupydroid.app.di

import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeDocumentSharer
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeNotebookExporter
import com.jupydroid.app.data.fake.FakeRecentDocumentsRepository
import com.jupydroid.app.data.fake.FakeServerLauncher
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.data.space.IdleServerStopper
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.port.DocumentSharer
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.RecentDocumentsRepository
import com.jupydroid.app.domain.port.ServerLauncher
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
   * What was opened, which no listing of the tree can answer - a recent is a record of an act.
   * Kept apart from [notebookRepository] for that reason, and because it remembers plain files
   * and folders too.
   */
  val recentDocuments: RecentDocumentsRepository = FakeRecentDocumentsRepository(),
  val kernelGateway: KernelGateway = FakeKernelGateway(),
  /**
   * `notebook:export-to-format`, over the Space's own nbconvert - a **server-side algorithm**,
   * which this app may not re-implement (`docs/DECISIONS.md`).
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
   * The app-wide editor preferences - `notebook:toggle-autoclosing-brackets` today, grown by P6c
   * rather than replaced. Read by every notebook and file editor alike, which is what "universal"
   * means (`fileeditor:toggle-autoclosing-brackets-universal`).
   */
  val editorSettings: EditorSettingsRepository = FakeEditorSettingsRepository(),
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

  /**
   * P1c: stop the server when the app goes away - but only once nothing is still running.
   * `MainActivity` drives it from `ProcessLifecycleOwner`'s ON_STOP/ON_START; the counter the
   * stopper reads is moved by every run for the whole of its life, kernel start included, so a
   * backgrounded computation keeps its server until it finishes.
   */
  val idleServerStopper = IdleServerStopper(
    scope = applicationScope,
    cellsInFlight = activeCellRuns.inFlight,
    stopServer = { serverLauncher.stop() },
    grace = IDLE_STOP_GRACE,
  )

  private companion object {
    /**
     * Half a minute of being left alone, then the server goes. Chosen on the measurement rather
     * than on taste: bringing it back costs ~1-3 s of server + kernel start and ~7 s tap to
     * answer on the tablet, against ~130 MB held by a server nobody is using
     * (`evidence/p1c-app-death-backstop/README.md`, `evidence/step18-lifecycle/`). P1b's own
     * timeouts already cover the app that is *open* and idle; this one is for the app that is
     * left.
     */
    val IDLE_STOP_GRACE = 30.seconds
  }
}

