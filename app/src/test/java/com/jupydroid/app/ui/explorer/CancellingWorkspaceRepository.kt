package com.jupydroid.app.ui.explorer

import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.CancellationException

/**
 * A workspace whose listing is cancelled, and which is otherwise the one it wraps.
 *
 * **A named fake rather than an inline stub** (CLAUDE.md), and it exists because the thing it
 * stands for cannot be reproduced on a virtual scheduler: `ExplorerViewModel.goTo` cancels the
 * listing already in flight, and on a device `DiskWorkspaceRepository.list` -
 * a `withContext(ioDispatcher)` - raises that cancellation when its block *finishes*, after the
 * newer listing has written a clean state. `runTest` resumes a cancelled `delay` immediately
 * instead, so the ordering that makes the defect visible never happens there.
 *
 * What this asks is the question underneath the ordering: when a cancellation reaches the
 * ViewModel's `runCatching`, does it become something the user is told about?
 *
 * ```
 * explorer(workspace = CancellingWorkspaceRepository(disk()))
 * ```
 */
internal class CancellingWorkspaceRepository(private val workspace: WorkspaceRepository) :
  WorkspaceRepository by workspace {
  override suspend fun list(path: String): List<WorkspaceEntry> =
    throw CancellationException("StandaloneCoroutine was cancelled")
}
