package com.jupydroid.app.di

import com.jupydroid.app.data.fake.FakeDocumentSharer
import com.jupydroid.app.data.fake.FakeKernelDebugger
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeNotebookExporter
import com.jupydroid.app.data.fake.FakeRecentDocumentsRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.data.fake.FakeWorkspaceSearcher
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.DocumentSharer
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.RecentDocumentsRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.domain.port.WorkspaceSearcher

/**
 * The ports one entered Space binds, built for that Space alone and never pointed at another.
 *
 * VS Code rebuilds the window when a workspace is entered, so every window-scoped service serves
 * exactly one workspace (`vscode@3f3380c`, `enterWorkspace` in
 * `src/vs/workbench/services/workspaces/electron-browser/workspaceEditingService.ts`). Several
 * Spaces run at once here (ADR-0075), so the same rule takes the shape of one set per Space, which
 * [AppContainer.portsOf] caches for the process. This class groups
 * existing ports and is not a port of its own.
 *
 * ```
 * EnteredSpacePorts(DiskNotebookRepository(workspace), DiskWorkspaceRepository(workspace))
 * ```
 */
class EnteredSpacePorts(
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
   * How an exported file leaves the app, which the reference has no equivalent of - it exports by
   * browser download. See [DocumentSharer].
   */
  val documentSharer: DocumentSharer = FakeDocumentSharer(),
  /** The Space these ports serve - what every kernel call names, so none of them names base by default. */
  val space: Space = BASE_SPACE,
  /**
   * One registry for [kernelGateway] and [kernelSessions] and for no other Space's: a stopped
   * server's sessions port ends everything its registry holds (`BaseSpaceKernelSessions.running`),
   * so a registry shared across Spaces would end base's sessions when Thesis's were asked for.
   * Not a `val`: it is the pair's joint plumbing, not a port.
   */
  kernelSessionRegistry: KernelSessionRegistry = KernelSessionRegistry(),
  /** This Space's kernels, on its own `jupyter_server`. */
  val kernelGateway: KernelGateway = FakeKernelGateway(kernelSessionRegistry),
  /** The Running panel's port - the sessions this Space's server holds, and `ended` for those that stop. */
  val kernelSessions: KernelSessions = FakeKernelSessions(kernelSessionRegistry),
  /** This Space's kernelspec catalogue - `GET api/kernelspecs` while it runs, its declared list while not. */
  val kernelSpecs: KernelSpecs = FakeKernelSpecs(),
  /** DAP over the same kernel session's control channel. */
  val kernelDebugger: KernelDebugger = FakeKernelDebugger(),
  /** `notebook:export-to-format`, over this Space's own nbconvert, which resolves paths in its root. */
  val notebookExporter: NotebookExporter = FakeNotebookExporter(),
)
