package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The server and the app have to mean the same directory by "the notebooks".
 *
 * `--ServerApp.root_dir` was never passed, so jupyter_server rooted its Contents API at the
 * process cwd - wherever `ProcessBuilder` happened to leave it. Nothing depended on that while
 * notebooks lived in `filesDir/notebooks` and nothing but the app ever read them; it becomes a
 * correctness question the moment they move into the kernel's `HOME`, because then two components
 * both claim to know where a notebook is.
 *
 * The argument list is asserted rather than the server's behaviour: starting a real jupyter_server
 * needs a Space, which is a tablet gate (`step10-run-cell-real-kernel`), and what a JVM test can own is
 * that the flag is there and points where [SpaceRuntime] puts `HOME`.
 */
class JupyterServerRootDirTest {
  private val baseDir = File("/data/data/com.jupydroid.app/files/spaces/base")

  @Test
  fun theServerIsRootedAtTheWorkspace() {
    val args = jupyterServerArgs(rootDir = notebookWorkspaceIn(baseDir), token = "t", shell = spaceShellIn(baseDir))

    assertTrue(
      "expected a root_dir argument, got $args",
      args.contains("--ServerApp.root_dir=/data/data/com.jupydroid.app/files/spaces/base/home/jupydroid"),
    )
  }

  /** The one place the path is written down, so the server and `HOME` cannot drift apart. */
  @Test
  fun theWorkspaceIsWhereTheRuntimePutsHome() {
    val env = SpaceRuntime(baseDir, File("/lib/libpython3.so")).environment()

    assertEquals(env["HOME"], notebookWorkspaceIn(baseDir).path)
  }

  @Test
  fun theOtherArgumentsAreStillThere() {
    val args =
      jupyterServerArgs(rootDir = notebookWorkspaceIn(baseDir), token = "secret", shell = spaceShellIn(baseDir))

    assertTrue(args.contains("--IdentityProvider.token=secret"))
    assertTrue(args.contains("--KernelManager.transport=ipc"))
  }
}
