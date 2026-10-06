package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.DEFAULT_IDLE_SHUTDOWN_MINUTES
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
 * that the flag is there and points where [SpaceRuntime] puts `HOME`, both named inside the Space's
 * root because that is the only filesystem the server sees under proot (ADR-0073).
 */
class JupyterServerRootDirTest {
  private val baseDir = File("/data/data/com.jupydroid.app/files/spaces/base")

  @Test
  fun theServerIsRootedAtTheWorkspace() {
    val args =
      jupyterServerArgs(rootDir = "/home/jupydroid", token = "t", idleShutdownMinutes = DEFAULT_IDLE_SHUTDOWN_MINUTES)

    assertTrue("expected a root_dir argument, got $args", args.contains("--ServerApp.root_dir=/home/jupydroid"))
  }

  /** The one place the path is written down, so the server and `HOME` cannot drift apart. */
  @Test
  fun theWorkspaceIsWhereTheRuntimePutsHome() {
    val env = SpaceRuntime(baseDir, File("/data/app/abc==/lib/arm64")).environment()
    val dirs = SpaceDirs(baseDir)

    assertEquals(env["HOME"], dirs.inRoot(dirs.workspace))
  }

  @Test
  fun theOtherArgumentsAreStillThere() {
    val args =
      jupyterServerArgs(
        rootDir = "/home/jupydroid",
        token = "secret",
        idleShutdownMinutes = DEFAULT_IDLE_SHUTDOWN_MINUTES,
      )

    assertTrue(args.contains("--IdentityProvider.token=secret"))
    assertTrue(args.contains("--KernelManager.transport=ipc"))
  }

  /**
   * proot's `-0` makes every process see uid 0, which `apk add` needs, and jupyter_server refuses
   * to start as root without this flag (jupyter_server 2.20.0 `serverapp.py` `ServerApp.start_app`).
   */
  @Test
  fun theServerIsAllowedToRunAsTheRootProotReports() {
    assertTrue(
      jupyterServerArgs(
        rootDir = "/home/jupydroid",
        token = "t",
        idleShutdownMinutes = DEFAULT_IDLE_SHUTDOWN_MINUTES,
      ).contains("--allow-root"),
    )
  }
}
