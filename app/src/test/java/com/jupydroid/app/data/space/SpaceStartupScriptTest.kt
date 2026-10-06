package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.minutes

/**
 * How a Space's startup script is launched, asserted as a value - the bound and the kill are a
 * tablet gate's to prove, which `SpaceStartupScriptDeviceTest` does.
 */
class SpaceStartupScriptTest {
  @get:Rule val tempFolder = TemporaryFolder()

  /**
   * busybox's `timeout` bounds the inner shell inside the Space, so the kill reaches a tracee, not
   * proot; the outer shell turns that kill into an exit status proot passes on. The script is an
   * argument, never spliced into the command string.
   */
  @Test
  fun theScriptRunsUnderBusyboxTimeoutInsideAShellThatReportsItsStatus() {
    assertEquals(
      listOf(
        "/bin/sh",
        "-c",
        "/usr/bin/timeout -s KILL 300 /bin/sh -c \"\$1\"; exit \$?",
        "startup-script",
        "echo 'a' \"b\"",
      ),
      startupScriptArgs("echo 'a' \"b\"", timeout = 5.minutes),
    )
  }

  /** proot takes every process the script left behind with it once `timeout` exits. */
  @Test
  fun theScriptsProotKillsWhatItLeavesBehindAndItsOutputGoesToTheRunDir() {
    val spaceDir = tempFolder.newFolder("space-1")
    val runtime = SpaceRuntime(spaceDir, tempFolder.newFolder("native"))
    val runDir = File(spaceDir, "run")

    val process = startupScriptProcess(runtime, runDir, "echo started", timeout = 5.minutes)

    assertTrue(process.command().toString(), "--kill-on-exit" in process.command())
    assertEquals(File(runDir, "startup.log"), process.redirectOutput().file())
    assertTrue(process.redirectErrorStream())
  }

  /** 137 is the only status that means the bound, so it is the only one said to. */
  @Test
  fun aScriptKilledAtItsTimeoutIsRefusedNamingTheTimeoutAndItsOutput() {
    val runDir = tempFolder.newFolder("run")
    startupScriptLogFile(runDir).writeText("Collecting rich\n")

    val error = assertThrows(IllegalStateException::class.java) {
      requireStartupScriptSucceeded(137, runDir, timeout = 5.minutes)
    }

    assertTrue(error.message!!, "status 137 - killed at its 5m timeout" in error.message!!)
    assertTrue(error.message!!, error.message!!.endsWith("Collecting rich\n"))
  }

  @Test
  fun aZeroStatusIsNotRefused() {
    requireStartupScriptSucceeded(0, tempFolder.newFolder("run"), timeout = 5.minutes)
  }

  @Test
  fun aLogsTailIsItsLastExcerptAndNothingWithoutALog() {
    val log = File(tempFolder.root, "startup.log").apply { writeText("x".repeat(LOG_EXCERPT) + "end") }

    assertEquals("x".repeat(LOG_EXCERPT - 3) + "end", logTail(log))
    assertEquals("", logTail(File(tempFolder.root, "absent.log")))
  }
}
