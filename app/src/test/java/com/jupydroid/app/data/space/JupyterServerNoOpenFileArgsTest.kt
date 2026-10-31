package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.DEFAULT_IDLE_SHUTDOWN_MINUTES
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server writes no `jpserver-<pid>-open.html`.
 *
 * That redirect page carries the token in its URL and is removed only by a clean stop, so a killed
 * server left one per start in `run/`. Nothing here opens it: the app reads `jpserver-<pid>.json`
 * (`usr/lib/python3.12/site-packages/jupyter_server/serverapp.py`, `no_browser_open_file`).
 */
class JupyterServerNoOpenFileArgsTest {
  @Test
  fun theServerWritesNoBrowserOpenFile() {
    val args =
      jupyterServerArgs(rootDir = "/home/jupydroid", token = "t", idleShutdownMinutes = DEFAULT_IDLE_SHUTDOWN_MINUTES)

    assertTrue("expected no_browser_open_file on, got $args", args.contains("--ServerApp.no_browser_open_file=True"))
  }
}
