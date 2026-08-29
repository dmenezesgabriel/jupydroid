package com.jupydroid.app.data.space

import java.io.File

/**
 * The one path the server and terminado both mean by "the Space's shell".
 *
 * It is one of the **304** busybox applet links [BusyBoxApplets] repoints at `bin/busybox`, which
 * is in turn the only ELF in the Space that can be `execve`'d - it alone points into
 * `nativeLibraryDir`, the one directory carrying an SELinux context that permits exec at
 * targetSdk 36 (`docs/DECISIONS.md`, "Exec model"). So this path is executable *because* the
 * relinker ran, and naming it here rather than at the call site keeps the two from drifting: a
 * shell pinned outside `bin/` would be a link nothing had repaired, and `execve` answers that with
 * ENOENT rather than with anything a log explains.
 *
 * Written down once for the reason [notebookWorkspaceIn] is - two components agreeing by
 * coincidence is not agreement.
 *
 * ```
 * jupyterServerArgs(rootDir, token, shell = spaceShellIn(baseSpaceDir(context)))
 * ```
 */
fun spaceShellIn(baseDir: File): File = File(baseDir, SHELL_RELATIVE)

private const val SHELL_RELATIVE = "bin/sh"
