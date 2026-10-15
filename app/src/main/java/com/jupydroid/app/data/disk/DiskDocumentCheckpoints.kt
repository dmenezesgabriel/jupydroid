package com.jupydroid.app.data.disk

import com.jupydroid.app.data.disk.CheckpointFiles.Companion.CHECKPOINT_ID
import com.jupydroid.app.domain.model.Checkpoint
import com.jupydroid.app.domain.port.DocumentCheckpoints
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * A workspace file's checkpoint, kept exactly where the shipped rootfs's `FileCheckpoints` keeps
 * it: `<folder>/.ipynb_checkpoints/<stem>-checkpoint<ext>`, one per file, under the id
 * `"checkpoint"` (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/filecheckpoints.py`).
 *
 * Written from disk rather than through the server, because the server's checkpoint is a file copy
 * and nothing more - `copy2_safe` in the same package's `fileio.py` - and the server is down most of
 * the time. In the same layout so a server started on this tree, and any Jupyter client talking to
 * it, lists the checkpoint this app made.
 *
 * ```
 * DiskDocumentCheckpoints(notebookWorkspace(context)).create("notes.py")
 * ```
 */
class DiskDocumentCheckpoints(rootDir: File, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
  DocumentCheckpoints {
  private val root = WorkspaceRoot(rootDir)
  private val checkpointFiles = CheckpointFiles(root)

  /** `create_checkpoint`: the file copied aside, its mtime with it as `copystat` carries it. */
  override suspend fun create(path: String): Checkpoint = withContext(ioDispatcher) {
    val file = root.fileAt(path)
    require(file.isFile) { "no file at '$path' in the workspace to checkpoint" }
    val copy = checkpointFiles.fileOf(path)
    copy.parentFile?.mkdirs()
    val bytes = file.readBytes()
    copy.writeAtomically { it.write(bytes) }
    copy.setLastModified(file.lastModified())
    copy.asCheckpoint()
  }

  /** `list_checkpoints`: the one checkpoint, or none. */
  override suspend fun list(path: String): List<Checkpoint> = withContext(ioDispatcher) {
    val copy = checkpointFiles.fileOf(path)
    if (copy.isFile) listOf(copy.asCheckpoint()) else emptyList()
  }

  /**
   * `restore_checkpoint`, except that the file takes a new mtime rather than the checkpoint's: the
   * server's `copy2` would hand the file back the stamp it had at the save, and anything holding it
   * open could then not tell the file had been written (ADR-0090).
   */
  override suspend fun restore(path: String, id: String): Unit = withContext(ioDispatcher) {
    require(id == CHECKPOINT_ID) { "no checkpoint '$id' for '$path'; a file has only '$CHECKPOINT_ID'" }
    val copy = checkpointFiles.fileOf(path)
    require(copy.isFile) { "no checkpoint for '$path' in the workspace to restore" }
    val bytes = copy.readBytes()
    root.fileAt(path).writeAtomically { it.write(bytes) }
  }

  private fun File.asCheckpoint() = Checkpoint(CHECKPOINT_ID, Instant.ofEpochMilli(lastModified()))
}

/**
 * `os.path.splitext` on a bare name: the extension is from the last dot, and leading dots are part
 * of the stem, so `.bashrc` has none and `a.tar.gz` has `.gz`.
 *
 * ```
 * splitExtension("Analysis.ipynb")   // ("Analysis", ".ipynb")
 * ```
 */
internal fun splitExtension(name: String): Pair<String, String> {
  val dot = name.lastIndexOf('.')
  val firstNotDot = name.indexOfFirst { it != '.' }
  if (firstNotDot < 0 || dot <= firstNotDot) return name to ""
  return name.substring(0, dot) to name.substring(dot)
}
