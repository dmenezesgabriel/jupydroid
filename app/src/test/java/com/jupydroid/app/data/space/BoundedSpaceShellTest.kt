package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The bounded shell every in-Space script shares - the startup script's and a harness install's
 * alike. The bound and the kill are a tablet gate's to prove, which `SpaceStartupScriptDeviceTest`
 * and `ProotSpaceShellDeviceTest` do.
 */
class BoundedSpaceShellTest {
  /**
   * busybox's `timeout` bounds the inner shell inside the Space, so the kill reaches a tracee, not
   * proot; the outer shell turns that kill into an exit status proot passes on. The script is an
   * argument, never spliced into the command string, and [arg0] is the only thing naming the run.
   */
  @Test
  fun theScriptRunsUnderBusyboxTimeoutInsideAShellThatReportsItsStatus() {
    assertEquals(
      listOf(
        "/bin/sh",
        "-c",
        "/usr/bin/timeout -s KILL 15 /bin/sh -c \"\$1\"; exit \$?",
        "space-shell",
        "command -v opencode",
      ),
      boundedShellArgs("space-shell", "command -v opencode", timeout = 15.seconds),
    )
  }
}
