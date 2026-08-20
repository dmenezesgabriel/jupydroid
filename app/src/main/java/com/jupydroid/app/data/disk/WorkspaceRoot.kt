package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.incrementedFileName
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Turns an API path into a file inside [rootDir], and refuses anything that leaves it.
 *
 * Its own object because it is its own subject: jupyter_server keeps the identical split, with
 * `_get_os_path` and the rest of the path handling in `fileio.py` and the operations that use them
 * in `filemanager.py`. Everything here is about *addressing*; nothing here reads or writes.
 *
 * ```
 * val root = WorkspaceRoot(notebookWorkspace(context))
 * root.fileAt("reports/summary.ipynb")
 * ```
 */
internal class WorkspaceRoot(private val rootDir: File) {
  /**
   * The file [path] names, refusing anything that leaves the workspace.
   *
   * **Checked canonically, returned as named.** The check compares canonical paths, so `..`, a
   * doubled separator and an absolute path are all caught by one rule rather than by a list of
   * spellings to remember; above the root are the rootfs and the app's private files, and the
   * answer must not depend on the UI never asking.
   *
   * What comes back is the file the path *names*, not the file it resolves to, because the
   * canonical path of a symlink is its **target** - so a move or a delete built on it would touch
   * the tree behind the link instead of the link. This workspace lives inside the rootfs, and both
   * references address a link as a link: jupyter_server joins and `normpath`s without resolving
   * and stats with `os.lstat` (`fileio.py:290-329`), and Winlator checks `isSymlink` before it
   * walks anything (`FileUtils.delete:114-119`). Reading and writing follow the link either way,
   * which is why this was invisible until something had to move a file. Same defect as
   * `TarGzExtractor` writing through the stale loader symlink.
   */
  fun fileAt(path: String): File {
    val root = rootDir.canonicalFile
    val canonical = File(root, path).canonicalFile
    require(canonical == root || canonical.path.startsWith("${root.path}${File.separator}")) {
      "path '$path' is outside the workspace"
    }
    return File(rootDir, path)
  }

  /**
   * Present *as named* - a dangling symlink is something, where `File.exists` says it is not.
   *
   * `os.lstat` is what jupyter_server asks with (`fileio.py:316`), and it matters twice: a broken
   * link can still be renamed, and a broken link sitting on a name still means the name is taken.
   */
  fun isPresent(file: File): Boolean = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)

  /**
   * A link, whatever it points at - which `File.isDirectory` cannot answer, since it follows.
   *
   * Winlator asks the same question before it walks a directory, and for the same reason: in a
   * tree built out of symlinks, recursing through one deletes something nobody named
   * (`FileUtils.isSymlink:110-112`).
   */
  fun isLink(file: File): Boolean = Files.isSymbolicLink(file.toPath())

  /** A name joined onto the directory holding it, with the root spelled as the empty string. */
  fun pathIn(directory: String, name: String): String =
    if (directory.isEmpty()) name else "$directory/$name"

  /**
   * The first name in the server's series that [directory] does not already hold.
   *
   * Here rather than on the repository because it is addressing rather than content - the same
   * line this class already draws - and because `incrementedFileName` is the *domain's* rule for
   * the series; all this adds is the question "is that name taken here".
   */
  fun freeNameIn(directory: String, wanted: String, insert: String = ""): String {
    val parent = fileAt(directory)
    require(parent.isDirectory) { "no directory at '$directory' in the workspace" }
    return incrementedFileName(wanted, insert) { File(parent, it).exists() }
  }
}
