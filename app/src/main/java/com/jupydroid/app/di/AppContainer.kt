package com.jupydroid.app.di

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeRecentDocumentsRepository
import com.jupydroid.app.data.fake.FakeServerLauncher
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.RecentDocumentsRepository
import com.jupydroid.app.domain.port.ServerLauncher
import com.jupydroid.app.domain.port.WorkspaceRepository
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
  val serverLauncher: ServerLauncher = FakeServerLauncher(),
  /**
   * For work that must finish after the screen that asked for it is gone - closing a notebook
   * flushes its last edit and closes its socket from here, because `viewModelScope` is already
   * cancelled by then. It lives on the container because an external scope belongs to something
   * longer-lived than the screen, which is what the composition root is.
   */
  val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
)

