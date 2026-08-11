package com.jupydroid.app.domain.model

/**
 * The rule for what a notebook may be called, once its name is its filename.
 *
 * Taken from JupyterLab rather than invented
 * (`reference/research_repos/jupyterlab/packages/docmanager/src/dialogs.ts`, `isValidFileName`):
 * a name must have nonzero length and must not contain `/`, `\` or `:`.
 *
 * ```
 * if (!isValidNotebookName(typed)) { /* tell the user which characters are refused */ }
 * ```
 *
 * **Rejects, never rewrites.** Turning `a/b` quietly into `a-b` hands the user a notebook they did
 * not name and cannot find by the name they typed. The reference shows an error naming the three
 * characters; so should we.
 *
 * Pure, so it belongs in the domain: no file, no repository, nothing to mock.
 */
fun isValidNotebookName(name: String): Boolean {
  val trimmed = name.trim()
  return trimmed.isNotEmpty() && FORBIDDEN.none { it in trimmed } && !trimmed.startsWith(".")
}

/** The file a valid name maps to. Trimmed, because `"a "` is a name nobody can type twice. */
fun notebookFileName(name: String): String = "${name.trim()}$NOTEBOOK_SUFFIX"

/** And back again: the name a file carries, which is the notebook's title and its id's last part. */
fun notebookNameOf(fileName: String): String = fileName.removeSuffix(NOTEBOOK_SUFFIX)

/**
 * `/` and `\` would make the name a path, `:` is refused by the reference alongside them, and a
 * leading `.` hides the file - from the explorer Phase X is for, from `ls`, and from
 * jupyter_server, whose `ContentsManager.allow_hidden` is false by default.
 */
private val FORBIDDEN = listOf('/', '\\', ':')

const val NOTEBOOK_SUFFIX = ".ipynb"

/**
 * The first name in the series that [isTaken] says nothing holds.
 *
 * `FileContentsManager.increment_filename`, transcribed from the jupyter_server in our own rootfs
 * (`services/contents/manager.py`):
 *
 * ```python
 * for i in itertools.count():
 *     insert_i = f"{insert}{i}" if i else ""
 *     name = f"{basename}{insert_i}{suffix}"
 *     if not self.exists(f"{path}/{name}"):
 *         break
 * ```
 *
 * So the bare name comes first and the counter starts at 1 with **no separator** - `Untitled1`,
 * not `Untitled-1`. Copied rather than invented because the server that serves this same workspace
 * names files this way, and two conventions in one directory is two answers to one question.
 *
 * ```
 * incrementedName("Untitled") { File(dir, notebookFileName(it)).exists() }
 * ```
 */
fun incrementedName(base: String, insert: String = "", isTaken: (String) -> Boolean): String {
  var counter = 0
  while (true) {
    val candidate = if (counter == 0) base else "$base$insert$counter"
    if (!isTaken(candidate)) return candidate
    counter++
  }
}
