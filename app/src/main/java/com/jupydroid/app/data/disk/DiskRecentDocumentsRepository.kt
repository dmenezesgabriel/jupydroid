@file:Suppress("SwallowedException")

package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.withMostRecent
import com.jupydroid.app.domain.port.RecentDocumentsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One Space's recents, held in the shared [file] and validated against that Space's workspace.
 *
 * **The [file] is not in the workspace, and that is the design.** The workspace is the kernel's `HOME`
 * and the server's `root_dir`, shared with any other Jupyter client - a bookkeeping file
 * there would show up in the explorer's own listing and in an `ls` from a cell. The reference keeps
 * recents in `IStateDB` under `docmanager:recents` (`recents.ts:288`), which is client state and
 * not a Contents API file, and this app's own migration stamp is kept out of the rootfs `home` for
 * the same reason.
 *
 * **The cap is per Space**, unlike JupyterLab, which slices the whole store before filtering by
 * root (`recents.ts`, `_setRecents`) and so lets a busy root crowd a quiet one out of its own list.
 *
 * ```
 * DiskRecentDocumentsRepository(DiskRecentDocumentsFile(context.filesDir), "space-1", workspace)
 * ```
 */
class DiskRecentDocumentsRepository(
  private val file: DiskRecentDocumentsFile,
  private val spaceId: String,
  workspaceDir: File,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RecentDocumentsRepository {
  private val workspace = WorkspaceRoot(workspaceDir)

  /**
   * Published only after [file] has written, so the flow repeats what the file holds: a write the
   * filesystem refuses must not broadcast a list the next launch never kept. `recordOpening`
   * writes twice per document tap and the stack's opens race freely, which [file]'s one lock
   * serialises.
   */
  private val recents = MutableStateFlow(file.read(spaceId).filter { stillThere(it) })

  override fun observeRecents(): Flow<List<RecentDocument>> = recents

  /**
   * A path the workspace cannot address is dropped rather than stored.
   *
   * `fileAt` throws on anything that leaves the root, and a recent that would open something
   * outside the workspace is worse than one that was never recorded - there is no screen that
   * could honour it.
   */
  override suspend fun record(document: RecentDocument) = withContext(ioDispatcher) {
    if (!isInsideTheWorkspace(document)) return@withContext
    // From the published list rather than the stored one, which still holds what the launch found
    // gone. Only this Space's repository writes its entries, so the two differ by that alone.
    recents.value = file.update(spaceId) { recents.value.withMostRecent(document) }
  }

  override suspend fun clear() = withContext(ioDispatcher) {
    recents.value = file.update(spaceId) { emptyList() }
  }

  /**
   * The same filter the launch applies, applied again on demand. Only this Space's entries: another
   * Space's paths name files in a tree this one cannot see.
   *
   * [DiskRecentDocumentsFile.update] writes only when something actually went, so a refresh on a
   * healthy list costs one `lstat` per entry and touches no disk.
   */
  override suspend fun revalidate() = withContext(ioDispatcher) {
    recents.value = file.update(spaceId) { stored -> stored.filter { stillThere(it) } }
  }

  /**
   * Whether [document] is still there. `_loadRecents` filters every entry through `_isValid` before
   * the list is ever shown (`recents.ts:203-224`), where invalid means the Contents API answered
   * **404**. This workspace is shared with the kernel, so a cell calling `os.remove` is an ordinary
   * way for a recent to go stale rather than an edge case.
   *
   * `isPresent`, not `exists`: a dangling symlink is still something the browser lists.
   */
  private fun stillThere(document: RecentDocument): Boolean =
    isInsideTheWorkspace(document) && workspace.isPresent(workspace.fileAt(document.path))

  private fun isInsideTheWorkspace(document: RecentDocument): Boolean = try {
    workspace.fileAt(document.path)
    true
  } catch (refused: IllegalArgumentException) {
    false
  }
}
