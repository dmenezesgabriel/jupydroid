package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.FileStamp
import com.jupydroid.app.domain.model.StampedText
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.domain.port.decodedAsTextOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
class DiskWorkspaceRepository(rootDir: File, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
  WorkspaceRepository {
  private val root = WorkspaceRoot(rootDir)

  override suspend fun list(path: String): List<WorkspaceEntry> = withContext(ioDispatcher) {
    val directory = root.fileAt(path)
    require(directory.isDirectory) { "no directory at '$path' in the workspace" }
    directory.workspaceEntriesUnder(path)
  }

  override suspend fun readStampedText(path: String): StampedText = withContext(ioDispatcher) {
    val file = root.fileAt(path)
    require(file.isFile) { "no file at '$path' in the workspace" }
    // Stamped before the read: a write landing in between leaves a stamp older than the text,
    // which the next save refuses - the safe side of that race.
    val stamp = file.stamp()
    StampedText(file.readBytes().decodedAsTextOf(path), stamp)
  }

  override suspend fun writeText(path: String, text: String, expected: FileStamp?): FileStamp =
    withContext(ioDispatcher) { writeTextFile(path, text, expected) }

  private fun writeTextFile(path: String, text: String, expected: FileStamp?): FileStamp {
    // `resolve` refuses an escape before anything is written, so a bad path cannot reach the
    // filesystem at all - jupyter_server validates before it saves for the same reason.
    val file = root.fileAt(path)
    require(!file.isDirectory) { "'$path' is a directory in the workspace, not a file" }
    return file.writeIfUnchanged(expected, text.toByteArray(), named = path)
  }

  override suspend fun readBytes(path: String): ByteArray = withContext(ioDispatcher) {
    val file = root.fileAt(path)
    require(file.isFile) { "no file at '$path' in the workspace" }
    file.readBytes()
  }

  override suspend fun writeBytes(path: String, bytes: ByteArray): Unit = withContext(ioDispatcher) {
    // The same order [writeText] uses: `resolve` refuses an escape before anything is written, so
    // a bad path never reaches the filesystem.
    val file = root.fileAt(path)
    require(!file.isDirectory) { "'$path' is a directory in the workspace, not a file" }
    file.writeAtomically { it.write(bytes) }
  }
}
