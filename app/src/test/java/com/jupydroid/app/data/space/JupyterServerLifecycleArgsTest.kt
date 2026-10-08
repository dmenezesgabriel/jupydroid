package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.DEFAULT_IDLE_SHUTDOWN_MINUTES
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server is allowed to stop itself, so an app that sits idle stops leaking
 * memory instead of holding a server and its kernels for ever.
 *
 * The mechanism is the reference's own: the server shuts down after
 * `shutdown_no_activity_timeout` seconds with **no kernels** and no activity, and idle kernels
 * are culled first by `MappingKernelManager`. The arguments are asserted rather than the
 * behaviour, exactly as `JupyterServerRootDirTest` does — a real server needs a Space, which is
 * a tablet gate's to prove.
 *
 * Both timers take the Space's one idle-shutdown setting, because they only work as a pair:
 * `shutdown_no_activity` returns early while any kernel exists
 * (`usr/lib/python3.12/site-packages/jupyter_server/serverapp.py`), so a server stops itself only
 * once its idle kernels have been culled.
 */
class JupyterServerLifecycleArgsTest {
  @Test
  fun theServerStopsItselfAfterAnIdleWithNoKernels() {
    val args = argsFor(DEFAULT_IDLE_SHUTDOWN_MINUTES)

    assertTrue(
      "expected shutdown_no_activity_timeout, got $args",
      args.contains("--ServerApp.shutdown_no_activity_timeout=300"),
    )
  }

  @Test
  fun idleKernelsAreCulledBeforeTheServerCanStop() {
    val args = argsFor(DEFAULT_IDLE_SHUTDOWN_MINUTES)

    assertTrue(
      "expected cull_idle_timeout, got $args",
      args.contains("--MappingKernelManager.cull_idle_timeout=300"),
    )
  }

  @Test
  fun aSpacesIdleMinutesReachBothTimersAsSeconds() {
    val args = argsFor(idleShutdownMinutes = 2)

    assertTrue(args.toString(), args.contains("--ServerApp.shutdown_no_activity_timeout=120"))
    assertTrue(args.toString(), args.contains("--MappingKernelManager.cull_idle_timeout=120"))
  }

  /** `0` is the server's own "never" for both timers, so the setting passes straight through. */
  @Test
  fun zeroIdleMinutesDisablesBothTimers() {
    val args = argsFor(idleShutdownMinutes = 0)

    assertTrue(args.toString(), args.contains("--ServerApp.shutdown_no_activity_timeout=0"))
    assertTrue(args.toString(), args.contains("--MappingKernelManager.cull_idle_timeout=0"))
  }

  /**
   * The default cull poll is every 5 minutes, which would make the tablet gate take
   * an unbounded 0-5 min after the idle timeout. A 60 s poll is trivial work and makes
   * "idle past the timeout" deterministic enough to assert against. It is not a setting.
   */
  @Test
  fun cullingRunsOnAShortEnoughIntervalToBeMeasured() {
    val args = argsFor(idleShutdownMinutes = 2)

    assertTrue(
      "expected cull_interval, got $args",
      args.contains("--MappingKernelManager.cull_interval=60"),
    )
  }

  /**
   * A session asked for no kernel gets `default_kernel_name`, and `GET api/kernelspecs` answers it as
   * `default` (`usr/lib/python3.12/site-packages/jupyter_server/services/kernelspecs/handlers.py`),
   * which is what a new notebook records.
   */
  @Test
  fun aSpacesDefaultKernelIsTheServersDefaultKernelName() {
    val args =
      jupyterServerArgs(rootDir = "/home/jupydroid", token = "t", idleShutdownMinutes = 2, defaultKernel = "stats")

    assertTrue(args.toString(), args.contains("--MappingKernelManager.default_kernel_name=stats"))
  }

  private fun argsFor(idleShutdownMinutes: Int) =
    jupyterServerArgs(rootDir = "/home/jupydroid", token = "t", idleShutdownMinutes = idleShutdownMinutes)
}
