package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.inWorkspaceOrder
import com.jupydroid.app.domain.model.incrementedFileName
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

  override suspend fun readText(path: String): String = withContext(ioDispatcher) {
    val file = resolve(path)
    require(file.isFile) { "no file at '$path' in the workspace" }
    file.readText()
  }

  override suspend fun writeText(path: String, text: String): Unit = withContext(ioDispatcher) {
    // `resolve` refuses an escape before anything is written, so a bad path cannot reach the
    // filesystem at all - jupyter_server validates before it saves for the same reason.
    val file = resolve(path)
    require(!file.isDirectory) { "'$path' is a directory in the workspace, not a file" }
    file.writeText(text)
  }

  override suspend fun createFile(directory: String, extension: String): String =
    withContext(ioDispatcher) {
      val name = freeNameIn(directory, "$UNTITLED_FILE.$extension")
      resolve(pathIn(directory, name)).createNewFile()
      pathIn(directory, name)
    }

  override suspend fun createFolder(directory: String): String = withContext(ioDispatcher) {
    // The space is the server's `insert` for a directory and for nothing else, so the second one
    // is `Untitled Folder 1` while a second file is `untitled1.txt`.
    val name = freeNameIn(directory, UNTITLED_FOLDER, insert = " ")
    resolve(pathIn(directory, name)).mkdir()
    pathIn(directory, name)
  }

  /** The first name in the server's series that this directory does not already hold. */
  private fun freeNameIn(directory: String, wanted: String, insert: String = ""): String {
    val parent = resolve(directory)
    require(parent.isDirectory) { "no directory at '$directory' in the workspace" }
    return incrementedFileName(wanted, insert) { File(parent, it).exists() }
  }

  private fun pathIn(directory: String, name: String): String =
    if (directory.isEmpty()) name else "$directory/$name"

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

/** `untitled`, lowercase - the server's `untitled_file`, which is not its `Untitled` notebook. */
private const val UNTITLED_FILE = "untitled"

private const val UNTITLED_FOLDER = "Untitled Folder"
