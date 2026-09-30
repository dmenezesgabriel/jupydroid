package com.jupydroid.app.data.space

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The server is allowed to stop itself, so an app that sits idle stops leaking
 * memory instead of holding a server and its kernels for ever.
 *
 * The mechanism is the reference's own: the server shuts down after
 * `shutdown_no_activity_timeout` seconds with **no kernels** and no activity, and idle kernels
 * are culled first by `MappingKernelManager`. The arguments are asserted rather than the
 * behaviour, exactly as `JupyterServerRootDirTest` does — a real server needs a Space, which is
 * a tablet gate's to prove.
 */
class JupyterServerLifecycleArgsTest {
  private val baseDir = File("/data/data/com.jupydroid.app/files/spaces/base")

  @Test
  fun theServerStopsItselfAfterAnIdleWithNoKernels() {
    val args = jupyterServerArgs(rootDir = notebookWorkspaceIn(baseDir), token = "t", shell = spaceShellIn(baseDir))

    assertTrue(
      "expected shutdown_no_activity_timeout, got $args",
      args.contains("--ServerApp.shutdown_no_activity_timeout=300"),
    )
  }

  @Test
  fun idleKernelsAreCulledBeforeTheServerCanStop() {
    val args = jupyterServerArgs(rootDir = notebookWorkspaceIn(baseDir), token = "t", shell = spaceShellIn(baseDir))

    assertTrue(
      "expected cull_idle_timeout, got $args",
      args.contains("--MappingKernelManager.cull_idle_timeout=300"),
    )
  }

  /**
   * The default cull poll is every 5 minutes, which would make the tablet gate take
   * an unbounded 0-5 min after the idle timeout. A 60 s poll is trivial work and makes
   * "idle past the timeout" deterministic enough to assert against.
   */
  @Test
  fun cullingRunsOnAShortEnoughIntervalToBeMeasured() {
    val args = jupyterServerArgs(rootDir = notebookWorkspaceIn(baseDir), token = "t", shell = spaceShellIn(baseDir))

    assertTrue(
      "expected cull_interval, got $args",
      args.contains("--MappingKernelManager.cull_interval=60"),
    )
  }
}
