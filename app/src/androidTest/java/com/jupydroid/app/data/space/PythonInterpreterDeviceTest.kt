package com.jupydroid.app.data.space

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * **The Space's shell can run the Space's Python**.
 *
 * Under proot the image runs as built (ADR-0073): `python3` is the image's own
 * `/usr/bin/python3`, resolved by name through the Space's `PATH`, and every name the image
 * ships reaches it.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.PythonInterpreterDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class PythonInterpreterDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val baseDir by lazy { baseSpaceDir(context) }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no interpreter to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  /** Unqualified, because `PATH` is what a user types against inside the Space. */
  @Test
  fun theShellRunsTheSpacesOwnPython() {
    val run = runInSpace(SPACE_SHELL, "-c", "python3 --version")

    assertRan(run)
    assertTrue("expected a CPython version, got: ${run.output.trim()}", run.output.startsWith("Python 3."))
  }

  /** The other two names the image ships: relative links onto the one real ELF. */
  @Test
  fun theOtherShippedNamesReachItToo() {
    val run = runInSpace(SPACE_SHELL, "-c", "python --version && python3.12 --version")

    assertRan(run)
    assertEquals("both names should have answered", 2, run.output.lines().count { it.startsWith("Python 3.") })
  }

  /**
   * An interpreter that runs but cannot find its own stdlib would pass the version check and fail
   * every real use, so this is the assertion that says the *interpreter* works rather than the
   * exec - and `/usr` is what the image reports under its own root.
   */
  @Test
  fun pythonReachesItsOwnStandardLibrary() {
    val run = runInSpace(SPACE_SHELL, "-c", "python3 -c 'import json, sys; print(sys.prefix)'")

    assertRan(run)
    assertEquals("/usr", run.output.trim())
  }

  /** A refusal the SIGSYS handler swallowed would leave the interpreter working and the log dirty. */
  @Test
  fun nothingWasRefusedWhileTheInterpreterRan() {
    runInSpace(SPACE_SHELL, "-c", "python3 -c 'import json'")

    assertEquals(
      "seccomp refused a syscall other than mbind while the interpreter ran",
      emptyList<String>(),
      seccompRefusalsIn(baseDir),
    )
  }

  /**
   * **The fact proot is built on, from inside the Space.**
   *
   * On the host a `#!` script under `filesDir` is refused `execve` whatever its mode
   * (ADR-0002). Under proot the Space is its own `/`, and the same script - this app's own file,
   * with its executable bit set - runs through its shebang line, because proot reads the `#!`
   * and execs the interpreter inside the root. This is why `apk add` and every console script
   * work inside a Space.
   */
  @Test
  fun aScriptInsideTheSpaceRunsThroughItsShebangLine() {
    val script = File(baseDir, "tmp/shebang-probe.sh").apply {
      parentFile?.mkdirs()
      writeText("#!/bin/sh\necho ran\n")
      check(setExecutable(true, false)) { "could not set the executable bit on '$this'" }
    }

    val run = runInSpace(SPACE_SHELL, "-c", "/tmp/shebang-probe.sh; echo rc=$?")

    script.delete()
    assertTrue(
      "a #! script inside the Space did not run under proot: ${run.output.trim()}",
      "ran" in run.output && "rc=0" in run.output,
    )
  }

  private fun assertRan(run: SpaceProcessRun) {
    // Java reports a signalled child as 128 + signal, so a seccomp kill is 159 - the same
    // encoding `JupyterServerLauncher.exitCode` documents. 126 is the SELinux exec refusal.
    assertEquals(
      "${run.command} exited ${run.exitCode}" +
        (if (run.exitCode == EXEC_REFUSED) " (EACCES: is usr/bin/python3.12 still a plain file?)" else "") +
        "; output: ${run.output.take(OUTPUT_EXCERPT)}",
      0,
      run.exitCode,
    )
  }

  private companion object {
    const val EXEC_REFUSED = 126
    const val OUTPUT_EXCERPT = 200
  }
}
