package com.jupydroid.app.data.disk

import java.io.File

/**
 * Moves notebooks out of the app's private folder and into the workspace the kernel can see.
 *
 * Until Phase X a notebook lived at `filesDir/notebooks/<id>.ipynb` while the kernel's `HOME` was
 * `files/spaces/base/home/jupydroid` - two trees that never met. So a notebook could not
 * `open('data.csv')` on anything the user could see, and the user could not see anything a
 * notebook wrote. One filesystem is JupyterLab's model and it is what makes X3 possible at all.
 *
 * ```
 * NotebookWorkspaceMigration(legacyDir, workspaceDir, stampFile).runOnce()
 * ```
 *
 * **Stamped, because the source directory keeps being writable.** Nothing stops a later build - or
 * a stale process - from putting a file back in the old place, and a migration that re-ran would
 * suffix every notebook again. The stamp is written even when there was nothing to move, so the
 * question "has this run?" never depends on what happens to be on disk.
 *
 * Copy-then-delete rather than `renameTo`: the two directories are the same filesystem today, but
 * that is a fact about the current layout and not one worth betting a user's notebooks on.
 */
class NotebookWorkspaceMigration(
  private val legacyDir: File,
  private val workspaceDir: File,
  /**
   * Where "this has run" is recorded - passed in rather than derived from [workspaceDir].
   *
   * It was `workspaceDir.parentFile` at first, which put our bookkeeping inside the rootfs's
   * `home` directory and left the real path a matter of reading the code. A driver that deleted
   * the path it *assumed* then made the migration a no-op and the flow failed looking for a
   * notebook that had never moved. `BaseSpaceInstaller` already keeps its version stamp under
   * `.jupydroid/`; this belongs beside it, and being a parameter means the flow that drives it
   * names the same file the app does.
   */
  private val stampFile: File,
) {
  /** Moves what is there, or does nothing at all if it has run before. Returns how many moved. */
  fun runOnce(): Int {
    if (stamp().exists()) return 0
    val moved = notebooksToMove().count(::move)
    workspaceDir.mkdirs()
    stamp().apply { parentFile?.mkdirs() }.writeText(STAMP_TEXT)
    return moved
  }

  private fun notebooksToMove(): List<File> =
    (legacyDir.listFiles() ?: emptyArray())
      .filter { it.isFile && it.name.endsWith(NOTEBOOK_SUFFIX) }
      .sortedBy { it.name }

  private fun move(file: File): Boolean {
    workspaceDir.mkdirs()
    val target = freeNameFor(file.name)
    file.copyTo(target)
    return file.delete()
  }

  /**
   * The incoming name, or that name with a counter, never a name already taken.
   *
   * The workspace is a directory the user and the kernel both write to, so a clash is ordinary
   * and the file already there may well be the newer one. Suffixing keeps both.
   */
  private fun freeNameFor(name: String): File {
    val stem = name.removeSuffix(NOTEBOOK_SUFFIX)
    var candidate = File(workspaceDir, name)
    var counter = 1
    while (candidate.exists()) {
      candidate = File(workspaceDir, "$stem-$counter$NOTEBOOK_SUFFIX")
      counter++
    }
    return candidate
  }

  private fun stamp(): File = stampFile

  private companion object {
    const val NOTEBOOK_SUFFIX = ".ipynb"
    const val STAMP_TEXT = "1"
  }
}
