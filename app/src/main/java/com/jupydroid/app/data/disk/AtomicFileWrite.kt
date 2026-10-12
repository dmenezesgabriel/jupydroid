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
    temp.takePermissionsOf(target)
    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
  } finally {
    temp.delete()
  }
}

/**
 * Gives the temp the mode of the file it replaces. The rename makes a new inode, which would
 * otherwise take the app's default mode, and a script made executable in the terminal would stop
 * being one when the file editor saved it; jupyter_server's in-place write keeps the mode, as a
 * user expects of a save. A new file keeps the default.
 */
private fun File.takePermissionsOf(target: File) {
  if (!target.exists()) return
  Files.setPosixFilePermissions(toPath(), Files.getPosixFilePermissions(target.toPath()))
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
