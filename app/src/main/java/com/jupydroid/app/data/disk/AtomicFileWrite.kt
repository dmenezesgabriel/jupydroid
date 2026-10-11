package com.jupydroid.app.data.disk

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Replaces this file with what [write] puts out, so a save cut short leaves the file as it was
 * rather than half rewritten (ADR-0089).
 *
 * [write] fills `.~<name>` beside the file, which is synced and then moved over it in one rename:
 * the file is the old one until the move and the new one after, so a process killed mid-write
 * leaves at most that temp, which the next save overwrites. vscode@3f3380c6's
 * `src/vs/platform/files/node/diskFileSystemProvider.ts` (`doWriteFileAtomic`) has this shape; the
 * name is jupyter_server's `path_to_intermediate` (the shipped rootfs's
 * `usr/lib/python3.12/site-packages/jupyter_server/services/contents/fileio.py`), and a dotfile is
 * one the explorer and the search already skip.
 *
 * ```
 * File(rootDir, "Analysis.ipynb").writeAtomically { it.write(json.toByteArray()) }
 * ```
 */
internal fun File.writeAtomically(write: (OutputStream) -> Unit) {
  val target = withoutSymlink()
  val temp = File(target.parentFile, ".~${target.name}")
  try {
    FileOutputStream(temp).use { stream ->
      write(stream)
      // Synced before the rename, or a power cut can leave the rename on disk ahead of the bytes
      // and the file comes back empty - microsoft/vscode#9589.
      stream.fd.sync()
    }
    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
  } finally {
    temp.delete()
  }
}

/**
 * The file a symlink names, resolved one level as jupyter_server's `atomic_writing` does: the rename
 * then replaces that file and the link stays, where renaming onto the link would replace the link.
 */
private fun File.withoutSymlink(): File {
  val path = toPath()
  if (!Files.isSymbolicLink(path)) return this
  return path.resolveSibling(Files.readSymbolicLink(path)).toFile()
}
