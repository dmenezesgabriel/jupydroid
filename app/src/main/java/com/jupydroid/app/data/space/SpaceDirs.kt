package com.jupydroid.app.data.space

import java.io.File

/**
 * The writable directories a Space's processes need, provisioned the first time the Space runs.
 *
 * First launch rather than create, because creating is not the only way a directory becomes a
 * Space: `DiskSpaceRepository`'s scan counts any directory holding `config.json` (ADR-0010), so a
 * Space can exist that nothing ever provisioned. `base` already carries these from its rootfs
 * tarball, which makes [ensure] a no-op there.
 *
 * ```
 * SpaceDirs(spaceDirIn(spacesDir(context), space.id)).ensure()
 * ```
 */
class SpaceDirs(val spaceDir: File) {
  /** The server's root and the kernel's `HOME`; see [notebookWorkspaceIn]. */
  val workspace: File get() = notebookWorkspaceIn(spaceDir)

  /** `JUPYTER_RUNTIME_DIR`, where the server's `jpserver-*.json` and its log land. */
  val runtime: File get() = File(spaceDir, "run")

  /** `TMPDIR`, and where the SIGSYS handler's log lands. */
  val tmp: File get() = File(spaceDir, "tmp")

  /** `PYTHONUSERBASE`: what `pip` installs into this Space, and its console scripts under `bin`. */
  val pkgs: File get() = File(spaceDir, "pkgs")

  /**
   * [dir]'s path as a process in this Space sees it: the Space is its own `/` under proot
   * (ADR-0073), so that is its path under [spaceDir]. `inRoot(workspace)` is `/home/jupydroid`.
   */
  fun inRoot(dir: File): String {
    require(dir.startsWith(spaceDir)) { "'$dir' is outside the Space '$spaceDir'; expected a path under it" }
    return "/" + dir.relativeTo(spaceDir).invariantSeparatorsPath
  }

  /** Creates whichever of the Space's writable directories are missing; never touches their contents. */
  fun ensure() {
    listOf(workspace, runtime, tmp, pkgs).forEach { dir ->
      check(dir.isDirectory || dir.mkdirs()) { "could not create '$dir': expected a directory, found ${shapeOf(dir)}" }
    }
  }

  /**
   * Empties [runtime] and keeps the directory, so a server starts beside only its own files.
   *
   * A killed server removes nothing: its `jpserver-<pid>-open.html`, `jpserver-mcp-<pid>.json` and
   * kernels' connection files stayed, one set per start (ADR-0128). Nothing in it outlives a start -
   * the logs are truncated by the redirect that opens them - so what is cleared is only what a
   * dead process left. Where the directory was never provisioned there is nothing to clear.
   *
   * ```
   * dirs.clearRuntime() // before the server's process is built
   * ```
   */
  fun clearRuntime() {
    val leftovers = runtime.listFiles() ?: return
    leftovers.forEach { leftover ->
      check(leftover.deleteRecursively()) { "could not delete '$leftover' from '$runtime': expected it removed" }
    }
  }

  private fun shapeOf(path: File): String = if (path.exists()) "a file" else "nothing (mkdirs failed)"
}
