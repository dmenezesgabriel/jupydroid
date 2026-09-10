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

/**
 * **P7e-2c: `pip` runs by name in the Space's shell**, which before this step it could not -
 * `evidence/p7e-1-space-python/04-what-the-console-script-still-does.png` is `pip --version`
 * answering *Permission denied*, rc 126, *after* P7e-1: the exec of the script file, refused by
 * SELinux whatever its mode.
 *
 * Runs the commands **in the app process** through [runInSpace], the only place the seccomp filter
 * applies - a `run-as` shell would answer a question nobody asked (P4b).
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.ConsoleScriptDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ConsoleScriptDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val baseDir by lazy { baseSpaceDir(context) }
  private val shell by lazy { spaceShellIn(baseDir).path }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there are no console scripts to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  @Test
  fun pipRunsByNameAndReportsItsVersion() {
    val run = runInSpace(shell, "-c", "pip --version")

    assertEquals("pip --version exited ${run.exitCode}: ${run.output.trim()}", 0, run.exitCode)
    assertTrue("expected a pip version, got: ${run.output.trim()}", run.output.trim().startsWith("pip "))
  }

  /** A second script, from a different package, so the walk is not proven by `pip` alone. */
  @Test
  fun jupyterRunsByNameToo() {
    val run = runInSpace(shell, "-c", "jupyter --version")

    assertEquals("jupyter --version exited ${run.exitCode}: ${run.output.trim()}", 0, run.exitCode)
    assertTrue("expected jupyter's version block, got: ${run.output.trim()}", "jupyter_core" in run.output)
  }

  /**
   * The **non-interactive** case: `sh -c` is already what [runInSpace] does, but this spells out
   * that it is a script and a subprocess that benefit, not just a person at a prompt - the half a
   * shell-function or alias approach could never reach.
   */
  @Test
  fun aConsoleScriptRunsFromANonInteractiveShell() {
    val run = runInSpace(shell, "-c", "sh -c 'pip --version'")

    assertEquals("nested sh -c 'pip --version' exited ${run.exitCode}: ${run.output.trim()}", 0, run.exitCode)
    assertTrue(run.output.trim().startsWith("pip "))
  }

  /** No syscall was refused while a script ran - a refusal the SIGSYS handler swallowed would hide. */
  @Test
  fun nothingWasRefusedWhileAConsoleScriptRan() {
    runInSpace(shell, "-c", "pip --version")

    assertEquals(
      "seccomp refused a syscall other than mbind while pip ran",
      emptyList<String>(),
      seccompRefusalsIn(baseDir),
    )
  }
}
