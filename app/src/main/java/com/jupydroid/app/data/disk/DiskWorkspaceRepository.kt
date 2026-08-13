package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.idBeside
import com.jupydroid.app.domain.model.inWorkspaceOrder
import com.jupydroid.app.domain.model.incrementedFileName
import com.jupydroid.app.domain.model.isValidFileName
import com.jupydroid.app.domain.port.FileNameTakenException
import com.jupydroid.app.domain.port.WorkspaceRepository
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
  rootDir: File,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WorkspaceRepository {
  private val root = WorkspaceRoot(rootDir)

  // No replay and no buffer, so `emit` suspends until every open document has taken the change -
  // which is what puts "stop writing" *before* whatever closes the screen - and returns at once
  // when nothing is open, which is exactly when there is nobody it could matter to.
  private val _changes = MutableSharedFlow<WorkspaceChange>()
  override val changes: Flow<WorkspaceChange> = _changes.asSharedFlow()

  override suspend fun list(path: String): List<WorkspaceEntry> = withContext(ioDispatcher) {
    val directory = root.fileAt(path)
    require(directory.isDirectory) { "no directory at '$path' in the workspace" }
    (directory.listFiles() ?: emptyArray())
      .filterNot { it.name.startsWith(".") }
      .map { entryFor(it, path) }
      .inWorkspaceOrder()
  }

  override suspend fun readText(path: String): String = withContext(ioDispatcher) {
    val file = root.fileAt(path)
    require(file.isFile) { "no file at '$path' in the workspace" }
    file.readText()
  }

  override suspend fun writeText(path: String, text: String): Unit = withContext(ioDispatcher) {
    // `resolve` refuses an escape before anything is written, so a bad path cannot reach the
    // filesystem at all - jupyter_server validates before it saves for the same reason.
    val file = root.fileAt(path)
    require(!file.isDirectory) { "'$path' is a directory in the workspace, not a file" }
    file.writeText(text)
  }

  override suspend fun createFile(directory: String, extension: String): String =
    withContext(ioDispatcher) {
      val name = freeNameIn(directory, "$UNTITLED_FILE.$extension")
      root.fileAt(root.pathIn(directory, name)).createNewFile()
      root.pathIn(directory, name)
    }

  override suspend fun createFolder(directory: String): String = withContext(ioDispatcher) {
    // The space is the server's `insert` for a directory and for nothing else, so the second one
    // is `Untitled Folder 1` while a second file is `untitled1.txt`.
    val name = freeNameIn(directory, UNTITLED_FOLDER, insert = " ")
    root.fileAt(root.pathIn(directory, name)).mkdir()
    root.pathIn(directory, name)
  }

  override suspend fun rename(path: String, name: String): String = withContext(ioDispatcher) {
    val moved = idBeside(path, name)
    // The server's own first line: `if new_path == old_path: return`, before anything is validated.
    if (moved == path) return@withContext path
    require(isValidFileName(name)) { "'$name$NOT_A_NAME" }
    val from = root.fileAt(path)
    require(root.isPresent(from)) { "nothing at '$path' in the workspace to rename" }
    val to = root.fileAt(moved)
    // Before the move, because `rename(2)` would replace the target without a word.
    if (root.isPresent(to)) throw FileNameTakenException(moved)
    check(from.renameTo(to)) { "could not rename '$path' to '$moved' in the workspace" }
    _changes.emit(WorkspaceChange.Renamed(from = path, to = moved))
    moved
  }

  /**
   * `DocumentManager.overwrite`, in its order: ours aside, the target gone, ours into place.
   *
   * The reference's `finally` swallows a failed delete (`manager.ts:520-522`), which would leave
   * ours parked under a temporary name and nothing at either path. Ours is moved back instead - an
   * overwrite that fails must not be the operation that loses the file.
   */
  override suspend fun overwrite(path: String, name: String): String = withContext(ioDispatcher) {
    val moved = idBeside(path, name)
    if (moved == path) return@withContext path
    require(isValidFileName(name)) { "'$name$NOT_A_NAME" }
    val from = root.fileAt(path)
    require(root.isPresent(from)) { "nothing at '$path' in the workspace to rename" }
    val target = root.fileAt(moved)
    val aside = File(target.parentFile, "${target.name}.${UUID.randomUUID()}")
    check(from.renameTo(aside)) { "could not move '$path' aside to overwrite '$moved'" }
    if (root.isPresent(target) && !target.delete()) {
      check(aside.renameTo(from)) { "could not put '$path' back after failing to overwrite '$moved'" }
      error("could not replace '$moved' in the workspace; a folder with anything in it cannot be replaced")
    }
    check(aside.renameTo(target)) { "could not move '$path' into place as '$moved'" }
    _changes.emit(WorkspaceChange.Renamed(from = path, to = moved))
    moved
  }

  override suspend fun delete(path: String): Unit = withContext(ioDispatcher) {
    val target = root.fileAt(path)
    require(root.isPresent(target)) { "nothing at '$path' in the workspace to delete" }
    // `isDirectory` follows a link, so the link check comes first: unlinking one is not a
    // recursive delete and must not be refused for what its target happens to hold.
    val emptyEnough = root.isLink(target) || !target.isDirectory || target.list().isNullOrEmpty()
    require(emptyEnough) { "'$path' is a folder with something in it; empty it before deleting it" }
    check(target.delete()) { "could not delete '$path' from the workspace" }
    _changes.emit(WorkspaceChange.Deleted(path))
  }

  /** The first name in the server's series that this directory does not already hold. */
  private fun freeNameIn(directory: String, wanted: String, insert: String = ""): String {
    val parent = root.fileAt(directory)
    require(parent.isDirectory) { "no directory at '$directory' in the workspace" }
    return incrementedFileName(wanted, insert) { File(parent, it).exists() }
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

/** `untitled`, lowercase - the server's `untitled_file`, which is not its `Untitled` notebook. */
private const val UNTITLED_FILE = "untitled"

private const val UNTITLED_FOLDER = "Untitled Folder"

/** The reference's own wording, which names the rule instead of merely refusing. */
private const val NOT_A_NAME =
  "' is not a valid file name: names must have nonzero length, cannot start with '.', " +
    "and cannot include / \\ :"
