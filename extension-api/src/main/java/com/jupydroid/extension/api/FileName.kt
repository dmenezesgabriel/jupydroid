package com.jupydroid.extension.api

/**
 * The rule for what a file may be called - a notebook, a `.py`, a folder, or a chat.
 *
 * Taken from JupyterLab rather than invented
 * (`jupyterlab@7f0e6906 packages/docmanager/src/dialogs.ts`, `isValidFileName`):
 * a name must have nonzero length and must not contain `/`, `\` or `:`.
 *
 * ```
 * if (!isValidFileName(typed)) { /* tell the user which characters are refused */ }
 * ```
 *
 * **Rejects, never rewrites.** Turning `a/b` quietly into `a-b` hands the user a notebook they did
 * not name and cannot find by the name they typed. The reference shows an error naming the three
 * characters; so should we.
 *
 * In the contract rather than in `:app`'s domain because [RenameFileDialog] defaults to it, and an
 * extension that names a file in the workspace answers to the same server (ADR-0104). Pure, so it
 * needs no file, no repository, nothing to mock.
 */
public fun isValidFileName(name: String): Boolean {
  val trimmed = name.trim()
  return trimmed.isNotEmpty() && FORBIDDEN.none { it in trimmed } && !trimmed.startsWith(".")
}

/**
 * `/` and `\` would make the name a path, `:` is refused by the reference alongside them, and a
 * leading `.` hides the file - from the explorer, from `ls`, and from jupyter_server, whose
 * `ContentsManager.allow_hidden` is false by default.
 */
private val FORBIDDEN = listOf('/', '\\', ':')
