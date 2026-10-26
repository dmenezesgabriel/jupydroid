package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.COPY_INSERT
import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.model.idBeside
import com.jupydroid.app.domain.model.nameBeforeCopies
import com.jupydroid.app.domain.port.FileNameTakenException
import com.jupydroid.app.domain.port.WorkspaceFiles
import com.jupydroid.extension.api.isValidFileName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The workspace's files created, moved and removed on disk, under [rootDir] - the tree
 * [DiskWorkspaceRepository] reads, addressed through the same [WorkspaceRoot].
 *
 * Plain `java.io.File` for the reason the repository gives: the tree is inside the app's own
 * `filesDir`, so there is nothing to ask permission for.
 *
 * ```
 * DiskWorkspaceFiles(notebookWorkspace(context)).rename("reports/summary.ipynb", "final.ipynb")
 * ```
 */
class DiskWorkspaceFiles(rootDir: File, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
  WorkspaceFiles {
  private val root = WorkspaceRoot(rootDir)

  // A file's checkpoint goes where the file goes, as `ContentsManager.rename` and `delete` carry it
  // (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/manager.py`): a renamed file
  // would otherwise have nothing to revert to, and a deleted one would leave its copy behind.
  private val checkpoints = CheckpointFiles(root)

  // No replay and no buffer, so `emit` suspends until every open document has taken the change -
  // which is what puts "stop writing" *before* whatever closes the screen - and returns at once
  // when nothing is open, which is exactly when there is nobody it could matter to.
  private val _changes = MutableSharedFlow<WorkspaceChange>()
  override val changes: Flow<WorkspaceChange> = _changes.asSharedFlow()

  override suspend fun createFile(directory: String, extension: String): String = withContext(ioDispatcher) {
    val name = root.freeNameIn(directory, "$UNTITLED_FILE.$extension")
    root.fileAt(root.pathIn(directory, name)).createNewFile()
    root.pathIn(directory, name)
  }

  override suspend fun createFolder(directory: String): String = withContext(ioDispatcher) {
    // The space is the server's `insert` for a directory and for nothing else, so the second one
    // is `Untitled Folder 1` while a second file is `untitled1.txt`.
    val name = root.freeNameIn(directory, UNTITLED_FOLDER, insert = " ")
    root.fileAt(root.pathIn(directory, name)).mkdir()
    root.pathIn(directory, name)
  }

  override suspend fun createIfAbsent(path: String, text: String): Boolean = withContext(ioDispatcher) {
    val file = root.fileAt(path)
    if (root.isPresent(file)) return@withContext false
    file.parentFile?.mkdirs()
    file.writeAtomically { it.write(text.toByteArray()) }
    true
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
    checkpoints.move(path, moved)
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
    val targetWasThere = root.isPresent(target)
    if (targetWasThere && !target.delete()) {
      check(aside.renameTo(from)) { "could not put '$path' back after failing to overwrite '$moved'" }
      error("could not replace '$moved' in the workspace; a folder with anything in it cannot be replaced")
    }
    check(aside.renameTo(target)) { "could not move '$path' into place as '$moved'" }
    checkpoints.moveOnto(path, moved)
    // The target's delete lands as a delete, in the disk's own order and before the `Renamed` -
    // a document that just adopted `moved` must not take the announcement for its new file going,
    // and a document open on `moved` that heard nothing would write over the winner.
    if (targetWasThere) _changes.emit(WorkspaceChange.Deleted(moved))
    _changes.emit(WorkspaceChange.Renamed(from = path, to = moved))
    moved
  }

  override suspend fun delete(path: String): Unit = withContext(ioDispatcher) {
    val target = root.fileAt(path)
    require(root.isPresent(target)) { "nothing at '$path' in the workspace to delete" }
    // `isDirectory` follows a link, so the link check comes first: unlinking one is not a
    // recursive delete and must not be refused for what its target happens to hold.
    // A folder holding only `.ipynb_checkpoints` is empty, as `is_non_empty_dir` counts it
    // (`filemanager.py` in the same package): what is left there belongs to files already gone.
    val emptyEnough = root.isLink(target) || !target.isDirectory || checkpoints.isOnlyCheckpoints(target)
    require(emptyEnough) { "'$path' is a folder with something in it; empty it before deleting it" }
    if (!root.isLink(target) && target.isDirectory) checkpoints.removeCheckpointFolderIn(target)
    check(target.delete()) { "could not delete '$path' from the workspace" }
    checkpoints.remove(path)
    _changes.emit(WorkspaceChange.Deleted(path))
  }

  override suspend fun copy(path: String): String = withContext(ioDispatcher) {
    val source = root.fileAt(path)
    require(source.isFile) { "no file at '$path' in the workspace to copy; a folder cannot be copied" }
    val folder = path.substringBeforeLast('/', missingDelimiterValue = "")
    val copied = root.pathIn(folder, root.freeNameIn(folder, nameBeforeCopies(source.name), insert = COPY_INSERT))
    root.fileAt(copied).writeAtomically { out -> source.inputStream().use { it.copyTo(out) } }
    copied
  }
}

/** `untitled`, lowercase - the server's `untitled_file`, which is not its `Untitled` notebook. */
private const val UNTITLED_FILE = "untitled"

private const val UNTITLED_FOLDER = "Untitled Folder"

/** The reference's own wording, which names the rule instead of merely refusing. */
private const val NOT_A_NAME =
  "' is not a valid file name: names must have nonzero length, cannot start with '.', " +
    "and cannot include / \\ :"
