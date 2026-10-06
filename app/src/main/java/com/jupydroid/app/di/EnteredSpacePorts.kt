package com.jupydroid.app.di

import com.jupydroid.app.data.fake.FakeDocumentSharer
import com.jupydroid.app.data.fake.FakeRecentDocumentsRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.data.fake.FakeWorkspaceSearcher
import com.jupydroid.app.domain.port.DocumentSharer
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
)
