package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.inWorkspaceOrder
import com.jupydroid.app.domain.port.WorkspaceRepository
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The workspace as it is on disk, under [rootDir].
 *
 * Plain `java.io.File`, no Storage Access Framework: the tree lives inside the app's own
 * `filesDir`, so there is nothing to ask permission for. Winlator browses its rootfs the same way
 * and for the same reason (`FileInfo.list`, `File.listFiles()`).
 *
 * ```
 * DiskWorkspaceRepository(notebookWorkspace(context)).list("reports")
 * ```
 */
class DiskWorkspaceRepository(
  private val rootDir: File,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WorkspaceRepository {
  override suspend fun list(path: String): List<WorkspaceEntry> = withContext(ioDispatcher) {
    val directory = resolve(path)
    require(directory.isDirectory) { "no directory at '$path' in the workspace" }
    (directory.listFiles() ?: emptyArray())
      .filterNot { it.name.startsWith(".") }
      .map { entryFor(it, path) }
      .inWorkspaceOrder()
  }

  /**
   * The file [path] names, refusing anything that leaves the workspace.
   *
   * Compared by canonical path, so `..`, a doubled separator and an absolute path are all caught
   * by the same check rather than by a list of spellings to remember. Above the root are the
   * rootfs and the app's private files, and the answer must not depend on the UI never asking -
   * jupyter_server refuses the same thing in `_validate_path`.
   */
  private fun resolve(path: String): File {
    val root = rootDir.canonicalFile
    val target = File(root, path).canonicalFile
    require(target == root || target.path.startsWith("${root.path}${File.separator}")) {
      "path '$path' is outside the workspace"
    }
    return target
  }

  /**
   * A symlink is described by what it points at, which is how a rootfs has to be read.
   *
   * `File.isDirectory` follows the link already; a broken one is neither a directory nor a
   * readable file and is listed as a file, so a dangling link shows in the browser instead of
   * making the whole directory unopenable.
   */
  private fun entryFor(file: File, parentPath: String): WorkspaceEntry =
    WorkspaceEntry(
      name = file.name,
      path = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}",
      kind = if (file.isDirectory) WorkspaceEntryKind.DIRECTORY else WorkspaceEntryKind.FILE,
    )
}
